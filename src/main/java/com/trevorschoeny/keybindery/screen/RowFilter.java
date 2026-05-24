package com.trevorschoeny.keybindery.screen;

import net.minecraft.network.chat.Component;

/**
 * Single-select row filter for the F4 toolbar. {@link #NONE} disables
 * filtering; the other values apply at the list level via
 * {@code KeybinderyKeyBindsList.setRowFilter}.
 *
 * <p>Replaces the previous {@code conflictsOnly} + {@code freeKeysOnly}
 * boolean pair — Trev's spec (2026-05-19) collapses them to one dropdown.
 */
public enum RowFilter {
    NONE("None"),
    CONFLICTS("Conflicts"),
    FREE_KEYS("Not Bound");

    private final String displayName;

    RowFilter(String displayName) {
        this.displayName = displayName;
    }

    public Component display() {
        return Component.literal(displayName);
    }
}
