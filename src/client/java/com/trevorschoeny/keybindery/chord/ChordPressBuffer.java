package com.trevorschoeny.keybindery.chord;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * Records the latest KEY_DOWN time for every key Keybindery sees during this
 * session. Used by Simultaneous Mode: a chord fires when all its keys were
 * first pressed within a configurable time window.
 *
 * <p>Simultaneous Mode goes by the time of the first press only; whether the
 * key is released afterwards doesn't matter. This buffer holds those times.
 * The mixin that feeds it ({@code KeyboardChordPressMixin}) intercepts
 * vanilla's input dispatch and records the key on KEY_DOWN.
 *
 * <p>Left and right modifiers are stored as the left one, so lookups via
 * {@link #lastPress} see the same key whichever physical modifier was pressed.
 * Entries don't expire; the configured time window does the filtering. Memory
 * is bounded by the number of distinct keys pressed this session (a few KB at
 * most).
 *
 * <p>Singleton. Only this package's mixins write to it.
 */
public final class ChordPressBuffer {

    private static final ChordPressBuffer INSTANCE = new ChordPressBuffer();

    public static ChordPressBuffer get() { return INSTANCE; }

    private final Map<InputConstants.Key, Long> pressTimestamps = new HashMap<>();

    private ChordPressBuffer() {}

    /** Records a KEY_DOWN for the key at the given time. Right modifiers are stored as left. */
    public void recordPress(InputConstants.Key key, long timestampMs) {
        pressTimestamps.put(normalize(key), timestampMs);
    }

    /**
     * The time of the latest KEY_DOWN for the given key, or null if the key
     * hasn't been pressed this session.
     */
    public Long lastPress(InputConstants.Key key) {
        return pressTimestamps.get(normalize(key));
    }

    private static InputConstants.Key normalize(InputConstants.Key key) {
        if (key.getType() == InputConstants.Type.KEYBOARD) {
            int code = key.getValue();
            if (code == InputConstants.KEY_RSHIFT)   return InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_LSHIFT);
            if (code == InputConstants.KEY_RCONTROL) return InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_LCONTROL);
            if (code == InputConstants.KEY_RALT)     return InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_LALT);
            if (code == InputConstants.KEY_RGUI)   return InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_LGUI);
        }
        return key;
    }
}
