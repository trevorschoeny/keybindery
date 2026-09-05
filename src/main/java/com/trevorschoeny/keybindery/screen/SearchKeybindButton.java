package com.trevorschoeny.keybindery.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.Chord;
import com.trevorschoeny.keybindery.chord.ChordCapture;
import com.trevlar.menukit.core.AbstractPanelElement;
import com.trevlar.menukit.core.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * F4 toolbar "Search Keybind" — chord-capture button. Click to enter
 * capture mode; press a chord; release to finalize. Right-click clears.
 *
 * <p>Reuses {@link ChordCapture} + {@code KeyBindsScreenCaptureMixin}'s
 * key-routing path: when the button starts capture it sets
 * {@code ChordCapture.activeCapture}; the mixin's HEAD-injects on
 * {@code KeyBindsScreen.keyPressed/mouseClicked} forward all subsequent
 * events to the active capture engine.
 */
public class SearchKeybindButton extends AbstractPanelElement<SearchKeybindButton> {

    /** MK 2.0.0 self-typed-generic contract — chainable base setters
     *  return the concrete subtype. */
    @Override protected SearchKeybindButton self() { return this; }

    private final int childX;
    private final int childY;
    private final int width;
    private final int height;
    private final Supplier<Chord> chordGetter;
    private final Consumer<Chord> chordSetter;
    private final Component placeholder;

    private boolean hovered = false;
    private ChordCapture capture;

    public SearchKeybindButton(int childX, int childY, int width, int height,
                                Supplier<Chord> chordGetter,
                                Consumer<Chord> chordSetter,
                                Component placeholder) {
        this.childX = childX;
        this.childY = childY;
        this.width = width;
        this.height = height;
        this.chordGetter = chordGetter;
        this.chordSetter = chordSetter;
        this.placeholder = placeholder;
    }

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }

    private boolean isCapturing() { return capture != null && capture.isCapturing(); }

    /** Vanilla button sprite atlas — matches the look of MK widgets that
     *  opt into {@code ControlStyle.VANILLA} (the Sort/Filter dropdowns).
     *  Capturing uses {@code widget/button_disabled} (vanilla's darker
     *  uniform gray) to visually distinguish active-but-not-clickable. */
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
        hovered = isHovered(ctx);

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

        // Hover-triggered tooltip — surfaces the right-click-to-clear hint
        // (otherwise non-discoverable). Skip while capturing — the preview
        // text is the user's feedback signal there.
        @Nullable Supplier<Component> tt = getTooltipSupplier();
        if (hovered && !isCapturing() && tt != null && ctx.hasMouseInput()) {
            Component ttText = tt.get();
            if (ttText != null) {
                ctx.graphics().setTooltipForNextFrame(
                        font, ttText, ctx.mouseX(), ctx.mouseY());
            }
        }
    }

    private Component labelFor() {
        if (isCapturing()) return capture.getPreviewText();
        Chord current = chordGetter.get();
        if (current == null || current.isUnbound()) return placeholder;
        return current.getDisplayName();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            if (isCapturing()) stopCapture();
            chordSetter.accept(Chord.UNBOUND);
            return true;
        }
        if (button == 0) {
            if (isCapturing()) {
                capture.onMousePressed(InputConstants.Type.MOUSE.getOrCreate(0));
            } else {
                startCapture();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isCapturing()) {
            capture.onMouseReleased(InputConstants.Type.MOUSE.getOrCreate(button));
            return true;
        }
        return false;
    }

    private void startCapture() {
        // ESC and Delete/Backspace during a search-keybind capture both
        // CLEAR the chord — the user pressed the abort key, the natural
        // intent for a search field is "drop this filter," not "keep the
        // last value." Different from row-bind semantics (row-bind onCancel
        // preserves the previous binding so a misclick doesn't wipe it).
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
        // No activeMapping — that's the row-bind flow's live-preview signal;
        // toolbar search capture doesn't preview into a KeyMapping.
        ChordCapture.activeCapture = capture;
    }

    private void stopCapture() {
        if (ChordCapture.activeCapture == capture) ChordCapture.activeCapture = null;
        capture = null;
    }
}
