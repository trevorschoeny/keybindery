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
     * mapping's single vanilla key as a one-key {@link Chord}, preserving the
     * original {@link InputConstants.Type} (KEYSYM, MOUSE, or SCANCODE).
     *
     * <p>Type preservation matters: {@code Chord.ofKey(int)} always assumes
     * KEYSYM type, which would coerce a MOUSE binding (left=0, right=1,
     * middle=2) into a KEYSYM key with the same value. Vanilla's name
     * fallback for those unknown KEYSYM values collides with the digit
     * keys' names, producing spurious conflicts (Trev 2026-05-24 — left
     * mouse vs hotbar 1, etc.). Wrapping the actual {@code Key} keeps the
     * Type intact so the chord's name-based comparator distinguishes them.
     */
    static Chord getChord(KeyMapping mapping) {
        Chord chord = ((IChordKeyMapping) mapping).keybindery$getChord();
        if (chord != null && !chord.isUnbound()) return chord;
        if (mapping.isUnbound()) return Chord.UNBOUND;
        InputConstants.Key key;
        try {
            key = InputConstants.getKey(mapping.saveString());
        } catch (Exception e) {
            return Chord.UNBOUND;
        }
        if (key.equals(InputConstants.UNKNOWN)) return Chord.UNBOUND;
        return new Chord(java.util.Set.of(key));
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
            // Use primaryKey() — preserves InputConstants.Type so a chord
            // whose primary is a MOUSE button stays MOUSE on the underlying
            // KeyMapping (and serializes as e.g. key.mouse.left, not
            // key.keyboard.0). The earlier path built KEYSYM from the
            // int code and silently downgraded mouse bindings.
            mapping.setKey(chord.primaryKey());
            ((IChordKeyMapping) mapping).keybindery$setChord(chord);
        }
        KeyMapping.resetMapping();
    }

    /**
     * Returns the keymapping's <b>default</b> chord — the chord state the
     * user gets back when they press the Reset button. Wraps
     * {@link KeyMapping#getDefaultKey()} as a single-key chord (preserving
     * Type so a mouse-default like Attack stays MOUSE), or {@link Chord#UNBOUND}
     * if the mapping was registered without a default key.
     *
     * <p>Used as the {@code .binding(default, ...)} arg on YACL options so
     * YACL's "reset to default" semantics line up with vanilla's notion of
     * a keymapping's default — without this helper, both call sites pass
     * {@code Chord.UNBOUND} as the default, and reset becomes "clear."
     */
    static Chord defaultChord(KeyMapping mapping) {
        if (mapping == null) return Chord.UNBOUND;
        InputConstants.Key defaultKey = mapping.getDefaultKey();
        if (defaultKey == null || defaultKey.equals(InputConstants.UNKNOWN)) return Chord.UNBOUND;
        return new Chord(java.util.Set.of(defaultKey));
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
