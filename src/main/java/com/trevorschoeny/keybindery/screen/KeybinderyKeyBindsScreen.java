package com.trevorschoeny.keybindery.screen;

import com.trevorschoeny.keybindery.api.Chord;
import com.trevorschoeny.keybindery.chord.IChordKeyMapping;
import com.trevorschoeny.keybindery.mixin.KeyBindsScreenAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.options.controls.KeybinderyKeyBindsList;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
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
 * list subclass in place of vanilla's; shrink it from the top to leave
 * headroom for the toolbar.
 */
public class KeybinderyKeyBindsScreen extends KeyBindsScreen {

    /** Vertical pixels reserved above the keybind list for the toolbar
     *  (panel paints y=4 to y≈72 with the 2-row layout). */
    private static final int TOOLBAR_RESERVATION = 48;

    /** The screen currently open, so the MK panel's widget lenses can find
     *  the active list. Null when no controls screen is open. */
    private static @Nullable KeybinderyKeyBindsScreen current;

    public KeybinderyKeyBindsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options);
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
        // post-super so vanilla's layout has positioned everything; we then
        // replace the list-shaped widget at shrunk dimensions to leave
        // headroom for the toolbar.
        KeyBindsScreenAccessor accessor = (KeyBindsScreenAccessor) this;
        var oldList = accessor.keybindery$getKeyBindsList();
        KeybinderyKeyBindsList newList = new KeybinderyKeyBindsList(this, this.minecraft);
        if (oldList != null) {
            newList.setRectangle(
                    oldList.getWidth(),
                    Math.max(0, oldList.getHeight() - TOOLBAR_RESERVATION),
                    oldList.getX(),
                    oldList.getY() + TOOLBAR_RESERVATION);
        }
        accessor.keybindery$setKeyBindsList(newList);
        if (oldList != null) this.removeWidget(oldList);
        this.addRenderableWidget(newList);
        newList.refreshEntries();
    }

    @Override
    public void removed() {
        super.removed();
        if (current == this) current = null;
    }

    /**
     * Pre-fills the toolbar to surface every keybind that shares ≥1 key with
     * {@code mapping}'s chord. Called by the per-row "See Conflicts" button
     * (deferred — Section 3b.2). Sets the search-chord filter and switches
     * the row filter to {@link RowFilter#CONFLICTS}.
     */
    public void filterToConflictsOf(KeyMapping mapping) {
        Chord chord = IChordKeyMapping.getChord(mapping);
        KeybinderyKeyBindsList list = currentList();
        if (list == null) return;
        Set<com.mojang.blaze3d.platform.InputConstants.Key> keys =
                new LinkedHashSet<>(chord.getKeys());
        list.setSearchChordKeys(keys);
        list.setRowFilter(RowFilter.CONFLICTS);
    }
}
