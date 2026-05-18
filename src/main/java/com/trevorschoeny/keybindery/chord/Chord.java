package com.trevorschoeny.keybindery.chord;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A multi-key chord descriptor. ANY combination of simultaneously-held keys
 * and/or mouse buttons forms a binding. There is no distinction between
 * "modifier" and "base" keys at the data level — bindings like "K+E+2",
 * "Mouse4+Shift", or "A+B+C" are first-class.
 *
 * <p><b>L/R normalization:</b> Left and right variants of Shift, Ctrl, Alt,
 * and Super are normalized to the LEFT variant at construction time, so
 * LShift+I and RShift+I resolve to the same binding.
 *
 * <p><b>Serialization:</b> Keys joined by "+" by their {@link InputConstants.Key#getName()}.
 * Example: {@code "key.keyboard.left.shift+key.keyboard.i"}.
 *
 * <p>Migrated from MenuKit's {@code MKKeybind} (2026-05-18) per Trev's
 * directive; chord-keybind work belongs in @ Keybindery per §0030, not in
 * @ MenuKit (UI-only per its §0042). V1 modifier-bitmask compat that lived
 * on MKKeybind was dropped — no consumer mod has V1-persisted state since
 * MK's chord code was unused downstream.
 */
public final class Chord {

    /** An unbound chord — matches nothing. Used as the default for optional bindings. */
    public static final Chord UNBOUND = new Chord(Set.of());

    /** Maximum number of keys in a chord. Keeps chord-capture and display
     *  bounded; six is more than enough for "Ctrl+Shift+Alt+K+E+2" — the
     *  upper limit of what's physically practical. */
    public static final int MAX_CHORD = 6;

    private final SortedSet<InputConstants.Key> keys;

    public Chord(Set<InputConstants.Key> rawKeys) {
        TreeSet<InputConstants.Key> normalized = new TreeSet<>(
                Comparator.comparing(InputConstants.Key::getName));
        for (InputConstants.Key key : rawKeys) {
            if (key.equals(InputConstants.UNKNOWN)) continue;
            normalized.add(normalizeKey(key));
        }
        if (normalized.size() > MAX_CHORD) {
            TreeSet<InputConstants.Key> capped = new TreeSet<>(
                    Comparator.comparing(InputConstants.Key::getName));
            int count = 0;
            for (InputConstants.Key k : normalized) {
                if (count >= MAX_CHORD) break;
                capped.add(k);
                count++;
            }
            normalized = capped;
        }
        this.keys = Collections.unmodifiableSortedSet(normalized);
    }

    public boolean isUnbound() { return keys.isEmpty(); }
    public int size() { return keys.size(); }
    public SortedSet<InputConstants.Key> getKeys() { return keys; }

    public boolean isSingleKeyboard() {
        return keys.size() == 1 && keys.first().getType() == InputConstants.Type.KEYSYM;
    }

    public InputConstants.Key getSingleKey() {
        if (keys.size() == 1) return keys.first();
        return InputConstants.UNKNOWN;
    }

    private static InputConstants.Key normalizeKey(InputConstants.Key key) {
        if (key.getType() == InputConstants.Type.KEYSYM) {
            int code = key.getValue();
            if (code == InputConstants.KEY_RSHIFT)   return InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LSHIFT);
            if (code == InputConstants.KEY_RCONTROL) return InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LCONTROL);
            if (code == InputConstants.KEY_RALT)     return InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LALT);
            if (code == InputConstants.KEY_RSUPER)   return InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LSUPER);
        }
        return key;
    }

    public static boolean isTraditionalModifier(InputConstants.Key key) {
        if (key.getType() != InputConstants.Type.KEYSYM) return false;
        int code = key.getValue();
        return code == InputConstants.KEY_LSHIFT || code == InputConstants.KEY_RSHIFT
                || code == InputConstants.KEY_LCONTROL || code == InputConstants.KEY_RCONTROL
                || code == InputConstants.KEY_LALT || code == InputConstants.KEY_RALT
                || code == InputConstants.KEY_LSUPER || code == InputConstants.KEY_RSUPER;
    }

    public static boolean isModifierKey(int keyCode) {
        return keyCode == InputConstants.KEY_LSHIFT || keyCode == InputConstants.KEY_RSHIFT
                || keyCode == InputConstants.KEY_LCONTROL || keyCode == InputConstants.KEY_RCONTROL
                || keyCode == InputConstants.KEY_LALT || keyCode == InputConstants.KEY_RALT
                || keyCode == InputConstants.KEY_LSUPER || keyCode == InputConstants.KEY_RSUPER;
    }

    // ── Default-mode activation: all keys currently held (GLFW poll) ────────

    /**
     * Default-mode check: are ALL chord keys currently held, per GLFW poll?
     * L/R modifier variants both count when the normalized form is in the chord.
     */
    public boolean isActiveHeld(long windowHandle) {
        if (isUnbound()) return false;
        for (InputConstants.Key key : keys) {
            if (!isKeyHeld(windowHandle, key)) return false;
        }
        return true;
    }

    private static boolean isKeyHeld(long windowHandle, InputConstants.Key key) {
        if (key.getType() == InputConstants.Type.MOUSE) {
            return org.lwjgl.glfw.GLFW.glfwGetMouseButton(windowHandle, key.getValue())
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }
        int code = key.getValue();
        if (code == InputConstants.KEY_LSHIFT)   return glfwKey(windowHandle, InputConstants.KEY_LSHIFT)   || glfwKey(windowHandle, InputConstants.KEY_RSHIFT);
        if (code == InputConstants.KEY_LCONTROL) return glfwKey(windowHandle, InputConstants.KEY_LCONTROL) || glfwKey(windowHandle, InputConstants.KEY_RCONTROL);
        if (code == InputConstants.KEY_LALT)     return glfwKey(windowHandle, InputConstants.KEY_LALT)     || glfwKey(windowHandle, InputConstants.KEY_RALT);
        if (code == InputConstants.KEY_LSUPER)   return glfwKey(windowHandle, InputConstants.KEY_LSUPER)   || glfwKey(windowHandle, InputConstants.KEY_RSUPER);
        return glfwKey(windowHandle, code);
    }

    private static boolean glfwKey(long windowHandle, int glfwKeyCode) {
        return org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, glfwKeyCode) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    // ── Simultaneous-mode activation: all keys initially pressed within window ──

    /**
     * Simultaneous-mode check: have ALL chord keys been initially pressed within
     * the last {@code windowMs}, per the press-timestamp buffer?
     *
     * <p>Per Trev's spec (2026-05-18): in simultaneous mode, keys do NOT need
     * to be held simultaneously — only their initial KEY_DOWN events must fall
     * within the same time window. Releases between presses are irrelevant.
     */
    public boolean isActiveSimultaneous(ChordPressBuffer buffer, long windowMs, long nowMs) {
        if (isUnbound()) return false;
        for (InputConstants.Key key : keys) {
            Long pressedAt = buffer.lastPress(key);
            if (pressedAt == null) return false;
            if ((nowMs - pressedAt) > windowMs) return false;
        }
        return true;
    }

    /**
     * Simultaneous-mode event check: same as {@link #isActiveSimultaneous} but
     * called from a key-event handler where {@code eventKey} is the key whose
     * KEY_DOWN just fired. The event key counts as pressed at {@code nowMs};
     * other chord keys must have been pressed within the window.
     */
    public boolean matchesEventSimultaneous(InputConstants.Key eventKey, ChordPressBuffer buffer,
                                             long windowMs, long nowMs) {
        if (isUnbound()) return false;
        InputConstants.Key normalized = normalizeKey(eventKey);
        if (!keys.contains(normalized)) return false;
        for (InputConstants.Key key : keys) {
            if (key.equals(normalized)) continue;
            Long pressedAt = buffer.lastPress(key);
            if (pressedAt == null) return false;
            if ((nowMs - pressedAt) > windowMs) return false;
        }
        return true;
    }

    // ── Default-mode event check (held-state aware) ──────────────────────────

    /**
     * Default-mode event check: does this chord match a KEY_DOWN event whose
     * key is part of the chord, with all OTHER chord keys currently held?
     * Used by in-screen handlers where vanilla doesn't route through the
     * {@link net.minecraft.client.KeyMapping#setDown} path.
     */
    public boolean matchesKeyEvent(InputConstants.Key eventKey, long windowHandle) {
        if (isUnbound()) return false;
        InputConstants.Key normalized = normalizeKey(eventKey);
        if (!keys.contains(normalized)) return false;
        for (InputConstants.Key key : keys) {
            if (key.equals(normalized)) continue;
            if (!isKeyHeld(windowHandle, key)) return false;
        }
        return true;
    }

    public boolean matchesMouseEvent(int mouseButton, long windowHandle) {
        if (isUnbound()) return false;
        InputConstants.Key eventKey = InputConstants.Type.MOUSE.getOrCreate(mouseButton);
        if (!keys.contains(eventKey)) return false;
        for (InputConstants.Key key : keys) {
            if (key.equals(eventKey)) continue;
            if (!isKeyHeld(windowHandle, key)) return false;
        }
        return true;
    }

    // ── Display ──────────────────────────────────────────────────────────────

    public Component getDisplayName() {
        if (isUnbound()) {
            return Component.translatable("key.keybindery.unbound");
        }
        List<InputConstants.Key> modifiers = new ArrayList<>();
        List<InputConstants.Key> regularKeys = new ArrayList<>();
        List<InputConstants.Key> mouseKeys = new ArrayList<>();
        for (InputConstants.Key key : keys) {
            if (isTraditionalModifier(key)) modifiers.add(key);
            else if (key.getType() == InputConstants.Type.MOUSE) mouseKeys.add(key);
            else regularKeys.add(key);
        }
        modifiers.sort(Comparator.comparingInt(Chord::modifierDisplayOrder));
        StringBuilder sb = new StringBuilder();
        for (InputConstants.Key mod : modifiers) {
            if (!sb.isEmpty()) sb.append("+");
            sb.append(getModifierLabel(mod));
        }
        regularKeys.sort(Comparator.comparing(k -> k.getDisplayName().getString()));
        for (InputConstants.Key key : regularKeys) {
            if (!sb.isEmpty()) sb.append("+");
            sb.append(key.getDisplayName().getString());
        }
        for (InputConstants.Key key : mouseKeys) {
            if (!sb.isEmpty()) sb.append("+");
            sb.append(key.getDisplayName().getString());
        }
        return Component.literal(sb.toString());
    }

    private static String getModifierLabel(InputConstants.Key key) {
        int code = key.getValue();
        boolean mac = Util.getPlatform() == Util.OS.OSX;
        if (code == InputConstants.KEY_LSHIFT || code == InputConstants.KEY_RSHIFT) return "Shift";
        if (code == InputConstants.KEY_LCONTROL || code == InputConstants.KEY_RCONTROL) return "Ctrl";
        if (code == InputConstants.KEY_LALT || code == InputConstants.KEY_RALT) return "Alt";
        if (code == InputConstants.KEY_LSUPER || code == InputConstants.KEY_RSUPER) return mac ? "Cmd" : "Super";
        return key.getDisplayName().getString();
    }

    private static int modifierDisplayOrder(InputConstants.Key key) {
        int code = key.getValue();
        boolean mac = Util.getPlatform() == Util.OS.OSX;
        if (mac) {
            if (code == InputConstants.KEY_LSUPER || code == InputConstants.KEY_RSUPER) return 0;
            if (code == InputConstants.KEY_LCONTROL || code == InputConstants.KEY_RCONTROL) return 1;
        } else {
            if (code == InputConstants.KEY_LCONTROL || code == InputConstants.KEY_RCONTROL) return 0;
            if (code == InputConstants.KEY_LSUPER || code == InputConstants.KEY_RSUPER) return 1;
        }
        if (code == InputConstants.KEY_LSHIFT || code == InputConstants.KEY_RSHIFT) return 2;
        if (code == InputConstants.KEY_LALT || code == InputConstants.KEY_RALT) return 3;
        return 99;
    }

    // ── Serialization ────────────────────────────────────────────────────────

    public String serialize() {
        if (isUnbound()) return "key.keyboard.unknown";
        return keys.stream()
                .map(InputConstants.Key::getName)
                .collect(Collectors.joining("+"));
    }

    public static Chord deserialize(String raw) {
        if (raw == null || raw.isEmpty()) return UNBOUND;
        try {
            String[] parts = raw.split("\\+");
            Set<InputConstants.Key> keySet = new HashSet<>();
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                InputConstants.Key key = InputConstants.getKey(trimmed);
                if (!key.equals(InputConstants.UNKNOWN)) keySet.add(key);
            }
            if (keySet.isEmpty()) return UNBOUND;
            return new Chord(keySet);
        } catch (Exception e) {
            return UNBOUND;
        }
    }

    /** Factory: single keyboard key, no modifiers. Used when wrapping a
     *  vanilla single-key mapping as a Chord for uniform comparison. */
    public static Chord ofKey(int keyCode) {
        if (keyCode == -1 || keyCode == InputConstants.UNKNOWN.getValue()) return UNBOUND;
        return new Chord(Set.of(InputConstants.Type.KEYSYM.getOrCreate(keyCode)));
    }

    /**
     * Returns the GLFW key code of the "primary" non-modifier key in this chord,
     * or the first key's value if all are modifiers/mouse, or -1 if unbound.
     * Used to determine vanilla's base-key registration in {@link IChordKeyMapping#fromChord}.
     */
    public int primaryKeyCode() {
        for (InputConstants.Key key : keys) {
            if (key.getType() == InputConstants.Type.KEYSYM && !isModifierKey(key.getValue())) {
                return key.getValue();
            }
        }
        if (!keys.isEmpty()) return keys.first().getValue();
        return -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Chord other)) return false;
        return keys.equals(other.keys);
    }

    @Override
    public int hashCode() { return keys.hashCode(); }

    @Override
    public String toString() { return "Chord{" + serialize() + "}"; }
}
