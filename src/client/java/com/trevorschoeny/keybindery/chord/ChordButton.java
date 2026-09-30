package com.trevorschoeny.keybindery.chord;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.Chord;
import com.trevorschoeny.keybindery.api.KeybinderyAPI;
import com.trevorschoeny.keybindery.screen.KeybinderyKeyBindsScreen;
import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.ChildDispatch;
import com.trevlar.menukit.api.element.ElementConstants;
import com.trevlar.menukit.api.element.InputContext;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.element.Text;
import com.trevlar.menukit.api.element.TextLabel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * MenuKit sibling of {@link ChordControllerWidget}: the in-config key button
 * for a consumer's MenuKit screen. Public API for mods that require Keybindery
 * (it lives in main, not the api jar, so the api jar stays MenuKit-free).
 *
 * <pre>{@code
 * ChordButton.builder(SORT_KEY)
 *         .label(Component.literal("Sort"))
 *         .disabledWhen(() -> !sortEnabled)
 *         .at(0, y)
 *         .build()
 * }</pre>
 *
 * <p>Built like every MenuKit 6 element: the builder carries MenuKit's shared
 * vocabulary ({@code at}, {@code disabledWhen}, {@code visibleWhen},
 * {@code tooltip}, ...) plus {@link Builder#label}.
 *
 * <p>Layout, one row, 16px tall: {@code [label] [ chord ][⚠][↻]}. All three
 * buttons are plain MenuKit {@link Button}s in the default
 * {@code ControlStyle.MK}. They live in one element so a wrapping
 * {@code Flow} can never split them across lines.
 *
 * <ul>
 *   <li><b>Key face</b>: fixed {@value #FACE_W}px so the row doesn't jump
 *       while capturing; a longer chord scrolls inside. Text is exactly
 *       {@code ChordControllerWidget.getValueText}: chord / click-to-bind /
 *       live preview / yellow brackets on conflict.</li>
 *   <li><b>⚠</b>: opens the Controls screen filtered to this key's conflicts;
 *       greyed out when there are none.</li>
 *   <li><b>↻</b>: back to the mapping's default; greyed out when already there.</li>
 * </ul>
 *
 * <p>Disabled ({@code disabledWhen}, or a disabled panel around it): the whole
 * row greys and takes no input. The children don't each ask; the row hands
 * them a disabled render/input context, which MenuKit elements honour.
 *
 * <p>Capture: left-click starts it, press the chord, release to apply,
 * Escape cancels, Delete/Backspace unbinds, right-click unbinds. Applied chords
 * save straight away through {@link KeybinderyAPI#setChord} (MenuKit has no
 * pending/apply step). Building the element claims the mapping, like
 * {@code createYACLChordOption} does.
 */
public class ChordButton extends AbstractPanelElement {

    private static final int FACE_W = 108;
    private static final int ICON = 16;
    private static final int H = 16;
    /** Between face and icons, and between the two icons. */
    private static final int GAP = 2;
    /** Between the optional label and the face. */
    private static final int LABEL_GAP = 4;

    private final KeyMapping mapping;
    private final @Nullable TextLabel label;
    private final Face face;
    private final Button conflicts;
    private final Button reset;
    /** Label (if any), face, ⚠, ↻: the order ChildDispatch renders and routes. */
    private final List<PanelElement> children;

    private @Nullable ChordCapture capture;

    public static Builder builder(KeyMapping mapping) {
        return new Builder(mapping);
    }

    private ChordButton(Builder b) {
        super(b);
        this.mapping = b.mapping;
        KeybinderyAPI.getInstance().markClaimed(mapping);
        // Plain label colour; the disabled grey comes from the context.
        this.label = b.label == null ? null : TextLabel.builder().text(b.label).build();
        this.face = new Face();
        this.conflicts = Button.builder().size(ICON, ICON).label(Component.literal("⚠"))
                .onClick(() -> KeybinderyKeyBindsScreen.openWithConflictsFilterFor(
                        mapping, Minecraft.getInstance().gui.screen()))
                .disabledWhen(() -> !ChordConflicts.hasAnyConflict(mapping))
                .tooltip(Component.translatable("keybindery.tooltip.show_conflicts"))
                .build();
        this.reset = Button.builder().size(ICON, ICON).label(Component.literal("↻"))
                .onClick(() -> KeybinderyAPI.getInstance().setChord(mapping, IChordKeyMapping.defaultChord(mapping)))
                .disabledWhen(() -> Objects.equals(IChordKeyMapping.getChord(mapping), IChordKeyMapping.defaultChord(mapping)))
                .tooltip(Component.translatable("keybindery.tooltip.reset_to_default"))
                .build();
        this.children = label == null
                ? List.of(face, conflicts, reset)
                : List.of(label, face, conflicts, reset);
    }

    // ── Geometry: children are positioned relative to this element's origin ──

    private int labelW() {
        return label == null ? 0 : label.getWidth() + LABEL_GAP;
    }

    @Override public int getWidth() { return labelW() + FACE_W + GAP + ICON + GAP + ICON; }
    @Override public int getHeight() { return H; }
    // Fixed-size row: a Flow measures it as is and never stretches or squeezes it.
    @Override public int naturalWidth() { return getWidth(); }
    @Override public void layoutWithin(int budget) {}
    @Override public void fillWidth(int width) {}
    @Override public boolean isInteractive() { return true; }

    /** Re-anchor the children on this element's current position (Flow may move it). */
    private void place() {
        int x = childX;
        if (label != null) {
            label.setChildPosition(x, childY + (H - Minecraft.getInstance().font.lineHeight) / 2 + 1);
            x += labelW();
        }
        face.setChildPosition(x, childY);
        x += FACE_W + GAP;
        conflicts.setChildPosition(x, childY);
        reset.setChildPosition(x + ICON + GAP, childY);
    }

    @Override
    public void render(RenderContext ctx) {
        place();
        // Disabled mid-capture (e.g. the owning tab just got toggled off):
        // drop it so the shared static capture state stops eating keys.
        if (capture != null && disabled(ctx)) stopCapture();
        ChildDispatch.render(children, ctx.disabledIf(ownDisabled()));
        // Releases don't reach panel elements as key events; poll GLFW each
        // frame, exactly as the YACL widget does.
        if (capture != null) capture.pollReleases(Minecraft.getInstance().getWindow().handle());
    }

    // ── Input: ChildDispatch hit-tests each child and skips all when disabled ──

    @Override
    public boolean mouseClicked(InputContext in, int button) {
        return ChildDispatch.mouseClicked(children, in.disabledIf(ownDisabled()), button);
    }

    @Override
    public boolean mouseReleased(InputContext in, int button) {
        ChildDispatch.mouseReleased(children, in, button);
        if (capture != null) {
            capture.onMouseReleased(InputConstants.Type.MOUSE.getOrCreate(button));
            return true;
        }
        return false;
    }

    /** Keys are offered un-hit-tested to every element; only claim them mid-capture. */
    @Override
    public boolean keyPressed(InputContext in, int keyCode, int scanCode, int modifiers) {
        if (capture == null) return false;
        capture.onKeyPressed(keyCode == InputConstants.UNKNOWN.getValue()
                ? InputConstants.Type.SCANCODE.getOrCreate(scanCode)
                : InputConstants.Type.KEYSYM.getOrCreate(keyCode));
        return true;
    }

    /** The buttons register their focus/narration stand-ins with the screen. */
    @Override
    public void onAttach(Screen screen) {
        ChildDispatch.attach(children, screen);
    }

    /** Screen closing or rebuilding mid-capture: drop it so the static
     *  {@code activeCapture} can't hijack the next screen's keys. */
    @Override
    public void onDetach(Screen screen) {
        ChildDispatch.detach(children, screen);
        stopCapture();
    }

    // ── Capture lifecycle (mirrors ChordControllerWidget) ────────────────

    private void startCapture() {
        capture = new ChordCapture(
                chord -> { KeybinderyAPI.getInstance().setChord(mapping, chord); stopCapture(); },
                this::stopCapture,
                () -> {},
                () -> { KeybinderyAPI.getInstance().setChord(mapping, Chord.UNBOUND); stopCapture(); });
        capture.start();
        ChordCapture.activeCapture = capture;
        ChordCapture.activeMapping = mapping;
    }

    private void stopCapture() {
        if (ChordCapture.activeCapture == capture && capture != null) {
            ChordCapture.activeCapture = null;
            ChordCapture.activeMapping = null;
        }
        capture = null;
    }

    /** Same text as {@code ChordControllerWidget.getValueText}. */
    private Component valueText() {
        if (capture != null) return capture.getPreviewText();
        Chord chord = IChordKeyMapping.getChord(mapping);
        Component shown = (chord == null || chord.isUnbound())
                ? Component.literal(">> click to bind <<")
                : chord.getDisplayName();
        if (ChordConflicts.hasAnyConflict(mapping)) {
            return Component.literal("[ ")
                    .append(shown.copy().withStyle(ChatFormatting.WHITE))
                    .append(" ]")
                    .withStyle(ChatFormatting.YELLOW);
        }
        return shown;
    }

    /** The key face: a stock MK Button whose content is the live chord text. */
    private final class Face extends Button {
        Face() {
            super(Button.builder().size(FACE_W, H));
        }

        @Override
        protected void renderContent(RenderContext ctx, int sx, int sy, Look look) {
            int color = look.disabled() ? ElementConstants.TEXT_DISABLED : ElementConstants.TEXT_LIGHT;
            Text.renderCentered(ctx.graphics(), valueText(), sx, sy, FACE_W, H, color, true);
        }

        /** Reached only when hovered and enabled (ChildDispatch hit-tests first). */
        @Override
        public boolean mouseClicked(InputContext in, int button) {
            if (disabled(in)) return false;
            if (capture != null) {
                // Mouse buttons are valid chord keys mid-capture.
                capture.onMousePressed(InputConstants.Type.MOUSE.getOrCreate(button));
                return true;
            }
            if (button == 1) {
                KeybinderyAPI.getInstance().setChord(mapping, Chord.UNBOUND);
                return true;
            }
            if (button != 0) return false;
            super.mouseClicked(in, button); // press affordance + click sound
            startCapture();
            return true;
        }
    }

    public static final class Builder extends AbstractPanelElement.Builder<ChordButton, Builder> {
        private final KeyMapping mapping;
        private @Nullable Component label;

        private Builder(KeyMapping mapping) {
            this.mapping = Objects.requireNonNull(mapping, "mapping");
        }

        @Override protected Builder self() { return this; }

        /** Optional text drawn left of the key, in MenuKit's default label colour. */
        public Builder label(Component text) {
            this.label = Objects.requireNonNull(text, "text");
            return this;
        }

        @Override
        public ChordButton build() {
            return new ChordButton(this);
        }
    }
}
