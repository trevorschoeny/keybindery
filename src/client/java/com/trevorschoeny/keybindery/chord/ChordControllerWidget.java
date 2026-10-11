package com.trevorschoeny.keybindery.chord;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.Chord;
import com.trevorschoeny.keybindery.screen.KeybinderyKeyBindsScreen;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.ControllerWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * One-row YACL widget for rebinding a chord. It follows YACL's base
 * {@code ControllerWidget.render} to draw one button spanning the row, with
 * the option name on the left and the chord on the right (click to rebind,
 * like vanilla). Two small icon buttons sit at the far right:
 *
 * <ul>
 *   <li><b>⚠ Conflicts</b>: opens the controls screen filtered to the
 *       chord's conflicts. Grayed out when there are none.</li>
 *   <li><b>↻ Reset</b>: restores the option's default. Grayed out when
 *       the option is already at its default. Uses YACL's own reset glyph
 *       (U+21BB CLOCKWISE OPEN CIRCLE ARROW).</li>
 * </ul>
 *
 * <p>{@link #canReset()} returns {@code false} to hide YACL's built-in reset
 * arrow, so the inline ↻ is the only reset control.
 *
 * <p>Capture happens in place: clicking anywhere on the row (not on the icons)
 * starts a {@link ChordCapture}, later key and mouse events go into it, and on
 * release the chord is applied via {@link IChordKeyMapping#updateFromChord}.
 */
public class ChordControllerWidget extends ControllerWidget<ChordController> {

    private static final int ICON_BTN_SIZE = 20;
    /**
     * Gap between widgets on the right: between the two icons, and between the
     * chord button and the conflicts icon, so the button visibly ends.
     */
    private static final int GAP = 4;
    private static final int BTN_H = 20;

    private static final Component RESET_GLYPH = Component.literal("↻");
    private static final Component CONFLICTS_GLYPH = Component.literal("⚠");

    /**
     * Only attached while the Conflicts button is active; there is no point
     * offering "Show Conflicts" when there are none.
     */
    private static final Tooltip CONFLICTS_TOOLTIP = Tooltip.create(
            Component.translatable("keybindery.tooltip.show_conflicts"));

    private final Button conflictsButton;
    private final Button resetButton;

    private boolean capturing = false;
    private ChordCapture capture;

    public ChordControllerWidget(ChordController control, YACLScreen screen, Dimension<Integer> dim) {
        super(control, screen, dim);

        // The tooltip is attached or removed each frame depending on whether
        // there are conflicts (see extractRenderState).
        this.conflictsButton = Button.builder(
                CONFLICTS_GLYPH,
                btn -> KeybinderyKeyBindsScreen.openWithConflictsFilterFor(
                        control.mapping(), Minecraft.getInstance().gui.screen()))
                .bounds(0, 0, ICON_BTN_SIZE, ICON_BTN_SIZE)
                .build();

        // No tooltip; the ↻ glyph explains itself.
        this.resetButton = Button.builder(
                RESET_GLYPH,
                btn -> {
                    if (control.option().changed()) control.option().requestSetDefault();
                })
                .bounds(0, 0, ICON_BTN_SIZE, ICON_BTN_SIZE)
                .build();
    }

    /**
     * Abstract on the base class. This widget skips {@code super.render}, so
     * the value only matters if other YACL code reads it; it is a fixed width
     * covering the icons plus typical chord text.
     */
    @Override
    protected int getHoveredControlWidth() {
        return ICON_BTN_SIZE + GAP + ICON_BTN_SIZE + GAP + 80;
    }

    @Override
    protected int getUnhoveredControlWidth() {
        return getHoveredControlWidth();
    }

    /** Hides YACL's built-in reset; the ↻ icon is the only reset. */
    @Override
    public boolean canReset() { return false; }

    /**
     * The chord text on the right of the button. Shows the live preview while
     * capturing, the chord's name when bound, or a click-to-bind prompt when
     * unbound, in yellow brackets on a conflict.
     */
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {   // 26.2/YACL 3.9.5 rename
        // Work out the icon positions first and place the button relative to
        // them, so there is always a clean GAP between the button and the
        // conflicts icon.
        int btnY = getDimension().y() + (getDimension().height() - BTN_H) / 2;
        int rightEdge = getDimension().x() + getDimension().width() - getXPadding();
        int resetX = rightEdge - ICON_BTN_SIZE;
        int conflictsX = resetX - GAP - ICON_BTN_SIZE;

        // The button stops GAP px before the conflicts icon, leaving a visible
        // gap between the wide chord button and the icons.
        int btnX2 = conflictsX - GAP;

        // Hover only counts over the button itself, not the gap or the icons.
        // Otherwise hovering an icon would light up the chord button too (the
        // icons are inside the controller's dimension, so isMouseOver(dim) is
        // true there).
        hovered = isMouseOver((double) mouseX, (double) mouseY) && mouseX < btnX2;

        // Chord text, right-aligned inside the button (with right padding).
        Component valueText = getValueText();
        int valueTextWidth = textRenderer.width(valueText);
        int valueTextLeft = btnX2 - getXPadding() - valueTextWidth;

        // Option name: left-aligned when it fits, and scrolling (vanilla's
        // marquee) only when the row is too narrow. Always using
        // acceptScrollingWithDefaultCenter would center short names in the
        // clip rect, which looks off when most names fit and only a few
        // overflow.
        int nameLeft = getDimension().x() + getXPadding();
        int nameRight = valueTextLeft - GAP;
        int nameBoxWidth = nameRight - nameLeft;
        Component name = control.option().changed() ? modifiedOptionName : control.option().name();

        // Draw the button (stopping before the icons), then the chord text on
        // top at the right, then the name (left-aligned or scrolling). The
        // hovered-control hook comes last; it usually does nothing here, but
        // stays in case the base class starts using it.
        drawButtonRect(graphics, getDimension().x(), getDimension().y(),
                btnX2, getDimension().yLimit(),
                (hovered && isAvailable()) || focused, isAvailable());
        graphics.text(textRenderer, valueText, valueTextLeft, getTextY(),
                getValueColor(), true);
        if (nameBoxWidth > 0) {
            if (textRenderer.width(name) <= nameBoxWidth) {
                graphics.text(textRenderer, name, nameLeft, getTextY(),
                        getValueColor(), true);
            } else {
                graphics.textRenderer(GuiGraphicsExtractor.HoveredTextEffects.NONE)
                        .acceptScrollingWithDefaultCenter(
                                name, nameLeft, nameRight,
                                getDimension().y(), getDimension().yLimit());
            }
        }
        if (isHovered()) extractHoveredControl(graphics, mouseX, mouseY, delta);

        // The icons are separate widgets, not drawn over the button.
        conflictsButton.setX(conflictsX);
        conflictsButton.setY(btnY);
        boolean hasConflict = ChordConflicts.hasAnyConflict(control.mapping());
        conflictsButton.active = hasConflict;
        conflictsButton.setTooltip(hasConflict ? CONFLICTS_TOOLTIP : null);
        conflictsButton.extractRenderState(graphics, mouseX, mouseY, delta);

        resetButton.setX(resetX);
        resetButton.setY(btnY);
        resetButton.active = control.option().changed();
        resetButton.extractRenderState(graphics, mouseX, mouseY, delta);

        // Poll GLFW for releases each frame for the active capture, as vanilla does.
        if (capturing && capture != null) {
            capture.pollReleases(Minecraft.getInstance().getWindow().handle());
        }
    }

    // Clicks: the icons first, otherwise the whole row starts a capture.

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver((double) event.x(), (double) event.y())) return false;

        // While capturing, mouse clicks anywhere in the row go into the
        // capture, since mouse buttons can be part of a chord.
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

        // Right-click anywhere except the icons clears the chord (the same
        // right-click-to-unbind shortcut as the chord button).
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
                        control.mapping(), Minecraft.getInstance().gui.screen());
            }
            return true;
        }
        // Anywhere else on the row starts a chord capture.
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

    // Keyboard during capture.

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
