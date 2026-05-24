package com.trevorschoeny.keybindery.screen;

import com.trevorschoeny.keybindery.api.Chord;
import com.trevorschoeny.keybindery.chord.ChordConflicts;
import com.trevorschoeny.keybindery.chord.IChordKeyMapping;
import com.trevorschoeny.menukit.core.AbstractPanelElement;
import com.trevorschoeny.menukit.core.RenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Vanilla-styled keybind row for the mod-config keybinds modal. Matches
 * {@code KeyBindsList$KeyEntry}'s visual: translated name on the left,
 * 75×20 change button + 50×20 reset button on the right. Yellow brackets
 * around the change button when {@link ChordConflicts} reports a conflict.
 *
 * <p>In-place capture: when {@link #captureMappingSupplier} returns this
 * row's mapping, the change button switches to {@code > ... <} arrows and
 * the displayed chord reflects the parent's live ChordCapture state via
 * {@link #liveCaptureLabelSupplier}.
 *
 * <p>Right-click on the change button clears the binding (parallels
 * {@link SearchKeybindButton}'s "right-click to unbind" convention).
 *
 * <p>Vanilla {@code Button} instances are constructed for rendering only —
 * they're never added to the screen's widget tree. The row draws them
 * inline via {@code render()} and dispatches its own click hit-tests.
 */
public final class KeybindRow extends AbstractPanelElement {

    static final int HEIGHT = 20;
    private static final int CHANGE_W = 75;
    private static final int RESET_W = 50;
    private static final int GAP = 4;

    private final int childX;
    private final int childY;
    private final int width;
    private final KeyMapping mapping;
    private final Supplier<@Nullable KeyMapping> captureMappingSupplier;
    private final Supplier<Component> liveCaptureLabelSupplier;
    private final Consumer<KeyMapping> onChangeClicked;
    private final Runnable onResetClicked;

    private final Button changeButton;
    private final Button resetButton;

    public KeybindRow(int childX, int childY, int width,
                      KeyMapping mapping,
                      Supplier<@Nullable KeyMapping> captureMappingSupplier,
                      Supplier<Component> liveCaptureLabelSupplier,
                      Consumer<KeyMapping> onChangeClicked,
                      Runnable onResetClicked) {
        this.childX = childX;
        this.childY = childY;
        this.width = width;
        this.mapping = mapping;
        this.captureMappingSupplier = captureMappingSupplier;
        this.liveCaptureLabelSupplier = liveCaptureLabelSupplier;
        this.onChangeClicked = onChangeClicked;
        this.onResetClicked = onResetClicked;
        this.changeButton = Button.builder(Component.empty(), btn -> {})
                .bounds(0, 0, CHANGE_W, HEIGHT).build();
        this.resetButton = Button.builder(Component.translatable("controls.reset"), btn -> {})
                .bounds(0, 0, RESET_W, HEIGHT).build();
    }

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return HEIGHT; }

    public KeyMapping mapping() { return mapping; }

    @Override
    public void render(RenderContext ctx) {
        GuiGraphics g = ctx.graphics();
        Minecraft mc = Minecraft.getInstance();
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;

        // Name on the left, vertically centered against the row.
        Component name = Component.translatable(mapping.getName());
        g.drawString(mc.font, name, sx, sy + (HEIGHT - 9) / 2, -1);

        boolean isBeingCaptured = mapping == captureMappingSupplier.get();
        Component chordLabel = isBeingCaptured
                ? liveCaptureLabelSupplier.get()
                : IChordKeyMapping.getChord(mapping).getDisplayName();
        Component changeMsg;
        if (isBeingCaptured) {
            changeMsg = Component.literal("> ")
                    .append(chordLabel.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE))
                    .append(" <").withStyle(ChatFormatting.YELLOW);
        } else if (ChordConflicts.hasAnyConflict(mapping)) {
            changeMsg = Component.literal("[ ")
                    .append(chordLabel.copy().withStyle(ChatFormatting.WHITE))
                    .append(" ]").withStyle(ChatFormatting.YELLOW);
        } else {
            changeMsg = chordLabel;
        }
        changeButton.setX(sx + width - RESET_W - GAP - CHANGE_W);
        changeButton.setY(sy);
        changeButton.setMessage(changeMsg);
        changeButton.render(g, ctx.mouseX(), ctx.mouseY(), 0f);

        resetButton.setX(sx + width - RESET_W);
        resetButton.setY(sy);
        resetButton.active = !mapping.isDefault();
        resetButton.render(g, ctx.mouseX(), ctx.mouseY(), 0f);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Left or right both interact with change button; reset is left-only.
        boolean overChange = within(mouseX, mouseY,
                changeButton.getX(), changeButton.getY(), CHANGE_W, HEIGHT);
        boolean overReset = within(mouseX, mouseY,
                resetButton.getX(), resetButton.getY(), RESET_W, HEIGHT);

        if (button == 1 && overChange) {
            IChordKeyMapping.updateFromChord(mapping, Chord.UNBOUND);
            return true;
        }
        if (button != 0) return false;
        if (overReset) {
            if (!mapping.isDefault()) onResetClicked.run();
            return true;
        }
        if (overChange) {
            onChangeClicked.accept(mapping);
            return true;
        }
        return false;
    }

    private static boolean within(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
