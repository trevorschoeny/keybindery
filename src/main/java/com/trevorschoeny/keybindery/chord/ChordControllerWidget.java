package com.trevorschoeny.keybindery.chord;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.Chord;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.ControllerWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Inline keybind widget for F1 — mimics vanilla's keybind button. Click to
 * enter capture mode (text changes to {@code > ... <} in yellow italic);
 * press chord keys; release to finalize and apply via the ChordCapture
 * engine. No modal screen.
 *
 * <p>Matches the high-water-mark capture pattern that vanilla's controls
 * screen uses via {@code KeyBindsScreenCaptureMixin}, so the UX is
 * consistent everywhere chord-binding happens.
 */
public class ChordControllerWidget extends ControllerWidget<ChordController> {

    private boolean capturing = false;
    private ChordCapture capture;

    public ChordControllerWidget(ChordController control, YACLScreen screen, Dimension<Integer> dim) {
        super(control, screen, dim);
    }

    @Override
    protected Component getValueText() {
        if (capturing && capture != null) {
            return capture.getPreviewText();
        }
        return control.formatValue();
    }

    @Override
    protected int getHoveredControlWidth() {
        return getUnhoveredControlWidth();
    }

    // ── Click → enter / cancel capture ───────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver((double) event.x(), (double) event.y())) return false;
        if (capturing) {
            // Click while capturing → also a chord input (mouse buttons are
            // valid chord components). Route to capture engine, same as
            // KeyBindsScreenCaptureMixin's mouseClicked path.
            InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(event.button());
            capture.onMousePressed(key);
            return true;
        }
        startCapture();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (capturing && capture != null) {
            InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(event.button());
            capture.onMouseReleased(key);
            return true;
        }
        return super.mouseReleased(event);
    }

    // ── Key press / release routes to ChordCapture ───────────────────────────

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (!capturing || capture == null) return super.keyPressed(keyEvent);
        InputConstants.Key key = InputConstants.getKey(keyEvent);
        capture.onKeyPressed(key);
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent keyEvent) {
        if (!capturing || capture == null) return super.keyReleased(keyEvent);
        InputConstants.Key key = InputConstants.getKey(keyEvent);
        capture.onKeyReleased(key);
        return true;
    }

    // ── Capture engine wiring ────────────────────────────────────────────────

    private void startCapture() {
        capture = new ChordCapture(
                chord -> {
                    // Apply the captured chord to the option's pending value;
                    // YACL's "Save" button (or auto-save on close) commits it
                    // via the option's setter → KeybinderyAPI.setChord →
                    // IChordKeyMapping.updateFromChord + options.txt save.
                    control.option().requestSet(chord);
                    stopCapture();
                },
                () -> stopCapture(),                  // onCancel
                () -> {},                              // onUpdate — render() reads fresh each frame
                () -> {                                // onClear (Delete/Backspace)
                    control.option().requestSet(Chord.UNBOUND);
                    stopCapture();
                }
        );
        capture.start();
        ChordCapture.activeCapture = capture;
        ChordCapture.activeMapping = control.mapping();
        capturing = true;
    }

    private void stopCapture() {
        capturing = false;
        capture = null;
        if (ChordCapture.activeCapture == null || ChordCapture.activeMapping == control.mapping()) {
            ChordCapture.activeMapping = null;
        }
    }

    // ── Render: per-frame GLFW poll for releases (vanilla pattern) ──────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        if (capturing && capture != null) {
            capture.pollReleases(Minecraft.getInstance().getWindow().handle());
        }
    }

}
