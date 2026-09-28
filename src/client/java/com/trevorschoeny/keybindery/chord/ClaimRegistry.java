package com.trevorschoeny.keybindery.chord;

import net.minecraft.client.KeyMapping;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tracks which {@link KeyMapping} instances have been "claimed" by a
 * consumer mod's own config UI (via the F1 API). Keybindery's auto-append
 * fallback (F2) skips claimed mappings — they're already surfaced in the
 * consumer's screen, so re-showing them in vanilla controls / ModMenu /
 * Cloth / YACL would be redundant.
 *
 * <p>The vanilla controls-screen replacement (F4) still includes claimed
 * mappings — vanilla controls is the universal surface where every
 * keybind belongs, regardless of consumer UX. Only the AUTO-APPEND
 * fallback paths (F2) consult this registry.
 *
 * <p>Uses {@link WeakHashMap}-backed set so registry entries don't pin
 * KeyMapping instances if the consumer mod is reloaded mid-session. In
 * practice that's rare, but the GC-friendliness costs nothing.
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
