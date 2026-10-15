package com.trevorschoeny.keybindery.chord;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.Chord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The vanilla key button behind {@code KeybinderyAPI.captureWidget}, for
 * runtime bindings in a mod's own screen. A plain vanilla {@link Button}, so it sits
 * in any vanilla screen or list and looks like every other button.
 *
 * <p>Same capture as Controls ({@link ChordCapture}): left-click, press the
 * chord, release to apply. Escape or Delete clears; so does a right-click
 * when not capturing. Mouse buttons clicked on the button join the chord.
 *
 * <p>Keys go through {@link #routeKey}, called by
 * {@code ScreenChordCaptureMixin} at the top of {@code Screen.keyPressed}:
 * the screen would otherwise close on Escape before the button saw it, and
 * a button inside a list only gets keys while focused. Capture belongs to
 * the screen it started in; a key arriving under any other screen drops it.
 */
public final class ChordCaptureWidget extends Button.Plain {

    /** The button capturing right now, if any. One at a time, as in Controls. */
    private static @Nullable ChordCaptureWidget capturing;

    private final Consumer<Chord> onChange;
    private Chord chord;
    private @Nullable ChordCapture capture;
    private @Nullable Screen owner;

    public ChordCaptureWidget(int x, int y, int width, int height, Chord chord, Consumer<Chord> onChange) {
        super(x, y, width, height, Component.empty(), b -> {}, DEFAULT_NARRATION);
        this.chord = chord == null ? Chord.UNBOUND : chord;
        this.onChange = onChange;
    }

    /** Called with every key the screen receives; true when the capture took it. */
    public static boolean routeKey(Screen screen, KeyEvent event) {
        ChordCaptureWidget w = capturing;
        if (w == null || w.capture == null) return false;
        if (w.owner != screen) { w.stop(); return false; }
        w.capture.onKeyPressed(InputConstants.Type.KEYBOARD.getOrCreate(event.key()));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !isMouseOver(event.x(), event.y())) return false;
        if (capture != null) {
            capture.onMousePressed(InputConstants.Type.MOUSE.getOrCreate(event.button()));
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            apply(Chord.UNBOUND);
            return true;
        }
        return super.mouseClicked(event, doubleClick); // left: onPress below, with the click sound
    }

    @Override
    public void onPress(net.minecraft.client.input.InputWithModifiers input) {
        if (capturing != null) capturing.stop();
        capture = new ChordCapture(this::apply, () -> apply(Chord.UNBOUND), () -> {});
        capture.start();
        owner = Minecraft.getInstance().gui.screen();
        capturing = this;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Releases arrive as no event a button sees; poll them, as ChordButton does.
        if (capture != null) capture.pollReleases();
        setMessage(capture != null ? capture.getPreviewText()
                : chord.isUnbound() ? Component.literal(">> click to bind <<")
                : chord.getDisplayName());
        super.extractContents(graphics, mouseX, mouseY, partialTick);
    }

    private void apply(Chord next) {
        stop();
        chord = next;
        onChange.accept(next);
    }

    private void stop() {
        capture = null;
        owner = null;
        if (capturing == this) capturing = null;
    }
}
