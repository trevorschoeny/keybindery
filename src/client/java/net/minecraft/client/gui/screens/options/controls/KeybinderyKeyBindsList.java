package net.minecraft.client.gui.screens.options.controls;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.Chord;
import com.trevorschoeny.keybindery.chord.ChordConflicts;
import com.trevorschoeny.keybindery.chord.IChordKeyMapping;
import com.trevorschoeny.keybindery.screen.RowFilter;
import com.trevorschoeny.keybindery.screen.SortOrder;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keybindery's replacement for vanilla's {@link KeyBindsList}, which can filter
 * and sort. It lives in vanilla's package so it can construct vanilla's
 * package-private {@code KeyEntry} subclass directly, the entry type vanilla's
 * controls screen knows how to render.
 *
 * <p>The filter and sort state lives here (search query, sort order, row
 * filter, search-chord keys). The setters refresh the entries.
 *
 * <p>The MISC fix: vanilla puts every uncategorized keybind in the "MISC"
 * category. {@link #refreshEntries} splits MISC by mod id (taken from the key
 * mapping name's namespace), so players see "Movement / Inventory / [Mod A] /
 * [Mod B]" instead of one huge MISC section.
 */
public class KeybinderyKeyBindsList extends KeyBindsList {

    private static final String MISC_CATEGORY_LABEL_KEY = "key.categories.misc";

    private String searchQuery = "";
    private SortOrder sortOrder = SortOrder.BY_CATEGORY;
    private RowFilter rowFilter = RowFilter.NONE;
    /**
     * Any row whose chord shares at least one key with this set passes. Set by
     * the toolbar's Search Keybind button.
     */
    private final Set<InputConstants.Key> searchChordKeys = new HashSet<>();

    /**
     * Groups the player has collapsed by clicking the category header. Static,
     * because vanilla rebuilds the whole screen (and this list) when the window
     * is resized, and the collapsed groups should survive that for the session.
     * Not saved to disk: collapsing is part of browsing, not a setting.
     */
    private static final Set<String> collapsedGroups = new HashSet<>();

    public KeybinderyKeyBindsList(KeyBindsScreen screen, Minecraft mc) {
        super(screen, mc);
        // Vanilla's default 20px row height is right, since KeybinderyKeyEntry
        // is one row (name on the left, chord and the Conflicts and Reset
        // icons on the right).
    }

    // Filter and sort setters. A change refreshes and scrolls to the top.

    public void setSearchQuery(String query) {
        if (query == null) query = "";
        if (this.searchQuery.equals(query)) return;
        this.searchQuery = query.toLowerCase();
        refreshAndScrollToTop();
    }

    public void setSortOrder(SortOrder order) {
        if (order == null) order = SortOrder.BY_CATEGORY;
        if (this.sortOrder == order) return;
        this.sortOrder = order;
        refreshAndScrollToTop();
    }

    public void setRowFilter(RowFilter filter) {
        if (filter == null) filter = RowFilter.NONE;
        if (this.rowFilter == filter) return;
        this.rowFilter = filter;
        refreshAndScrollToTop();
    }

    public void setSearchChordKeys(Set<InputConstants.Key> keys) {
        this.searchChordKeys.clear();
        if (keys != null) this.searchChordKeys.addAll(keys);
        refreshAndScrollToTop();
    }

    public void clearSearchChordKeys() {
        if (this.searchChordKeys.isEmpty()) return;
        this.searchChordKeys.clear();
        refreshAndScrollToTop();
    }

    // A filter or sort change changes which rows the player sees. Keeping the
    // old scroll offset would leave them in the middle of a possibly shorter
    // or reordered list, so scroll to the top to show the new result at once.
    private void refreshAndScrollToTop() {
        refreshEntries();
        setScrollAmount(0);
    }

    public Set<InputConstants.Key> getSearchChordKeys() {
        return Collections.unmodifiableSet(searchChordKeys);
    }

    public String getSearchQuery() { return searchQuery; }
    public SortOrder getSortOrder() { return sortOrder; }
    public RowFilter getRowFilter() { return rowFilter; }

    // Collapse All and Expand All (toolbar button). They only touch visible
    // groups (those with at least one mapping passing the current filter):
    // "collapse all" folds what the player is looking at, and groups hidden by
    // a filter keep their own state.

    public void collapseAllGroups() {
        collapsedGroups.addAll(visibleGroupKeys());
        refreshEntries();
    }

    public void expandAllGroups() {
        collapsedGroups.removeAll(visibleGroupKeys());
        refreshEntries();
    }

    /**
     * True if every visible group is collapsed; decides whether the toolbar
     * shows Collapse All or Expand All. False when nothing is visible (the
     * button hides itself on empty lists anyway).
     */
    public boolean allVisibleGroupsCollapsed() {
        Set<String> keys = visibleGroupKeys();
        return !keys.isEmpty() && collapsedGroups.containsAll(keys);
    }

    private Set<String> visibleGroupKeys() {
        Set<String> keys = new LinkedHashSet<>();
        Minecraft mc = this.minecraft;
        if (mc == null || mc.options == null || mc.options.keyMappings == null) return keys;
        for (KeyMapping km : mc.options.keyMappings) {
            if (passesFilter(km)) keys.add(groupKeyFor(km));
        }
        return keys;
    }

    @Override
    public void refreshEntries() {
        Minecraft mc = this.minecraft;
        if (mc == null || mc.options == null || mc.options.keyMappings == null) {
            super.refreshEntries();
            return;
        }

        this.clearEntries();

        List<KeyMapping> filtered = new ArrayList<>();
        for (KeyMapping km : mc.options.keyMappings) {
            if (passesFilter(km)) filtered.add(km);
        }

        // Sort and group as required by the selected SortOrder.
        if (sortOrder == SortOrder.BY_CATEGORY) {
            renderByCategory(filtered);
        } else {
            renderFlat(filtered);
        }
    }

    private boolean passesFilter(KeyMapping km) {
        // Search by name and category label. Searching by chord is handled
        // separately, by searchChordKeys.
        if (!searchQuery.isEmpty()) {
            String name = Component.translatable(km.getName()).getString().toLowerCase();
            String category = km.getCategory().label().getString().toLowerCase();
            if (!name.contains(searchQuery) && !category.contains(searchQuery)) return false;
        }
        // Single-choice row filter: None, Conflicts or Not Bound.
        switch (rowFilter) {
            case CONFLICTS -> { if (!ChordConflicts.hasAnyConflict(km)) return false; }
            case FREE_KEYS -> { if (!km.isUnbound()) return false; }
            case NONE -> {}
        }
        // Search-chord filter: the chord must share at least one key with the
        // captured search chord. An empty set means no filtering.
        if (!searchChordKeys.isEmpty()) {
            Chord chord = IChordKeyMapping.getChord(km);
            if (chord.isUnbound()) return false;
            boolean overlap = false;
            for (InputConstants.Key k : searchChordKeys) {
                if (chord.getKeys().contains(k)) { overlap = true; break; }
            }
            if (!overlap) return false;
        }
        return true;
    }

    private void renderFlat(List<KeyMapping> mappings) {
        sortFlat(mappings);
        for (KeyMapping km : mappings) {
            addKeyEntry(km);
        }
    }

    private void sortFlat(List<KeyMapping> mappings) {
        switch (sortOrder) {
            case ALPHABETICAL ->
                    mappings.sort(Comparator.comparing(m -> Component.translatable(m.getName()).getString()));
            case BY_KEY ->
                    mappings.sort(Comparator.comparing((KeyMapping m) -> m.saveString()));
            default -> {}
        }
    }

    private void renderByCategory(List<KeyMapping> mappings) {
        // Group mappings by category, with the MISC fix: MISC is split by mod id.
        Map<String, List<KeyMapping>> groups = new LinkedHashMap<>();
        // First pass: collect the groups in vanilla's existing order.
        for (KeyMapping km : mappings) {
            String groupKey = groupKeyFor(km);
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(km);
        }

        for (Map.Entry<String, List<KeyMapping>> e : groups.entrySet()) {
            String groupKey = e.getKey();
            List<KeyMapping> groupMappings = e.getValue();
            // Sort within the group alphabetically for readability.
            groupMappings.sort(Comparator.comparing(m -> Component.translatable(m.getName()).getString()));
            // Category header: one clickable, collapsible entry type for both
            // real vanilla categories and the mod-id groups made by the MISC
            // fix; only where the label comes from differs.
            KeyMapping.Category cat = vanillaCategoryFor(groupMappings.get(0), groupKey);
            Component label = cat != null ? cat.label() : Component.literal(groupKey);
            addEntry(new CollapsibleCategoryEntry(label, groupKey));
            // Collapsed groups contribute only their header row.
            if (!collapsedGroups.contains(groupKey)) {
                for (KeyMapping km : groupMappings) {
                    addKeyEntry(km);
                }
            }
        }
    }

    /**
     * Group key for a key mapping: normally the vanilla category label, but for
     * keybinds in MISC, a group based on the mod id, so unrelated mods'
     * keybinds don't all pile into one section.
     */
    private static String groupKeyFor(KeyMapping km) {
        KeyMapping.Category cat = km.getCategory();
        // In vanilla's MISC category, use the key mapping's namespace as the group.
        String catLabel = cat.label().getString();
        boolean isMisc = isMiscCategory(cat);
        if (isMisc) {
            String modGroup = modIdFromKeymapName(km.getName());
            if (modGroup != null) return modGroup;
        }
        return catLabel;
    }

    private static boolean isMiscCategory(KeyMapping.Category category) {
        // Vanilla's MISC category label uses the lang key "key.categories.misc".
        // It translates to "Miscellaneous" in en_us, but the Component's
        // .toString() sometimes contains the key form too, so compare the raw
        // label key.
        Component label = category.label();
        if (label.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
            return MISC_CATEGORY_LABEL_KEY.equals(tc.getKey());
        }
        return false;
    }

    /**
     * A readable mod id from a key mapping name. Names look like
     * "key.modname.action" (Fabric convention) or "key.action" (vanilla). The
     * middle part is the namespace, capitalized for display. Returns null if
     * there is no namespace.
     */
    private static @Nullable String modIdFromKeymapName(String name) {
        if (name == null) return null;
        // Skip the "key." prefix.
        String body = name.startsWith("key.") ? name.substring(4) : name;
        int dot = body.indexOf('.');
        if (dot <= 0) return null;
        String namespace = body.substring(0, dot);
        if (namespace.isEmpty()) return null;
        return Character.toUpperCase(namespace.charAt(0)) + namespace.substring(1);
    }

    /**
     * For groups that came from a real vanilla category, returns that
     * KeyMapping.Category so its own label is used. For the mod-id groups,
     * returns null and the group key is used as the label.
     */
    private static @Nullable KeyMapping.Category vanillaCategoryFor(KeyMapping representative, String groupKey) {
        KeyMapping.Category cat = representative.getCategory();
        if (groupKey.equals(cat.label().getString())) return cat;
        return null;
    }

    private void addKeyEntry(KeyMapping km) {
        Component name = Component.translatable(km.getName());
        // Default 20px row height; KeybinderyKeyEntry is one row.
        addEntry(new KeybinderyKeyEntry(this, km, name));
    }

    /**
     * Clickable, collapsible category header, used for both real vanilla
     * categories and the MISC fix's mod-id groups (only the label source
     * differs). Replaces vanilla's {@code CategoryEntry} (plain centered text)
     * with:
     *
     * <ul>
     *   <li>a ▼ (expanded) or ▶ (collapsed) marker before the label</li>
     *   <li>click to toggle: collapsing hides the group's key rows and keeps
     *       the scroll position (no jump to the top, since this is browsing,
     *       not a filter change)</li>
     * </ul>
     *
     * <p>Non-static, like vanilla's inner entry classes, so it gets the outer
     * KeyBindsList implicitly.
     */
    private class CollapsibleCategoryEntry extends KeyBindsList.Entry {
        private final Component label;
        private final String groupKey;

        CollapsibleCategoryEntry(Component label, String groupKey) {
            this.label = label;
            this.groupKey = groupKey;
        }

        @Override
        public void extractContent(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                  int mouseX, int mouseY, boolean hovered, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            boolean collapsed = collapsedGroups.contains(groupKey);
            // Marker and label as one centered string, vertically centered in
            // the 20px row. The position comes from the entry's content rect;
            // the int parameters are mouse coordinates, not origins.
            Component headerText = Component.literal(collapsed ? "▶ " : "▼ ")
                    .append(label);
            int centerX = this.getContentX() + this.getContentWidth() / 2;
            int textY = this.getContentY() + (this.getContentHeight() - 9) / 2;
            // A light tint on hover shows it can be clicked.
            int color = hovered ? 0xFFFFFFA0 : 0xFFFFFFFF;
            graphics.centeredText(mc.font, headerText, centerX, textY, color);
        }

        /**
         * Toggles collapse on left-click. The list already hit-tested the row,
         * so any click that reaches here is on this header.
         */
        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
                                    boolean doubleClick) {
            if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
            if (!collapsedGroups.remove(groupKey)) {
                collapsedGroups.add(groupKey);
            }
            net.minecraft.client.gui.components.AbstractWidget.playButtonClickSound(
                    Minecraft.getInstance().getSoundManager());
            // A plain refresh, deliberately not refreshAndScrollToTop: the
            // player is folding a section in place, not changing the filter.
            refreshEntries();
            return true;
        }

        @Override
        public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return List.of();
        }

        @Override
        public void refreshEntry() {}
    }
}
