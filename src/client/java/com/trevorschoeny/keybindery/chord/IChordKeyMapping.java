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
 * <p>Moved here from MenuKit's {@code MKKeybindExt}. MenuKit's subclass
 * approach ({@code MKKeyMapping}) was dropped: a mixin on vanilla works for
 * every mod without them doing anything, and nothing used the subclass.
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

    // Helpers that work on any KeyMapping through the duck interface.

    /**
     * Returns the effective chord for the given mapping. If a multi-key chord
     * has been set via the duck interface, returns that. Otherwise wraps the
     * mapping's single vanilla key as a one-key {@link Chord}, preserving the
     * original {@link InputConstants.Type} (KEYSYM, MOUSE, or SCANCODE).
     *
     * <p>Keeping the type matters: {@code Chord.ofKey(int)} always assumes
     * KEYSYM, which would turn a MOUSE binding (left=0, right=1, middle=2)
     * into a KEYSYM key with the same value. Vanilla's fallback name for those
     * unknown KEYSYM values collides with the digit keys' names, which gave
     * false conflicts (left mouse against hotbar 1, and so on). Wrapping the
     * actual {@code Key} keeps the type, so the chord's name-based comparison
     * tells them apart.
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
     * Updates a KeyMapping's chord. Sets both the chord field (through the duck
     * interface) and vanilla's base key (so vanilla's own key-to-mapping lookup
     * still works), then rebuilds vanilla's map.
     */
    static void updateFromChord(KeyMapping mapping, Chord chord) {
        if (chord == null || chord.isUnbound()) {
            mapping.setKey(InputConstants.UNKNOWN);
            ((IChordKeyMapping) mapping).keybindery$setChord(Chord.UNBOUND);
        } else {
            // primaryKey() keeps the InputConstants.Type, so a chord whose
            // primary is a MOUSE button stays MOUSE on the KeyMapping (and
            // saves as key.mouse.left, not key.keyboard.0). Building a KEYSYM
            // from the int code instead silently broke mouse bindings.
            mapping.setKey(chord.primaryKey());
            ((IChordKeyMapping) mapping).keybindery$setChord(chord);
        }
        KeyMapping.resetMapping();
    }

    /**
     * Returns the key mapping's <b>default</b> chord, the one the player gets
     * back from the Reset button. Wraps {@link KeyMapping#getDefaultKey()} as
     * a single-key chord (keeping the type, so a mouse default like Attack
     * stays MOUSE), or {@link Chord#UNBOUND} if the mapping has no default key.
     *
     * <p>Used as the {@code .binding(default, ...)} argument on YACL options so
     * YACL's reset to default matches vanilla's idea of a key mapping's
     * default. Without it, both call sites would pass {@code Chord.UNBOUND} as
     * the default, and reset would clear the binding.
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
     * in vanilla MC 26.2.
     */
    static int getKeyCode(KeyMapping mapping) {
        try {
            return InputConstants.getKey(mapping.saveString()).getValue();
        } catch (Exception e) {
            return InputConstants.UNKNOWN.getValue();
        }
    }
}
