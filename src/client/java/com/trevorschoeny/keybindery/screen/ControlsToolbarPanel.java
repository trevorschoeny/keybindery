package com.trevorschoeny.keybindery.screen;

import com.trevorschoeny.keybindery.api.Chord;
import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.Dropdown;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.InsideRegion;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.panel.VanillaScreenPanelAdapter;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.options.controls.KeybinderyKeyBindsList;

import java.util.List;

/**
 * The controls-screen toolbar, a MenuKit panel with two rows:
 *
 * <ul>
 *   <li><b>Row 1:</b> the name search box and the Search Keybind chord button</li>
 *   <li><b>Row 2:</b> the "Sort:" label and dropdown, the "Filter:" label and
 *       dropdown, and Collapse All or Expand All</li>
 * </ul>
 *
 * <p>Built and registered once at client init, on
 * {@link KeybinderyKeyBindsScreen} through the vanilla-screen panel adapter.
 * Every widget reads and writes its state through
 * {@link KeybinderyKeyBindsScreen#currentList()}.
 */
public final class ControlsToolbarPanel {

    private ControlsToolbarPanel() {}

    // Layout.

    /**
     * Y of row 1 inside the panel. Close to the panel top: the vanilla title
     * this used to clear at about y=16 is hidden by
     * {@code KeybinderyKeyBindsScreen.addTitle}, so the row can sit higher.
     */
    private static final int ROW1_Y = 4;
    /** Y of row 2 inside the panel: row 1, plus element height, plus a gap. */
    private static final int ROW2_Y = ROW1_Y + 20 + 4;
    /** Shared height of the search box, chord button and dropdowns. */
    private static final int ELEM_H = 20;

    // Row 1 widths and x positions.
    private static final int SEARCH_W = 140;
    private static final int CHORD_BTN_W = 110;
    private static final int SEARCH_X = 0;
    private static final int CHORD_BTN_X = SEARCH_X + SEARCH_W + 4;

    // Row 2 widths and x positions.
    private static final int SORT_W = 90;
    private static final int FILTER_W = 90;
    private static final int SORT_LABEL_X = 0;
    private static final int SORT_X = 28;          // after "Sort:" label
    private static final int FILTER_LABEL_X = SORT_X + SORT_W + 12;
    private static final int FILTER_X = FILTER_LABEL_X + 34; // after "Filter:" label
    /**
     * Collapse All and Expand All: two buttons sharing one spot after the
     * filter dropdown, with visibleWhen choosing which shows.
     */
    private static final int COLLAPSE_W = 80;
    private static final int COLLAPSE_X = FILTER_X + FILTER_W + 8;

    /** Total outer width. Row 2 is the wider row, with the collapse button after the filter dropdown. */
    static final int PANEL_WIDTH = COLLAPSE_X + COLLAPSE_W;

    /** Builds the panel and adapter and registers them with MenuKit. Call once at client init. */
    public static void install() {
        // Row 1.
        SearchBox searchBox = new SearchBox(
                SEARCH_X, ROW1_Y, SEARCH_W, ELEM_H,
                Component.literal("Search by name…"),
                query -> {
                    KeybinderyKeyBindsList l = KeybinderyKeyBindsScreen.currentList();
                    if (l != null) l.setSearchQuery(query);
                },
                // Fill the field from the list's current searchQuery on every
                // screen attach. This lets openWithModFilterFor pre-fill the box,
                // and the player can backspace it to see all keybinds.
                () -> {
                    KeybinderyKeyBindsList l = KeybinderyKeyBindsScreen.currentList();
                    return l != null ? l.getSearchQuery() : "";
                });

        SearchKeybindButton chordBtn = new SearchKeybindButton(
                CHORD_BTN_X, ROW1_Y, CHORD_BTN_W, ELEM_H,
                ControlsToolbarPanel::getSearchChord,
                ControlsToolbarPanel::setSearchChord,
                Component.literal("Search by keys"),
                Component.literal("Click to bind a chord; right-click to clear."));

        // Row 2. The labels are vertically centered against the dropdowns in
        // the 20px row.
        int labelTextY = ROW2_Y + (ELEM_H - 9) / 2; // 9 is about font.lineHeight
        TextLabel sortLabel = TextLabel.builder()
                .at(SORT_LABEL_X, labelTextY)
                .text(Component.literal("Sort:"))
                .color(TextLabel.COLOR_LIGHT).shadow(true)
                .build();

        Dropdown<SortOrder> sortDropdown = Dropdown.<SortOrder>builder()
                .at(SORT_X, ROW2_Y)
                .size(SORT_W, ELEM_H)
                .items(List.of(SortOrder.values()))
                .label(SortOrder::display)
                .state(ControlsToolbarPanel::getSortOrder,
                       ControlsToolbarPanel::setSortOrder)
                .build();

        TextLabel filterLabel = TextLabel.builder()
                .at(FILTER_LABEL_X, labelTextY)
                .text(Component.literal("Filter:"))
                .color(TextLabel.COLOR_LIGHT).shadow(true)
                .build();

        Dropdown<RowFilter> filterDropdown = Dropdown.<RowFilter>builder()
                .at(FILTER_X, ROW2_Y)
                .size(FILTER_W, ELEM_H)
                .items(List.of(RowFilter.values()))
                .label(RowFilter::display)
                .state(ControlsToolbarPanel::getRowFilter,
                       ControlsToolbarPanel::setRowFilter)
                .build();

        // Collapse All and Expand All. A MenuKit Button's label is fixed when
        // it is built, so the switch is two buttons in one spot with opposite
        // visibleWhen conditions. Both hide under the flat sorts (no category
        // headers to fold) and when no list is open.
        Button collapseAllBtn = Button.builder()
                .at(COLLAPSE_X, ROW2_Y).size(COLLAPSE_W, ELEM_H)
                .label(Component.literal("Collapse All"))
                .onClick(() -> {
                    KeybinderyKeyBindsList l = KeybinderyKeyBindsScreen.currentList();
                    if (l != null) l.collapseAllGroups();
                })
                .visibleWhen(() -> {
                    KeybinderyKeyBindsList l = KeybinderyKeyBindsScreen.currentList();
                    return l != null && l.getSortOrder() == SortOrder.BY_CATEGORY
                            && !l.allVisibleGroupsCollapsed();
                })
                .build();

        Button expandAllBtn = Button.builder()
                .at(COLLAPSE_X, ROW2_Y).size(COLLAPSE_W, ELEM_H)
                .label(Component.literal("Expand All"))
                .onClick(() -> {
                    KeybinderyKeyBindsList l = KeybinderyKeyBindsScreen.currentList();
                    if (l != null) l.expandAllGroups();
                })
                .visibleWhen(() -> {
                    KeybinderyKeyBindsList l = KeybinderyKeyBindsScreen.currentList();
                    return l != null && l.getSortOrder() == SortOrder.BY_CATEGORY
                            && l.allVisibleGroupsCollapsed();
                })
                .build();

        // Popovers paint in a second renderOverlay pass, so the order here
        // doesn't matter; it is row 1 first, then row 2 left to right. Since
        // MenuKit 6.0.0, placement is set on the panel.
        Panel toolbar = Panel.builder("keybindery-controls-toolbar")
                .elements(List.<PanelElement>of(searchBox, chordBtn,
                                                sortLabel, filterLabel,
                                                collapseAllBtn, expandAllBtn,
                                                sortDropdown, filterDropdown))
                .style(PanelStyle.NONE)
                .position(PanelPosition.screenAnchor(InsideRegion.TOP_CENTER))
                .build();

        new VanillaScreenPanelAdapter(toolbar, /*padding=*/ 0)
                .on(KeybinderyKeyBindsScreen.class);
    }

    // Read and write the active list's filter state.

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
