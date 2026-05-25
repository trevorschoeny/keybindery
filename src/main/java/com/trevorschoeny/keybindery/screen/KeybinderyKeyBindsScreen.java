package com.trevorschoeny.keybindery.screen;

import com.trevorschoeny.keybindery.api.Chord;
import com.trevorschoeny.keybindery.chord.IChordKeyMapping;
import com.trevorschoeny.keybindery.mixin.KeyBindsScreenAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.options.controls.KeybinderyKeyBindsList;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Keybindery's controls-screen overhaul (F4). Drops in for vanilla
 * {@link KeyBindsScreen} via {@link com.trevorschoeny.keybindery.mixin.MinecraftSetScreenMixin}.
 *
 * <p>All toolbar UI is in the MK panel registered at client init by
 * {@link ControlsToolbarPanel#install()} — search box, chord-capture
 * button, sort dropdown, filter dropdown. Widget lenses look up the
 * current screen's {@link KeybinderyKeyBindsList} via {@link #currentList()}.
 *
 * <p>This screen's only responsibilities: install the filter/sort-capable
 * list subclass in place of vanilla's; extend the vanilla header to
 * contain the toolbar; suppress the redundant "Key Binds" title.
 */
public class KeybinderyKeyBindsScreen extends KeyBindsScreen {

    /** Header height needed to fit the MK toolbar inside the vanilla
     *  header bar. Toolbar paints from y≈8 (4px EDGE_INSET + 4 ROW1_Y)
     *  to y≈52 (4 + 28 ROW2_Y + 20 ELEM_H). 56px covers it with a small
     *  buffer before the list begins below the header. */
    private static final int TOOLBAR_HEADER_HEIGHT = 56;

    /** The screen currently open, so the MK panel's widget lenses can find
     *  the active list. Null when no controls screen is open. */
    private static @Nullable KeybinderyKeyBindsScreen current;

    /** Pending conflict-filter target — populated by
     *  {@link #openWithConflictsFilterFor(KeyMapping, Screen)} before
     *  {@code setScreen(...)}, consumed by {@link #init()} once the new
     *  list has been installed. Allows callers to open the controls
     *  screen pre-filtered to a specific mapping's conflict set. */
    private static @Nullable KeyMapping pendingConflictFilterTarget;

    /** Pending search-query — populated by
     *  {@link #openWithModFilterFor(String, Screen)} before
     *  {@code setScreen(...)}, consumed by {@link #init()}. Used for
     *  mod-name search filter (overlay-button path on non-YACL config
     *  screens). */
    private static @Nullable String pendingSearchQuery;

    public KeybinderyKeyBindsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options);
        // Extend the vanilla header bar BEFORE init() runs — vanilla's
        // addContents() sizes the keybind list to fit the content area
        // (= screen − header − footer), so a taller header naturally
        // shrinks the list from the top and makes room for the MK
        // toolbar painted over the header.
        this.layout.setHeaderHeight(TOOLBAR_HEADER_HEIGHT);
    }

    /**
     * Suppress vanilla's "Key Binds" title widget — it'd eat space inside
     * the (now-taller) header that the F4 toolbar uses, and the
     * controls-screen context already tells the user where they are.
     * Vanilla {@code OptionsSubScreen.addTitle()} adds a string widget to
     * the header layout; overriding to a no-op skips that.
     */
    @Override
    protected void addTitle() {
        // intentionally empty
    }

    /** Returns the {@link KeybinderyKeyBindsList} on the currently-open
     *  controls screen, or {@code null} when none is open. Used by the
     *  toolbar panel widgets to read/write filter state. */
    public static @Nullable KeybinderyKeyBindsList currentList() {
        KeybinderyKeyBindsScreen s = current;
        if (s == null) return null;
        var raw = ((KeyBindsScreenAccessor) s).keybindery$getKeyBindsList();
        return raw instanceof KeybinderyKeyBindsList kl ? kl : null;
    }

    @Override
    protected void init() {
        super.init();
        current = this;

        // Swap vanilla's list for our filter/sort-capable subclass. Done
        // post-super so vanilla's layout has positioned the old list at
        // its final (header-aware) rectangle; we mirror those dimensions
        // straight to the new list — the taller header set in the ctor
        // already made room for the toolbar, no extra offset needed.
        KeyBindsScreenAccessor accessor = (KeyBindsScreenAccessor) this;
        var oldList = accessor.keybindery$getKeyBindsList();
        KeybinderyKeyBindsList newList = new KeybinderyKeyBindsList(this, this.minecraft);
        if (oldList != null) {
            newList.setRectangle(
                    oldList.getWidth(),
                    oldList.getHeight(),
                    oldList.getX(),
                    oldList.getY());
        }
        accessor.keybindery$setKeyBindsList(newList);
        if (oldList != null) this.removeWidget(oldList);
        this.addRenderableWidget(newList);
        newList.refreshEntries();

        // Apply pre-requested conflict filter, if one was set via
        // openWithConflictsFilterFor.
        if (pendingConflictFilterTarget != null) {
            filterToConflictsOf(pendingConflictFilterTarget);
            pendingConflictFilterTarget = null;
        }
        // Apply pre-requested search query, if one was set via
        // openWithModFilterFor. Pre-fills the list's filter so the user
        // sees their mod's keybinds immediately.
        if (pendingSearchQuery != null) {
            KeybinderyKeyBindsList l = currentList();
            if (l != null) l.setSearchQuery(pendingSearchQuery);
            pendingSearchQuery = null;
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (current == this) current = null;
    }


    /**
     * Opens a fresh controls screen pre-filtered to show the conflict set
     * of {@code mapping}. Called from per-row Conflicts buttons across all
     * surfaces (modal, YACL tab, vanilla rows). If the caller is already
     * on a {@code KeybinderyKeyBindsScreen}, just applies the filter
     * in-place — no screen swap needed.
     */
    public static void openWithConflictsFilterFor(KeyMapping mapping, Screen current) {
        if (current instanceof KeybinderyKeyBindsScreen kbScreen) {
            kbScreen.filterToConflictsOf(mapping);
            return;
        }
        pendingConflictFilterTarget = mapping;
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new KeybinderyKeyBindsScreen(current, mc.options));
    }

    /**
     * Opens a fresh controls screen pre-filtered (search box) to surface
     * keybinds belonging to the given mod.
     *
     * <p>Search query is the <b>category label</b> of the mod's first
     * keybind — that's the exact string the user sees in the Key Binds
     * menu's category headers, so the contains-match in
     * {@code KeybinderyKeyBindsList.passesFilter} is guaranteed to find
     * those rows. Earlier versions used FabricLoader's mod display name,
     * which often differed from the category label (mods commonly use
     * a custom category like "Inventory Plus" while the Fabric metadata
     * name is something subtly different, or the category lang key was
     * never translated).
     *
     * <p>Fallback when the mod has no registered keybinds: use the mod's
     * Fabric display name; the screen opens with that placeholder query
     * even though no rows will match.
     */
    public static void openWithModFilterFor(String modId, Screen current) {
        List<KeyMapping> mappings =
                com.trevorschoeny.keybindery.screen.ModConfigKeybindsRegistry.keybindsFor(modId);
        String query;
        if (!mappings.isEmpty()) {
            query = mappings.get(0).getCategory().label().getString();
        } else {
            query = FabricLoader.getInstance().getModContainer(modId)
                    .map(c -> c.getMetadata().getName())
                    .orElse(modId);
        }
        pendingSearchQuery = query;
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new KeybinderyKeyBindsScreen(current, mc.options));
    }

    /**
     * Pre-fills the toolbar to surface every keybind that shares ≥1 key with
     * {@code mapping}'s chord by setting the search-chord filter only — the
     * chord search alone naturally narrows the list to the conflict set
     * (any keybind sharing a key with the target chord IS a conflict by our
     * any-key-overlap rule), so layering the {@link RowFilter#CONFLICTS} row
     * filter on top is redundant. Whatever row filter the user had set
     * stays put.
     */
    public void filterToConflictsOf(KeyMapping mapping) {
        Chord chord = IChordKeyMapping.getChord(mapping);
        KeybinderyKeyBindsList list = currentList();
        if (list == null) return;
        Set<com.mojang.blaze3d.platform.InputConstants.Key> keys =
                new LinkedHashSet<>(chord.getKeys());
        list.setSearchChordKeys(keys);
    }
}
