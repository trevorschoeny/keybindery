package com.trevorschoeny.keybindery.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.Chord;
import com.trevorschoeny.keybindery.chord.ChordCapture;
import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.InputContext;
import com.trevlar.menukit.api.element.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The toolbar's "Search Keybind" chord-capture button. Click to start
 * capturing, press a chord, release to finish. Right-click clears.
 *
 * <p>Uses {@link ChordCapture} and {@code KeyBindsScreenCaptureMixin}'s key
 * routing: starting a capture sets {@code ChordCapture.activeCapture}, and the
 * mixin's HEAD injections on {@code KeyBindsScreen.keyPressed} and
 * {@code mouseClicked} forward every later event to that capture.
 */
public class SearchKeybindButton extends AbstractPanelElement {

    private final Supplier<Chord> chordGetter;
    private final Consumer<Chord> chordSetter;
    private final Component placeholder;
    /** Shown on hover (not while capturing); MenuKit 6 elements take it at construction. */
    private final Component tooltip;

    private ChordCapture capture;

    public SearchKeybindButton(int childX, int childY, int width, int height,
                                Supplier<Chord> chordGetter,
                                Consumer<Chord> chordSetter,
                                Component placeholder,
                                Component tooltip) {
        // A MenuKit 6 subclass without a builder: set the base's geometry directly.
        this.childX = childX;
        this.childY = childY;
        this.width = this.authoredWidth = width;
        this.height = height;
        this.tooltip = tooltip;
        this.chordGetter = chordGetter;
        this.chordSetter = chordSetter;
        this.placeholder = placeholder;
    }

    private boolean isCapturing() { return capture != null && capture.isCapturing(); }

    /**
     * Vanilla button sprites, to match the Sort and Filter dropdowns, which
     * always draw vanilla here because Controls is an options screen.
     * Capturing uses {@code widget/button_disabled} (vanilla's darker flat
     * gray) to show the button is busy and can't be clicked.
     */
    private static final Identifier SPRITE_NORMAL =
            Identifier.withDefaultNamespace("widget/button");
    private static final Identifier SPRITE_HIGHLIGHTED =
            Identifier.withDefaultNamespace("widget/button_highlighted");
    private static final Identifier SPRITE_CAPTURING =
            Identifier.withDefaultNamespace("widget/button_disabled");

    @Override
    public void render(RenderContext ctx) {
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;
        boolean hovered = isHovered(ctx);

        Identifier sprite;
        if (isCapturing()) sprite = SPRITE_CAPTURING;
        else if (hovered) sprite = SPRITE_HIGHLIGHTED;
        else sprite = SPRITE_NORMAL;
        ctx.graphics().blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
                sx, sy, width, height);

        Component label = labelFor();
        var font = Minecraft.getInstance().font;
        int textWidth = font.width(label);
        int textX = sx + Math.max(2, (width - textWidth) / 2);
        int textY = sy + (height - font.lineHeight) / 2;
        ctx.graphics().text(font, label, textX, textY, 0xFFFFFFFF, true);

        // The hover tooltip shows the right-click-to-clear hint, which players
        // would otherwise never find. Skipped while capturing, when the preview
        // text is the feedback.
        if (hovered && !isCapturing()) queueTooltip(ctx);
    }

    @Override
    public Supplier<Component> tooltipSupplier() {
        return () -> tooltip;
    }

    private Component labelFor() {
        if (isCapturing()) return capture.getPreviewText();
        Chord current = chordGetter.get();
        if (current == null || current.isUnbound()) return placeholder;
        return current.getDisplayName();
    }

    @Override
    public boolean mouseClicked(InputContext in, int button) {
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            if (isCapturing()) stopCapture();
            chordSetter.accept(Chord.UNBOUND);
            return true;
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            if (isCapturing()) {
                capture.onMousePressed(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_LEFT));
            } else {
                startCapture();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(InputContext in, int button) {
        if (isCapturing()) {
            capture.onMouseReleased(InputConstants.Type.MOUSE.getOrCreate(button));
            return true;
        }
        return false;
    }

    private void startCapture() {
        // Escape, Delete and Backspace during a search capture all clear the
        // chord: in a search field, aborting means dropping the filter, not
        // keeping the last value. Binding a row is different; there onCancel
        // keeps the previous binding so a misclick doesn't wipe it.
        Runnable clearAndStop = () -> {
            chordSetter.accept(Chord.UNBOUND);
            stopCapture();
        };
        capture = new ChordCapture(
                chord -> { chordSetter.accept(chord); stopCapture(); },
                clearAndStop,
                () -> {},
                clearAndStop
        );
        capture.start();
        // No activeMapping: that drives the live preview when binding a row, and
        // the toolbar search doesn't preview into a KeyMapping.
        ChordCapture.activeCapture = capture;
    }

    private void stopCapture() {
        if (ChordCapture.activeCapture == capture) ChordCapture.activeCapture = null;
        capture = null;
    }
}
