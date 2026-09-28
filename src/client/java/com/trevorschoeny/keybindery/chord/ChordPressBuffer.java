package com.trevorschoeny.keybindery.chord;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * Records the most-recent KEY_DOWN timestamp for every key seen by Keybindery
 * during this play session. Used for simultaneous-mode chord detection:
 * a chord fires when all its keys have been initially pressed within a
 * configurable time window.
 *
 * <p>Per Trev's spec (2026-05-18), simultaneous-mode chord detection is
 * timestamp-based on the initial press event — release state is irrelevant
 * after the press. This buffer holds those timestamps. The mixin that feeds
 * it ({@code KeyboardChordPressMixin}) intercepts vanilla's input dispatch
 * and stamps the key on KEY_DOWN.
 *
 * <p>Keys are L/R-normalized on write so chord lookups via {@link #lastPress}
 * see a consistent key identity regardless of which physical modifier was
 * pressed. Entries don't expire — the configured time window does the gating.
 * Memory cost is bounded by the number of distinct keys a player has ever
 * pressed in this session (small, single-digit-KB at most).
 *
 * <p>Singleton. Read-only outside this package's mixin layer.
 */
public final class ChordPressBuffer {

    private static final ChordPressBuffer INSTANCE = new ChordPressBuffer();

    /** Singleton accessor. */
    public static ChordPressBuffer get() { return INSTANCE; }

    private final Map<InputConstants.Key, Long> pressTimestamps = new HashMap<>();

    private ChordPressBuffer() {}

    /**
     * Records a KEY_DOWN event for the given key at the given time. L/R
     * modifier variants are normalized to LEFT before storage.
     */
    public void recordPress(InputConstants.Key key, long timestampMs) {
        pressTimestamps.put(normalize(key), timestampMs);
    }

    /**
     * Returns the timestamp of the most-recent KEY_DOWN for the given key,
     * or null if the key has never been pressed in this session.
     */
    public Long lastPress(InputConstants.Key key) {
        return pressTimestamps.get(normalize(key));
    }

    private static InputConstants.Key normalize(InputConstants.Key key) {
        if (key.getType() == InputConstants.Type.KEYSYM) {
            int code = key.getValue();
            if (code == InputConstants.KEY_RSHIFT)   return InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LSHIFT);
            if (code == InputConstants.KEY_RCONTROL) return InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LCONTROL);
            if (code == InputConstants.KEY_RALT)     return InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LALT);
            if (code == InputConstants.KEY_RSUPER)   return InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LSUPER);
        }
        return key;
    }
}
