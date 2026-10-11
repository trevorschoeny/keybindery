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
 * Keybindery's controls screen. Replaces vanilla's {@link KeyBindsScreen}
 * through {@link com.trevorschoeny.keybindery.mixin.MinecraftSetScreenMixin}.
 *
 * <p>The whole toolbar (search box, chord-capture button, sort and filter
 * dropdowns) is the MenuKit panel that {@link ControlsToolbarPanel#install()}
 * registers at client init. Its widgets find the current screen's
 * {@link KeybinderyKeyBindsList} through {@link #currentList()}.
 *
 * <p>This screen only does three things: puts in the list subclass that can
 * filter and sort, in place of vanilla's; makes the vanilla header tall
 * enough for the toolbar; and hides the redundant "Key Binds" title.
 */
public class KeybinderyKeyBindsScreen extends KeyBindsScreen {

    /**
     * Header height that fits the MenuKit toolbar inside the vanilla header
     * bar. The toolbar paints from about y=8 (4px EDGE_INSET + 4 ROW1_Y) to
     * about y=52 (4 + 28 ROW2_Y + 20 ELEM_H); 56px covers it with a little
     * room before the list starts below the header.
     */
    private static final int TOOLBAR_HEADER_HEIGHT = 56;

    /**
     * The screen that is open now, so the toolbar's widgets can find the
     * active list. Null when no controls screen is open.
     */
    private static @Nullable KeybinderyKeyBindsScreen current;

    /**
     * Mapping whose conflicts the next screen should show. Set by
     * {@link #openWithConflictsFilterFor(KeyMapping, Screen)} before
     * {@code setScreen(...)} and used by {@link #init()} once the new list is
     * in place, so callers can open the controls screen already filtered to
     * one mapping's conflicts.
     */
    private static @Nullable KeyMapping pendingConflictFilterTarget;

    /**
     * Search query for the next screen. Set by
     * {@link #openWithModFilterFor(String, Screen)} before
     * {@code setScreen(...)} and used by {@link #init()}. Used for the mod
     * filter behind the overlay button on non-YACL config screens.
     */
    private static @Nullable String pendingSearchQuery;

    public KeybinderyKeyBindsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options);
        // Make the vanilla header taller before init() runs. Vanilla's
        // addContents() sizes the keybind list to the content area (the
        // screen minus header and footer), so a taller header shrinks the
        // list from the top and leaves room for the toolbar painted over
        // the header.
        this.layout.setHeaderHeight(TOOLBAR_HEADER_HEIGHT);
    }

    /**
     * Hides vanilla's "Key Binds" title. It would take space in the taller
     * header that the toolbar uses, and the screen already makes clear where
     * the player is. Vanilla's {@code OptionsSubScreen.addTitle()} adds a
     * string widget to the header layout; overriding it with nothing skips
     * that.
     */
    @Override
    protected void addTitle() {
        // Intentionally empty.
    }

    /**
     * The {@link KeybinderyKeyBindsList} on the open controls screen, or
     * {@code null} when none is open. The toolbar widgets use it to read and
     * write the filter state.
     */
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

        // Swap vanilla's list for the subclass that can filter and sort. Done
        // after super so vanilla's layout has already placed the old list at
        // its final rectangle (below the header); the new list copies those
        // bounds directly. The taller header set in the constructor already
        // made room for the toolbar, so no extra offset is needed.
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

        // Apply the conflict filter requested through openWithConflictsFilterFor, if any.
        if (pendingConflictFilterTarget != null) {
            filterToConflictsOf(pendingConflictFilterTarget);
            pendingConflictFilterTarget = null;
        }
        // Apply the search query requested through openWithModFilterFor, if
        // any, so the player sees that mod's keybinds straight away.
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
     * Opens a new controls screen filtered to {@code mapping}'s conflicts.
     * Called from the per-row Conflicts buttons on both the YACL Keybinds tab
     * and the vanilla Key Binds list. If the caller is already on a
     * {@code KeybinderyKeyBindsScreen}, it just applies the filter there,
     * with no new screen.
     */
    public static void openWithConflictsFilterFor(KeyMapping mapping, Screen current) {
        if (current instanceof KeybinderyKeyBindsScreen kbScreen) {
            kbScreen.filterToConflictsOf(mapping);
            return;
        }
        pendingConflictFilterTarget = mapping;
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new KeybinderyKeyBindsScreen(current, mc.options));
    }

    /**
     * Opens a new controls screen with the search box filled in to show the
     * given mod's keybinds.
     *
     * <p>The query is the <b>category label</b> of the mod's first keybind.
     * That is the exact text in the Key Binds menu's category headers, so the
     * contains match in {@code KeybinderyKeyBindsList.passesFilter} always
     * finds those rows. An earlier version used FabricLoader's mod display
     * name, which often didn't match the category label (a mod's category,
     * like "Inventory Plus", can differ slightly from its Fabric metadata
     * name, or its category lang key may never have been translated).
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
        mc.gui.setScreen(new KeybinderyKeyBindsScreen(current, mc.options));
    }

    /**
     * Fills in the toolbar to show every keybind that shares at least one key
     * with {@code mapping}'s chord, by setting only the search-chord filter.
     * The chord search alone narrows the list to the conflicts (by the
     * any-shared-key rule, every keybind sharing a key with the chord is a
     * conflict), so adding the {@link RowFilter#CONFLICTS} row filter would be
     * redundant. Whatever row filter the player had set stays as it was.
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
