package com.trevorschoeny.keybindery.chord;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.Chord;
import com.trevorschoeny.keybindery.screen.KeybinderyKeyBindsScreen;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.ControllerWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * One-row YACL widget for chord rebinding — replicates YACL's base
 * {@code ControllerWidget.render} pattern to draw a single button-rect
 * spanning the whole row with the option name on the LEFT and
 * the chord value on the RIGHT (vanilla-style click-to-rebind). Two
 * text-symbol icon buttons overlay the far right:
 *
 * <ul>
 *   <li><b>⚠ Conflicts</b>: opens the controls screen filtered to the
 *       chord's conflict set. Grayed out when no conflicts exist.</li>
 *   <li><b>↻ Reset</b>: restores the option's default. Grayed out when
 *       the option is already at default. Uses YACL's own reset glyph
 *       (U+21BB CLOCKWISE OPEN CIRCLE ARROW).</li>
 * </ul>
 *
 * <p>YACL's built-in reset arrow is suppressed via {@link #canReset()}
 * returning {@code false} so our inline ↻ is the only reset control.
 *
 * <p>In-place chord capture: clicking anywhere on the row (not on the
 * icons) starts a {@link ChordCapture}; subsequent key/mouse events
 * route into it; on release the chord applies via
 * {@link IChordKeyMapping#updateFromChord}.
 */
public class ChordControllerWidget extends ControllerWidget<ChordController> {

    private static final int ICON_BTN_SIZE = 20;
    /** Gap between widgets in the right cluster. Used both between the
     *  two icons AND between the chord button and the conflicts icon
     *  (the latter is the "visible gap" Trev asked for). */
    private static final int GAP = 4;
    private static final int BTN_H = 20;

    private static final Component RESET_GLYPH = Component.literal("↻");
    private static final Component CONFLICTS_GLYPH = Component.literal("⚠");

    /** Cached tooltip — only attached when the Conflicts button is active
     *  (no point telling the user "Show Conflicts" when there are none). */
    private static final Tooltip CONFLICTS_TOOLTIP = Tooltip.create(
            Component.translatable("keybindery.tooltip.show_conflicts"));

    private final Button conflictsButton;
    private final Button resetButton;

    private boolean capturing = false;
    private ChordCapture capture;

    public ChordControllerWidget(ChordController control, YACLScreen screen, Dimension<Integer> dim) {
        super(control, screen, dim);

        // Tooltip attached/detached per-render based on active state — see
        // render(). No tooltip when there are no conflicts.
        this.conflictsButton = Button.builder(
                CONFLICTS_GLYPH,
                btn -> KeybinderyKeyBindsScreen.openWithConflictsFilterFor(
                        control.mapping(), Minecraft.getInstance().screen))
                .bounds(0, 0, ICON_BTN_SIZE, ICON_BTN_SIZE)
                .build();

        // No tooltip — the ↻ glyph is self-explanatory.
        this.resetButton = Button.builder(
                RESET_GLYPH,
                btn -> {
                    if (control.option().changed()) control.option().requestSetDefault();
                })
                .bounds(0, 0, ICON_BTN_SIZE, ICON_BTN_SIZE)
                .build();
    }

    /** Abstract on the base — we bypass {@code super.render} so this only
     *  matters if other YACL machinery reads it. Return a reasonable
     *  fixed value covering the icon area plus typical chord text. */
    @Override
    protected int getHoveredControlWidth() {
        return ICON_BTN_SIZE + GAP + ICON_BTN_SIZE + GAP + 80;
    }

    @Override
    protected int getUnhoveredControlWidth() {
        return getHoveredControlWidth();
    }

    /** Suppress YACL's built-in reset — our ↻ icon is the only reset. */
    @Override
    public boolean canReset() { return false; }

    /** Chord text displayed on the RIGHT of the button (handled by base
     *  ControllerWidget.drawValueText, which we override below to leave
     *  room for the icons). Shows the live capture preview while
     *  capturing, the chord display name when bound, or a click-to-bind
     *  prompt when unbound. Wraps in yellow brackets on conflict. */
    @Override
    protected Component getValueText() {
        if (capturing && capture != null) {
            return capture.getPreviewText();
        }
        Chord chord = control.option().pendingValue();
        Component chordDisplay = (chord == null || chord.isUnbound())
                ? Component.literal(">> click to bind <<")
                : chord.getDisplayName();
        if (ChordConflicts.hasAnyConflict(control.mapping())) {
            return Component.literal("[ ")
                    .append(chordDisplay.copy().withStyle(ChatFormatting.WHITE))
                    .append(" ]")
                    .withStyle(ChatFormatting.YELLOW);
        }
        return chordDisplay;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Compute icon positions FIRST so we can anchor the button rect
        // off them — guarantees a clean GAP between the button and the
        // conflicts icon (no negative-gap arithmetic surprises).
        int btnY = getDimension().y() + (getDimension().height() - BTN_H) / 2;
        int rightEdge = getDimension().x() + getDimension().width() - getXPadding();
        int resetX = rightEdge - ICON_BTN_SIZE;
        int conflictsX = resetX - GAP - ICON_BTN_SIZE;

        // Button rect stops GAP px BEFORE the conflicts icon — that's the
        // visible gap separating the wide chord button from the icons.
        int btnX2 = conflictsX - GAP;

        // Hover only counts when the cursor is over the button rect itself
        // — NOT over the gap or the icons. Without this, hovering an icon
        // would light up the main chord button too (the icons are inside
        // the controller dimension, so isMouseOver(dim) is true there).
        hovered = isMouseOver((double) mouseX, (double) mouseY) && mouseX < btnX2;

        // Chord text — right-aligned inside the button (with right padding).
        Component valueText = getValueText();
        int valueTextWidth = textRenderer.width(valueText);
        int valueTextLeft = btnX2 - getXPadding() - valueTextWidth;

        // Option name — left-aligned when it fits, auto-scrolls (vanilla's
        // built-in marquee) only when the row is too narrow.
        // acceptScrollingWithDefaultCenter would otherwise center short
        // names within the clip rect, which looks off when most names fit
        // comfortably and only a few overflow.
        int nameLeft = getDimension().x() + getXPadding();
        int nameRight = valueTextLeft - GAP;
        int nameBoxWidth = nameRight - nameLeft;
        Component name = control.option().changed() ? modifiedOptionName : control.option().name();

        // Draw the button rect (stops before icons), then chord text on top
        // (fixed-position right), then the name (left-aligned or scrolling).
        // Subclass hovered-control hook last (usually a no-op for us, but
        // preserved for forward-compat with the base).
        drawButtonRect(graphics, getDimension().x(), getDimension().y(),
                btnX2, getDimension().yLimit(),
                (hovered && isAvailable()) || focused, isAvailable());
        graphics.drawString(textRenderer, valueText, valueTextLeft, getTextY(),
                getValueColor(), true);
        if (nameBoxWidth > 0) {
            if (textRenderer.width(name) <= nameBoxWidth) {
                graphics.drawString(textRenderer, name, nameLeft, getTextY(),
                        getValueColor(), true);
            } else {
                graphics.textRenderer(GuiGraphics.HoveredTextEffects.NONE)
                        .acceptScrollingWithDefaultCenter(
                                name, nameLeft, nameRight,
                                getDimension().y(), getDimension().yLimit());
            }
        }
        if (isHovered()) drawHoveredControl(graphics, mouseX, mouseY, delta);

        // Icons — separate widgets now, not overlaid on the button rect.
        conflictsButton.setX(conflictsX);
        conflictsButton.setY(btnY);
        boolean hasConflict = ChordConflicts.hasAnyConflict(control.mapping());
        conflictsButton.active = hasConflict;
        conflictsButton.setTooltip(hasConflict ? CONFLICTS_TOOLTIP : null);
        conflictsButton.render(graphics, mouseX, mouseY, delta);

        resetButton.setX(resetX);
        resetButton.setY(btnY);
        resetButton.active = control.option().changed();
        resetButton.render(graphics, mouseX, mouseY, delta);

        // Per-frame GLFW release polling for the active capture (vanilla pattern).
        if (capturing && capture != null) {
            capture.pollReleases(Minecraft.getInstance().getWindow().handle());
        }
    }

    // ── Click — icons first, otherwise the whole row starts capture ───────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver((double) event.x(), (double) event.y())) return false;

        // If we're already capturing, route mouse clicks anywhere in the
        // row into the capture engine — mouse buttons are valid chord
        // components.
        if (capturing && capture != null) {
            capture.onMousePressed(InputConstants.Type.MOUSE.getOrCreate(event.button()));
            return true;
        }

        // Hit-test the two icons (positions match render()).
        int btnY = getDimension().y() + (getDimension().height() - BTN_H) / 2;
        int rightEdge = getDimension().x() + getDimension().width() - getXPadding();
        int resetX = rightEdge - ICON_BTN_SIZE;
        int conflictsX = resetX - GAP - ICON_BTN_SIZE;
        boolean overReset = within(event.x(), event.y(), resetX, btnY, ICON_BTN_SIZE, BTN_H);
        boolean overConflicts = within(event.x(), event.y(), conflictsX, btnY, ICON_BTN_SIZE, BTN_H);

        // Right-click anywhere except icons → clear chord (matches the
        // chord button's vanilla-style right-click-to-unbind shortcut).
        if (event.button() == 1 && !overReset && !overConflicts) {
            control.option().requestSet(Chord.UNBOUND);
            return true;
        }
        if (event.button() != 0) return false;

        if (overReset) {
            if (resetButton.active) control.option().requestSetDefault();
            return true;
        }
        if (overConflicts) {
            if (conflictsButton.active) {
                KeybinderyKeyBindsScreen.openWithConflictsFilterFor(
                        control.mapping(), Minecraft.getInstance().screen);
            }
            return true;
        }
        // Anywhere else on the row → start chord capture.
        startCapture();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (capturing && capture != null) {
            capture.onMouseReleased(InputConstants.Type.MOUSE.getOrCreate(event.button()));
            return true;
        }
        return super.mouseReleased(event);
    }

    // ── Keyboard during capture ─────────────────────────────────────────────

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!capturing || capture == null) return super.keyPressed(event);
        capture.onKeyPressed(InputConstants.getKey(event));
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (!capturing || capture == null) return super.keyReleased(event);
        capture.onKeyReleased(InputConstants.getKey(event));
        return true;
    }

    // ── Capture lifecycle ───────────────────────────────────────────────────

    private void startCapture() {
        capture = new ChordCapture(
                chord -> {
                    control.option().requestSet(chord);
                    stopCapture();
                },
                this::stopCapture,
                () -> {},
                () -> {
                    control.option().requestSet(Chord.UNBOUND);
                    stopCapture();
                });
        capture.start();
        ChordCapture.activeCapture = capture;
        ChordCapture.activeMapping = control.mapping();
        capturing = true;
    }

    private void stopCapture() {
        capturing = false;
        capture = null;
        if (ChordCapture.activeMapping == control.mapping()) {
            ChordCapture.activeMapping = null;
            ChordCapture.activeCapture = null;
        }
    }

    private static boolean within(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
