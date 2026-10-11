package com.trevorschoeny.keybindery.screen;

import net.minecraft.network.chat.Component;

/**
 * Single-choice row filter for the controls-screen toolbar. {@link #NONE}
 * turns filtering off; the other values are applied to the list through
 * {@code KeybinderyKeyBindsList.setRowFilter}.
 *
 * <p>Replaces the earlier {@code conflictsOnly} and {@code freeKeysOnly}
 * booleans with one dropdown.
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
