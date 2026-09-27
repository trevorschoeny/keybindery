package com.trevorschoeny.keybindery.chord;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.Chord;
import com.trevorschoeny.keybindery.api.KeybinderyAPI;
import com.trevorschoeny.keybindery.screen.KeybinderyKeyBindsScreen;
import com.trevlar.menukit.core.AbstractPanelElement;
import com.trevlar.menukit.core.Button;
import com.trevlar.menukit.core.MKText;
import com.trevlar.menukit.core.RenderContext;
import com.trevlar.menukit.core.TextLabel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * MenuKit sibling of {@link ChordControllerWidget}: the in-config key button
 * for a consumer's MenuKit screen. Public API for mods that require Keybindery
 * (it lives in main, not the api jar, so the api jar stays MenuKit-free).
 *
 * <pre>{@code
 * new ChordButton(SORT_KEY).label(Component.literal("Sort"))
 * }</pre>
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
 * <p>Capture: left-click starts it, press the chord, release to apply,
 * Escape cancels, Delete/Backspace unbinds, right-click unbinds. Applied chords
 * save straight away through {@link KeybinderyAPI#setChord} (MenuKit has no
 * pending/apply step). Constructing the element claims the mapping, like
 * {@code createYACLChordOption} does.
 */
public class ChordButton extends AbstractPanelElement<ChordButton> {

    @Override protected ChordButton self() { return this; }

    private static final int FACE_W = 108;
    private static final int ICON = 16;
    private static final int H = 16;
    /** Between face and icons, and between the two icons. */
    private static final int GAP = 2;
    /** Between the optional label and the face. */
    private static final int LABEL_GAP = 4;

    private final KeyMapping mapping;
    private final Face face;
    private final Button conflicts;
    private final Button reset;
    private @Nullable TextLabel label;

    private @Nullable ChordCapture capture;

    public ChordButton(KeyMapping mapping) {
        this.mapping = mapping;
        KeybinderyAPI.getInstance().markClaimed(mapping);
        this.face = new Face();
        this.conflicts = new Button(0, 0, ICON, ICON, Component.literal("⚠"),
                b -> KeybinderyKeyBindsScreen.openWithConflictsFilterFor(
                        mapping, Minecraft.getInstance().gui.screen()),
                () -> !ChordConflicts.hasAnyConflict(mapping))
                .tooltip(Component.translatable("keybindery.tooltip.show_conflicts"));
        this.reset = new Button(0, 0, ICON, ICON, Component.literal("↻"),
                b -> KeybinderyAPI.getInstance().setChord(mapping, IChordKeyMapping.defaultChord(mapping)),
                () -> Objects.equals(IChordKeyMapping.getChord(mapping), IChordKeyMapping.defaultChord(mapping)))
                .tooltip(Component.translatable("keybindery.tooltip.reset_to_default"));
    }

    /** Optional text drawn left of the key, in MenuKit's default label colour. */
    public ChordButton label(Component text) {
        this.label = new TextLabel(0, 0, text);
        return this;
    }

    // ── Geometry: children are positioned relative to this element's origin ──

    private int labelW() {
        return label == null ? 0 : label.getWidth() + LABEL_GAP;
    }

    @Override public int getWidth() { return labelW() + FACE_W + GAP + ICON + GAP + ICON; }
    @Override public int getHeight() { return H; }
    @Override public boolean isInteractive() { return true; }

    /** Re-anchor the children on this element's current position (Flow may move it). */
    private void place() {
        int x = childX;
        if (label != null) {
            label.at(x, childY + (H - Minecraft.getInstance().font.lineHeight) / 2 + 1);
            x += labelW();
        }
        face.at(x, childY);
        x += FACE_W + GAP;
        conflicts.at(x, childY);
        reset.at(x + ICON + GAP, childY);
    }

    @Override
    public void render(RenderContext ctx) {
        place();
        if (label != null) label.render(ctx);
        face.render(ctx);
        conflicts.render(ctx);
        reset.render(ctx);
        // Releases don't reach panel elements as key events; poll GLFW each
        // frame, exactly as the YACL widget does.
        if (capture != null) capture.pollReleases(Minecraft.getInstance().getWindow().handle());
    }

    // ── Input: children gate on their own per-frame hover state ──────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return face.mouseClicked(mouseX, mouseY, button)
                || conflicts.mouseClicked(mouseX, mouseY, button)
                || reset.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        face.mouseReleased(mouseX, mouseY, button);
        conflicts.mouseReleased(mouseX, mouseY, button);
        reset.mouseReleased(mouseX, mouseY, button);
        if (capture != null) {
            capture.onMouseReleased(InputConstants.Type.MOUSE.getOrCreate(button));
            return true;
        }
        return false;
    }

    /** Keys are offered un-hit-tested to every element; only claim them mid-capture. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (capture == null) return false;
        capture.onKeyPressed(keyCode == InputConstants.UNKNOWN.getValue()
                ? InputConstants.Type.SCANCODE.getOrCreate(scanCode)
                : InputConstants.Type.KEYSYM.getOrCreate(keyCode));
        return true;
    }

    /** Screen closing or rebuilding mid-capture: drop it so the static
     *  {@code activeCapture} can't hijack the next screen's keys. */
    @Override
    public void onDetach(Screen screen) {
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
            super(0, 0, FACE_W, H, Component.empty(), b -> {});
        }

        @Override
        protected void renderContent(RenderContext ctx, int sx, int sy) {
            MKText.renderCentered(ctx.graphics(), valueText(), sx, sy, FACE_W, H, 0xFFFFFFFF, true);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isHovered()) return false;
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
            super.mouseClicked(mouseX, mouseY, button); // press affordance
            startCapture();
            return true;
        }
    }
}
