package com.trevorschoeny.keybindery.chord;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.Chord;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Shared, callback-driven capture engine for multi-key chord recording.
 * Consumer-built capture UIs and Keybindery's controls-screen capture mixin
 * delegate to an instance of this class during binding sessions.
 *
 * <h3>High Water Mark pattern</h3>
 * <ul>
 *   <li>{@code heldKeys} — keys physically held RIGHT NOW</li>
 *   <li>{@code highWaterMark} — largest set of simultaneously-held keys
 *       seen during this capture session</li>
 * </ul>
 * Keys accumulate in {@code heldKeys}; whenever it grows past the previous
 * mark, the mark advances (capped at {@link Chord#MAX_CHORD}). When all keys
 * release and the mark is non-empty, the binding finalizes via {@code onFinalize}.
 *
 * <h3>Escape and Delete/Backspace</h3>
 * <ul>
 *   <li>Escape ALWAYS cancels capture (via {@code onCancel}).</li>
 *   <li>Delete/Backspace clears the binding to UNBOUND (via {@code onClear}).</li>
 * </ul>
 *
 * <p>Migrated verbatim from MenuKit's {@code MKKeybindCapture} (2026-05-18),
 * with type renames only.
 */
public class ChordCapture {

    private static final long GLFW_FALLBACK_TIMEOUT_MS = 2000;

    /** The KeyMapping currently being captured, or null. Set by the controls-
     *  screen mixin so the {@link com.trevorschoeny.keybindery.mixin.KeyMappingChordMixin}
     *  can render a live preview in {@code getTranslatedKeyMessage}. */
    public static KeyMapping activeMapping;

    /** The active capture engine, or null. */
    public static ChordCapture activeCapture;

    private final Set<InputConstants.Key> heldKeys = new LinkedHashSet<>();
    private final Set<InputConstants.Key> highWaterMark = new LinkedHashSet<>();
    private boolean capturing = false;
    private long lastEventTime = 0;

    private final Consumer<Chord> onFinalize;
    private final Runnable onCancel;
    private final Runnable onUpdate;
    private final Runnable onClear;

    public ChordCapture(Consumer<Chord> onFinalize, Runnable onCancel,
                        Runnable onUpdate, Runnable onClear) {
        this.onFinalize = onFinalize;
        this.onCancel = onCancel;
        this.onUpdate = onUpdate;
        this.onClear = onClear;
    }

    public ChordCapture(Consumer<Chord> onFinalize, Runnable onCancel, Runnable onUpdate) {
        this(onFinalize, onCancel, onUpdate, null);
    }

    public void start() {
        capturing = true;
        heldKeys.clear();
        highWaterMark.clear();
        lastEventTime = System.currentTimeMillis();
    }

    public boolean isCapturing() { return capturing; }

    public Set<InputConstants.Key> getHighWaterMark() { return Set.copyOf(highWaterMark); }

    public Component getPreviewText() {
        if (highWaterMark.isEmpty()) {
            return Component.literal("> ... <").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC);
        }
        Chord partial = new Chord(highWaterMark);
        return Component.literal("> " + partial.getDisplayName().getString() + " <")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC);
    }

    public boolean onKeyPressed(InputConstants.Key key) {
        if (!capturing) return false;
        int keyCode = key.getValue();
        if (keyCode == InputConstants.KEY_ESCAPE) { cancelCapture(); return true; }
        if (keyCode == InputConstants.KEY_DELETE || keyCode == InputConstants.KEY_BACKSPACE) {
            clearCapture(); return true;
        }
        heldKeys.add(key);
        lastEventTime = System.currentTimeMillis();
        updateHighWaterMark();
        return true;
    }

    public boolean onKeyReleased(InputConstants.Key key) {
        if (!capturing) return false;
        heldKeys.remove(key);
        lastEventTime = System.currentTimeMillis();
        if (heldKeys.isEmpty() && !highWaterMark.isEmpty()) finalizeCapture();
        return true;
    }

    public boolean onMousePressed(InputConstants.Key mouseKey) {
        if (!capturing) return false;
        heldKeys.add(mouseKey);
        lastEventTime = System.currentTimeMillis();
        updateHighWaterMark();
        return true;
    }

    public boolean onMouseReleased(InputConstants.Key mouseKey) {
        if (!capturing) return false;
        heldKeys.remove(mouseKey);
        lastEventTime = System.currentTimeMillis();
        if (heldKeys.isEmpty() && !highWaterMark.isEmpty()) finalizeCapture();
        return true;
    }

    /** Render-loop safety: if no events arrive for 2+ seconds, poll GLFW
     *  directly. Catches alt-tab / focus-loss where release events get dropped. */
    public void checkGLFWFallback(long windowHandle) {
        if (!capturing || highWaterMark.isEmpty()) return;
        long now = System.currentTimeMillis();
        if ((now - lastEventTime) < GLFW_FALLBACK_TIMEOUT_MS) return;
        boolean anyHeld = false;
        for (InputConstants.Key key : highWaterMark) {
            if (isKeyHeld(windowHandle, key)) { anyHeld = true; break; }
        }
        if (!anyHeld) { heldKeys.clear(); finalizeCapture(); }
    }

    /** Per-frame release polling — for vanilla KeyBindsScreen where concrete
     *  mixin methods can't reliably receive release events. */
    public void pollReleases(long windowHandle) {
        if (!capturing || heldKeys.isEmpty()) return;
        heldKeys.removeIf(key -> !isKeyHeld(windowHandle, key));
        if (heldKeys.isEmpty() && !highWaterMark.isEmpty()) finalizeCapture();
    }

    private void updateHighWaterMark() {
        int effectiveSize = Math.min(heldKeys.size(), Chord.MAX_CHORD);
        if (effectiveSize > highWaterMark.size()) {
            highWaterMark.clear();
            int count = 0;
            for (InputConstants.Key key : heldKeys) {
                if (count >= Chord.MAX_CHORD) break;
                highWaterMark.add(key);
                count++;
            }
            onUpdate.run();
        }
    }

    private void finalizeCapture() {
        Chord newChord = new Chord(highWaterMark);
        resetState();
        onFinalize.accept(newChord);
    }

    private void cancelCapture() { resetState(); onCancel.run(); }

    private void clearCapture() {
        resetState();
        if (onClear != null) onClear.run();
        else onCancel.run();
    }

    private void resetState() {
        capturing = false;
        heldKeys.clear();
        highWaterMark.clear();
        lastEventTime = 0;
        if (activeCapture == this) { activeMapping = null; activeCapture = null; }
    }

    private static boolean isKeyHeld(long windowHandle, InputConstants.Key key) {
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(windowHandle, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(windowHandle, key.getValue()) == GLFW.GLFW_PRESS;
    }
}
