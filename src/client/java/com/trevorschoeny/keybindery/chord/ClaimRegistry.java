package com.trevorschoeny.keybindery.chord;

import net.minecraft.client.KeyMapping;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tracks which {@link KeyMapping}s a consumer mod's own config UI has
 * "claimed" (through the in-config keybind API). Keybindery's auto-append
 * fallback skips claimed mappings: they already show in the consumer's
 * screen, so showing them again in vanilla controls, ModMenu, Cloth or YACL
 * would be redundant.
 *
 * <p>Keybindery's controls screen still includes claimed mappings, since the
 * controls screen is where every keybind belongs, whatever a consumer's own
 * UI does. Only the auto-append fallback checks this registry.
 *
 * <p>The set is backed by a {@link WeakHashMap} so it doesn't keep KeyMapping
 * instances alive if the consumer mod is reloaded mid-session. That is rare,
 * but it costs nothing.
 */
public final class ClaimRegistry {

    private static final Set<KeyMapping> CLAIMED =
            Collections.newSetFromMap(new WeakHashMap<>());

    private ClaimRegistry() {}

    public static void mark(KeyMapping mapping) {
        if (mapping == null) return;
        CLAIMED.add(mapping);
    }

    public static boolean isClaimed(KeyMapping mapping) {
        return mapping != null && CLAIMED.contains(mapping);
    }
}
