package com.trevorschoeny.keybindery.screen;

import com.trevorschoeny.keybindery.api.Chord;
import com.trevorschoeny.menukit.core.ControlStyle;
import com.trevorschoeny.menukit.core.Dropdown;
import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.PanelPosition;
import com.trevorschoeny.menukit.core.PanelStyle;
import com.trevorschoeny.menukit.core.TextLabel;
import com.trevorschoeny.menukit.core.VanillaScreenRegion;
import com.trevorschoeny.menukit.inject.VanillaScreenPanelAdapter;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.options.controls.KeybinderyKeyBindsList;

import java.util.List;

/**
 * F4 toolbar MK panel. Two rows:
 *
 * <ul>
 *   <li><b>Row 1:</b> name-search box + Search Keybind chord-capture button</li>
 *   <li><b>Row 2:</b> "Sort:" label + sort dropdown + "Filter:" label + filter dropdown</li>
 * </ul>
 *
 * <p>Built + registered once at client init; targets
 * {@link KeybinderyKeyBindsScreen} via the vanilla-screen panel adapter.
 * All widget state reads/writes through
 * {@link KeybinderyKeyBindsScreen#currentList()}.
 */
public final class ControlsToolbarPanel {

    private ControlsToolbarPanel() {}

    // ── Layout constants ─────────────────────────────────────────────────

    /** Y of row 1 inside the panel. Sits close to the panel top — the
     *  vanilla title that this used to clear at y≈16 is now suppressed
     *  by {@code KeybinderyKeyBindsScreen.addTitle}, so we can pull up. */
    private static final int ROW1_Y = 4;
    /** Y of row 2 inside the panel. Row 1 + element height + gap. */
    private static final int ROW2_Y = ROW1_Y + 20 + 4;
    /** Element height — search box, chord button, dropdowns share. */
    private static final int ELEM_H = 20;

    // Row 1 element widths + x positions
    private static final int SEARCH_W = 140;
    private static final int CHORD_BTN_W = 110;
    private static final int SEARCH_X = 0;
    private static final int CHORD_BTN_X = SEARCH_X + SEARCH_W + 4;

    // Row 2 element widths + x positions
    private static final int SORT_W = 90;
    private static final int FILTER_W = 90;
    private static final int SORT_LABEL_X = 0;
    private static final int SORT_X = 28;          // after "Sort:" label
    private static final int FILTER_LABEL_X = SORT_X + SORT_W + 12;
    private static final int FILTER_X = FILTER_LABEL_X + 34; // after "Filter:" label

    /** Total outer width (mirrored as {@link KeybinderyKeyBindsScreen#PANEL_WIDTH}). */
    static final int PANEL_WIDTH = CHORD_BTN_X + CHORD_BTN_W;

    /** Constructs the panel + adapter and registers it with MK. Call once
     *  at client init. */
    public static void install() {
        // ── Row 1 ──────────────────────────────────────────────────────
        SearchBox searchBox = new SearchBox(
                SEARCH_X, ROW1_Y, SEARCH_W, ELEM_H,
                Component.literal("Search keybinds..."),
                query -> {
                    KeybinderyKeyBindsList l = KeybinderyKeyBindsScreen.currentList();
                    if (l != null) l.setSearchQuery(query);
                },
                // Lens — populate the visible field from the list's current
                // searchQuery on every screen attach. Lets openWithModFilterFor
                // pre-fill the box so the user can backspace it to see all
                // keybinds.
                () -> {
                    KeybinderyKeyBindsList l = KeybinderyKeyBindsScreen.currentList();
                    return l != null ? l.getSearchQuery() : "";
                });

        SearchKeybindButton chordBtn = new SearchKeybindButton(
                CHORD_BTN_X, ROW1_Y, CHORD_BTN_W, ELEM_H,
                ControlsToolbarPanel::getSearchChord,
                ControlsToolbarPanel::setSearchChord,
                Component.literal("Search Keybind..."));
        chordBtn.tooltip(Component.literal(
                "Click to bind a chord; right-click to clear."));

        // ── Row 2 ──────────────────────────────────────────────────────
        // Labels render at ROW2_Y; the row-height of 20 leaves the label
        // text vertically centered against the dropdown triggers.
        int labelTextY = ROW2_Y + (ELEM_H - 9) / 2; // 9 = font.lineHeight approx
        TextLabel sortLabel = new TextLabel(
                SORT_LABEL_X, labelTextY,
                Component.literal("Sort:"),
                TextLabel.COLOR_LIGHT, true);

        Dropdown<SortOrder> sortDropdown = Dropdown.<SortOrder>builder()
                .at(SORT_X, ROW2_Y)
                .triggerSize(SORT_W, ELEM_H)
                .items(List.of(SortOrder.values()))
                .label(SortOrder::display)
                .selection(ControlsToolbarPanel::getSortOrder,
                           ControlsToolbarPanel::setSortOrder)
                .style(ControlStyle.VANILLA)
                .build();

        TextLabel filterLabel = new TextLabel(
                FILTER_LABEL_X, labelTextY,
                Component.literal("Filter:"),
                TextLabel.COLOR_LIGHT, true);

        Dropdown<RowFilter> filterDropdown = Dropdown.<RowFilter>builder()
                .at(FILTER_X, ROW2_Y)
                .triggerSize(FILTER_W, ELEM_H)
                .items(List.of(RowFilter.values()))
                .label(RowFilter::display)
                .selection(ControlsToolbarPanel::getRowFilter,
                           ControlsToolbarPanel::setRowFilter)
                .style(ControlStyle.VANILLA)
                .build();

        // Dropdowns declared last (render-order discipline — popovers paint
        // above earlier elements). filterDropdown after sortDropdown so its
        // popover wins z-order if they ever overlap (they don't in practice).
        Panel toolbar = new Panel(
                "keybindery-controls-toolbar",
                List.<PanelElement>of(searchBox, chordBtn,
                                       sortLabel, filterLabel,
                                       sortDropdown, filterDropdown),
                /*visible=*/ true,
                PanelStyle.NONE,
                PanelPosition.BODY,
                /*toggleKey=*/ -1);

        new VanillaScreenPanelAdapter(toolbar, VanillaScreenRegion.TOP_CENTER, /*padding=*/ 0)
                .on(KeybinderyKeyBindsScreen.class);
    }

    // ── Lens helpers (read/write the active list's filter state) ────────

    private static Chord getSearchChord() {
        KeybinderyKeyBindsList l = KeybinderyKeyBindsScreen.currentList();
        if (l == null) return Chord.UNBOUND;
        var keys = l.getSearchChordKeys();
        return keys.isEmpty() ? Chord.UNBOUND : new Chord(keys);
    }

    private static void setSearchChord(Chord chord) {
        KeybinderyKeyBindsList l = KeybinderyKeyBindsScreen.currentList();
        if (l == null) return;
        if (chord == null || chord.isUnbound()) l.clearSearchChordKeys();
        else l.setSearchChordKeys(chord.getKeys());
    }

    private static SortOrder getSortOrder() {
        KeybinderyKeyBindsList l = KeybinderyKeyBindsScreen.currentList();
        return l == null ? SortOrder.BY_CATEGORY : l.getSortOrder();
    }

    private static void setSortOrder(SortOrder order) {
        KeybinderyKeyBindsList l = KeybinderyKeyBindsScreen.currentList();
        if (l != null) l.setSortOrder(order);
    }

    private static RowFilter getRowFilter() {
        KeybinderyKeyBindsList l = KeybinderyKeyBindsScreen.currentList();
        return l == null ? RowFilter.NONE : l.getRowFilter();
    }

    private static void setRowFilter(RowFilter filter) {
        KeybinderyKeyBindsList l = KeybinderyKeyBindsScreen.currentList();
        if (l != null) l.setRowFilter(filter);
    }
}
