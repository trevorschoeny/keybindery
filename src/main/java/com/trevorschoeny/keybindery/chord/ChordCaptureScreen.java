package com.trevorschoeny.keybindery.chord;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.Chord;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Modal capture screen for F1's YACL chord widget. Opened when a consumer
 * mod's {@code createYACLChordOption(...)} button is clicked: shows a
 * "Press chord..." prompt and uses the shared {@link ChordCapture} engine
 * to record a multi-key chord. On finalize, invokes {@code onFinalize} with
 * the captured chord; on cancel, returns to the previous screen unchanged.
 *
 * <p>Mirrors the high-water-mark capture UX that vanilla's controls screen
 * uses via {@code KeyBindsScreenCaptureMixin}, so a player who already
 * knows the controls-screen flow doesn't have to learn anything new.
 */
public class ChordCaptureScreen extends Screen {

    private final Screen parent;
    private final Consumer<Chord> onFinalize;
    private ChordCapture capture;
    private Button cancelButton;

    public ChordCaptureScreen(Screen parent, Component title, Consumer<Chord> onFinalize) {
        super(title);
        this.parent = parent;
        this.onFinalize = onFinalize;
    }

    @Override
    protected void init() {
        super.init();
        capture = new ChordCapture(
                chord -> {
                    onFinalize.accept(chord);
                    Minecraft.getInstance().setScreen(parent);
                },
                () -> Minecraft.getInstance().setScreen(parent),
                () -> {} // onUpdate — no live-preview repaint needed; we render from capture state each frame
        );
        capture.start();
        ChordCapture.activeCapture = capture;

        cancelButton = Button.builder(
                Component.translatable("gui.cancel"),
                btn -> {
                    capture.checkGLFWFallback(Minecraft.getInstance().getWindow().handle());
                    Minecraft.getInstance().setScreen(parent);
                })
                .bounds(this.width / 2 - 60, this.height / 2 + 40, 120, 20)
                .build();
        addRenderableWidget(cancelButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        Component prompt = Component.literal("Press your chord")
                .withStyle(ChatFormatting.WHITE);
        Component preview = capture != null
                ? capture.getPreviewText()
                : Component.literal("> ... <").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC);

        graphics.drawCenteredString(this.font, prompt, this.width / 2, this.height / 2 - 30, 0xFFFFFF);
        graphics.drawCenteredString(this.font, preview, this.width / 2, this.height / 2 - 10, 0xFFFF55);
        graphics.drawCenteredString(this.font, Component.literal("Release all keys to apply"),
                this.width / 2, this.height / 2 + 15, 0xAAAAAA);

        // Per-frame GLFW polling for release detection (same pattern as
        // KeyBindsScreenCaptureMixin uses — concrete keyReleased can miss events
        // due to mixin/interface resolution, so polling is the reliable path).
        if (capture != null && capture.isCapturing()) {
            capture.pollReleases(Minecraft.getInstance().getWindow().handle());
        }
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (capture != null && capture.isCapturing()) {
            InputConstants.Key key = InputConstants.getKey(keyEvent);
            capture.onKeyPressed(key);
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean keyReleased(KeyEvent keyEvent) {
        if (capture != null && capture.isCapturing()) {
            InputConstants.Key key = InputConstants.getKey(keyEvent);
            capture.onKeyReleased(key);
            return true;
        }
        return super.keyReleased(keyEvent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Let cancel button take its click first.
        if (super.mouseClicked(event, doubleClick)) return true;
        if (capture != null && capture.isCapturing()) {
            InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(event.button());
            capture.onMousePressed(key);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (capture != null && capture.isCapturing()) {
            InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(event.button());
            capture.onMouseReleased(key);
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        // Closing without finalizing acts as cancel.
        if (capture != null && capture.isCapturing()) {
            ChordCapture.activeCapture = null;
        }
        Minecraft.getInstance().setScreen(parent);
    }
}
