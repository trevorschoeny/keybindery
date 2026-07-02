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
 * Keybindery's filter/sort-capable replacement for vanilla's
 * {@link KeyBindsList}. Lives in vanilla's package so it can construct
 * vanilla's package-private {@code KeyEntry} subclass directly — that's
 * the entry shape vanilla's controls screen knows how to render.
 *
 * <p>Filter/sort state is owned here (search query, sort order, row
 * filter, search-chord keys). Setters refresh the entries list.
 *
 * <p>MISC fix: vanilla dumps every uncategorized keybind into the
 * "MISC" category. {@link #refreshEntries} groups MISC entries by mod
 * id (extracted from the keymapping name's namespace) so players see
 * "Movement / Inventory / [Mod A] / [Mod B]" instead of one mega-MISC.
 */
public class KeybinderyKeyBindsList extends KeyBindsList {

    private static final String MISC_CATEGORY_LABEL_KEY = "key.categories.misc";

    private String searchQuery = "";
    private SortOrder sortOrder = SortOrder.BY_CATEGORY;
    private RowFilter rowFilter = RowFilter.NONE;
    /** OR-semantics: any row whose chord shares ≥1 key with this set passes.
     *  Driven by the toolbar's Search Keybind chord-capture button. */
    private final Set<InputConstants.Key> searchChordKeys = new HashSet<>();

    /** Group keys the user has collapsed by clicking the category header.
     *  STATIC — vanilla recreates the whole screen (and this list) on
     *  window resize; session-scoped state keeps the user's collapse
     *  choices across that. Not persisted to disk: collapse is a browsing
     *  gesture, not a setting. */
    private static final Set<String> collapsedGroups = new HashSet<>();

    public KeybinderyKeyBindsList(KeyBindsScreen screen, Minecraft mc) {
        super(screen, mc);
        // Default 20px row height — KeybinderyKeyEntry uses a one-row
        // layout (name on left, chord + Conflicts/Reset icons on right)
        // so vanilla's height is correct.
    }

    // ── Filter/sort state setters (refresh + scroll-to-top on change) ──────

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

    /** Filter/sort change just changed what rows the user sees — keeping
     *  their old scroll offset would leave them looking at the middle of
     *  a possibly-shorter (or differently-ordered) list. Reset to top so
     *  the new filter result is immediately visible. */
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

    // ── Collapse-all / expand-all (toolbar button) ──────────────────────────
    // Scoped to VISIBLE groups (those with ≥1 mapping passing the current
    // filter) — "collapse all" folds what the user is looking at; groups
    // hidden by a filter keep their individual state.

    public void collapseAllGroups() {
        collapsedGroups.addAll(visibleGroupKeys());
        refreshEntries();
    }

    public void expandAllGroups() {
        collapsedGroups.removeAll(visibleGroupKeys());
        refreshEntries();
    }

    /** True iff every currently-visible group is collapsed — drives the
     *  toolbar's Collapse All ↔ Expand All button swap. False when nothing
     *  is visible (button hides itself elsewhere on empty lists). */
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

    // ── Entry rebuild ────────────────────────────────────────────────────────

    @Override
    public void refreshEntries() {
        Minecraft mc = this.minecraft;
        if (mc == null || mc.options == null || mc.options.keyMappings == null) {
            super.refreshEntries();
            return;
        }

        this.clearEntries();

        // Collect filtered keymappings.
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
        // Search by name + category label (name-only is fine for v1; the
        // search-by-chord case is handled separately by searchChordKeys).
        if (!searchQuery.isEmpty()) {
            String name = Component.translatable(km.getName()).getString().toLowerCase();
            String category = km.getCategory().label().getString().toLowerCase();
            if (!name.contains(searchQuery) && !category.contains(searchQuery)) return false;
        }
        // Single-select row filter — None / Conflicts / Not Bound.
        switch (rowFilter) {
            case CONFLICTS -> { if (!ChordConflicts.hasAnyConflict(km)) return false; }
            case FREE_KEYS -> { if (!km.isUnbound()) return false; }
            case NONE -> {}
        }
        // Search-chord filter (OR semantics): chord shares at least one key
        // with the captured search chord. Empty set = no filtering.
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
        // Group mappings by category, with MISC fix: subdivide MISC by mod id.
        Map<String, List<KeyMapping>> groups = new LinkedHashMap<>();
        // First pass: gather vanilla category groups in vanilla's existing order.
        for (KeyMapping km : mappings) {
            String groupKey = groupKeyFor(km);
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(km);
        }

        for (Map.Entry<String, List<KeyMapping>> e : groups.entrySet()) {
            String groupKey = e.getKey();
            List<KeyMapping> groupMappings = e.getValue();
            // Sort within the group alphabetically for readability.
            groupMappings.sort(Comparator.comparing(m -> Component.translatable(m.getName()).getString()));
            // Category header — one clickable, collapsible entry type for
            // both real vanilla categories and synthesized mod-id groups
            // (MISC fix); only the label source differs.
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
     * Group key for a keymapping: vanilla category label normally, but for
     * MISC-categorized keybinds, derive a mod-id-based group so a wall of
     * unrelated mod keybinds doesn't pile into one section.
     */
    private static String groupKeyFor(KeyMapping km) {
        KeyMapping.Category cat = km.getCategory();
        // Vanilla MISC category? Use the keymap's namespace as the group.
        String catLabel = cat.label().getString();
        boolean isMisc = isMiscCategory(cat);
        if (isMisc) {
            String modGroup = modIdFromKeymapName(km.getName());
            if (modGroup != null) return modGroup;
        }
        return catLabel;
    }

    private static boolean isMiscCategory(KeyMapping.Category category) {
        // The vanilla MISC category's label is registered via the lang key
        // "key.categories.misc". Translation produces "Miscellaneous" in en_us
        // but the .toString() of the Component contains the key form too in
        // some cases. Compare the raw label key.
        Component label = category.label();
        if (label.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
            return MISC_CATEGORY_LABEL_KEY.equals(tc.getKey());
        }
        return false;
    }

    /**
     * Extract a human-readable mod id from a keymapping name. Names look
     * like "key.modname.action" (Fabric convention) or "key.action" (vanilla).
     * The middle segment is the namespace; we titlecase it for display.
     * Returns null if no namespace can be derived.
     */
    private static @Nullable String modIdFromKeymapName(String name) {
        if (name == null) return null;
        // Skip the "key." prefix.
        String body = name.startsWith("key.") ? name.substring(4) : name;
        int dot = body.indexOf('.');
        if (dot <= 0) return null;
        String namespace = body.substring(0, dot);
        if (namespace.isEmpty()) return null;
        // Titlecase first letter for display.
        return Character.toUpperCase(namespace.charAt(0)) + namespace.substring(1);
    }

    /**
     * For groups that came from a real vanilla category, return that
     * KeyMapping.Category so we use the same CategoryEntry rendering.
     * For mod-id synthetic groups, return null and we'll render our own.
     */
    private static @Nullable KeyMapping.Category vanillaCategoryFor(KeyMapping representative, String groupKey) {
        KeyMapping.Category cat = representative.getCategory();
        if (groupKey.equals(cat.label().getString())) return cat;
        return null;
    }

    private void addKeyEntry(KeyMapping km) {
        Component name = Component.translatable(km.getName());
        // Default 20px row height — KeybinderyKeyEntry is one row.
        addEntry(new KeybinderyKeyEntry(this, km, name));
    }

    /**
     * Clickable, collapsible category header — used for BOTH real vanilla
     * categories and MISC-fix mod-id groups (label source differs; behavior
     * is identical). Replaces vanilla's {@code CategoryEntry} (read-only
     * centered text) with:
     *
     * <ul>
     *   <li>a ▼ (expanded) / ▶ (collapsed) indicator before the label</li>
     *   <li>click-to-toggle — collapsing hides the group's key rows,
     *       preserving scroll position (no jump-to-top; this is a browsing
     *       gesture, not a filter change)</li>
     * </ul>
     *
     * <p>Non-static so it can be a vanilla-inner-class peer (gets the outer
     * KeyBindsList implicitly).
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
            // Indicator + label as one centered string, vertically centered
            // in the 20px row. Position derives from the entry's CONTENT
            // rect — the int params are mouse coordinates, not origins.
            Component headerText = Component.literal(collapsed ? "▶ " : "▼ ")
                    .append(label);
            int centerX = this.getContentX() + this.getContentWidth() / 2;
            int textY = this.getContentY() + (this.getContentHeight() - 9) / 2;
            // Subtle hover tint invites the click.
            int color = hovered ? 0xFFFFFFA0 : 0xFFFFFFFF;
            graphics.centeredText(mc.font, headerText, centerX, textY, color);
        }

        /** Toggle collapse on left-click. The list's click dispatch already
         *  hit-tested the row, so any click landing here is ours. */
        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
                                    boolean doubleClick) {
            if (event.button() != 0) return false;
            if (!collapsedGroups.remove(groupKey)) {
                collapsedGroups.add(groupKey);
            }
            net.minecraft.client.gui.components.AbstractWidget.playButtonClickSound(
                    Minecraft.getInstance().getSoundManager());
            // Plain refresh — deliberately NOT refreshAndScrollToTop; the
            // user is folding a section in place, not changing what the
            // list is filtered to.
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
