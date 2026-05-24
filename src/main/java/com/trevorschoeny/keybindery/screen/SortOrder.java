package com.trevorschoeny.keybindery.screen;

import net.minecraft.network.chat.Component;

/**
 * Sort orders for Keybindery's controls-screen toolbar. Default
 * ({@link #BY_CATEGORY}) matches vanilla's grouping for familiarity.
 */
public enum SortOrder {
    BY_CATEGORY("Category"),
    ALPHABETICAL("Alphabetical"),
    BY_KEY("Keybind Assignment");

    private final String displayName;

    SortOrder(String displayName) {
        this.displayName = displayName;
    }

    public Component display() {
        return Component.literal(displayName);
    }
}
