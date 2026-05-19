package com.trevorschoeny.keybindery.chord;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.Chord;
import net.minecraft.client.KeyMapping;

/**
 * Duck interface mixed into vanilla's {@link KeyMapping} so every keybinding
 * (vanilla or modded) supports a multi-key chord.
 *
 * <p>Cast any KeyMapping to this interface to access the chord:
 * <pre>
 *   Chord chord = ((IChordKeyMapping) someKeyMapping).keybindery$getChord();
 * </pre>
 *
 * <p>Migrated from MenuKit's {@code MKKeybindExt} (2026-05-18). The {@code MK*}
 * subclass-based approach ({@code MKKeyMapping}) was dropped — the mixin-on-
 * vanilla approach is universal and consumer-mod-agnostic, and the subclass
 * was unused downstream.
 */
public interface IChordKeyMapping {

    /**
     * Returns the multi-key chord for this KeyMapping, or null if no chord
     * has been set (single-key binding using vanilla's default behavior).
     */
    Chord keybindery$getChord();

    /**
     * Sets the multi-key chord for this KeyMapping. Pass null or {@link Chord#UNBOUND}
     * to clear and revert to vanilla single-key behavior.
     */
    void keybindery$setChord(Chord chord);

    // ── Convenience helpers (operate on any KeyMapping via the duck interface) ──

    /**
     * Returns the effective chord for the given mapping. If a multi-key chord
     * has been set via the duck interface, returns that. Otherwise wraps the
     * mapping's single vanilla key as a one-key {@link Chord}.
     */
    static Chord getChord(KeyMapping mapping) {
        Chord chord = ((IChordKeyMapping) mapping).keybindery$getChord();
        if (chord != null && !chord.isUnbound()) return chord;
        if (mapping.isUnbound()) return Chord.UNBOUND;
        int keyCode = getKeyCode(mapping);
        if (keyCode == InputConstants.UNKNOWN.getValue()) return Chord.UNBOUND;
        return Chord.ofKey(keyCode);
    }

    /**
     * Updates a KeyMapping's chord. Sets both the chord field (via duck
     * interface) and vanilla's base key (so vanilla's internal key→mapping
     * lookup still resolves), then rebuilds vanilla's map.
     */
    static void updateFromChord(KeyMapping mapping, Chord chord) {
        if (chord == null || chord.isUnbound()) {
            mapping.setKey(InputConstants.UNKNOWN);
            ((IChordKeyMapping) mapping).keybindery$setChord(Chord.UNBOUND);
        } else {
            int baseKey = chord.primaryKeyCode();
            if (baseKey == -1) mapping.setKey(InputConstants.UNKNOWN);
            else mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(baseKey));
            ((IChordKeyMapping) mapping).keybindery$setChord(chord);
        }
        KeyMapping.resetMapping();
    }

    /**
     * Returns the GLFW key code of the given KeyMapping's current key binding.
     * Works around {@code KeyMapping.key} being protected with no public getter
     * in vanilla MC 1.21.11.
     */
    static int getKeyCode(KeyMapping mapping) {
        try {
            return InputConstants.getKey(mapping.saveString()).getValue();
        } catch (Exception e) {
            return InputConstants.UNKNOWN.getValue();
        }
    }
}
