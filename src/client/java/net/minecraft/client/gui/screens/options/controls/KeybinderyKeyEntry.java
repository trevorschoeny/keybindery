package net.minecraft.client.gui.screens.options.controls;

import com.google.common.collect.ImmutableList;
import com.trevorschoeny.keybindery.chord.ChordConflicts;
import com.trevorschoeny.keybindery.mixin.KeyEntryAccessor;
import com.trevorschoeny.keybindery.screen.KeybinderyKeyBindsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Subclass of {@link KeyBindsList.KeyEntry}, in vanilla's package, that draws
 * one row with the key mapping's name and current chord inside a single wide
 * button (click to rebind, as in vanilla), plus two small icon buttons on the
 * far right:
 *
 * <ul>
 *   <li><b>Chord button</b> (wide): name on the left, chord on the right,
 *       click to rebind.</li>
 *   <li><b>⚠ Conflicts</b>: opens the controls screen filtered to the
 *       chord's conflicts. Grayed out when the mapping has none.</li>
 *   <li><b>↻ Reset</b>: restores the mapping's default. Grayed out when it is
 *       already at its default. Uses YACL's text glyph (U+21BB CLOCKWISE OPEN
 *       CIRCLE ARROW).</li>
 * </ul>
 *
 * <p>It lives in vanilla's package so it can reach the package-private
 * {@code KeyEntry(KeyMapping, Component)} constructor, use the inner-class
 * enclosing-instance syntax, and call the package-private
 * {@link KeyBindsList#resetMappingAndUpdateButtons()} for Reset.
 *
 * <p>The chord text is taken on every {@link #refreshEntry()} from vanilla's
 * newly built button message (which already has the selection arrows while
 * the row is being rebound, and yellow brackets from
 * {@link com.trevorschoeny.keybindery.mixin.KeyEntryConflictMixin}). The
 * vanilla button's message is then blanked, so this class can draw the name
 * and chord itself, split left and right inside the button.
 */
public class KeybinderyKeyEntry extends KeyBindsList.KeyEntry {

    /** Icon button size, vanilla's standard button height. */
    private static final int ICON_BTN_SIZE = 20;
    /** Gap between widgets in the right cluster. */
    private static final int GAP = 4;
    /** Inset from the chord button's edges to its inner text. */
    private static final int TEXT_INSET = 6;

    /** U+21BB CLOCKWISE OPEN CIRCLE ARROW, the same glyph as YACL's own reset button. */
    private static final Component RESET_GLYPH = Component.literal("↻");
    /** U+26A0 WARNING SIGN, drawn as text like the reset glyph (no sprite). */
    private static final Component CONFLICTS_GLYPH = Component.literal("⚠");

    /**
     * Only attached while the Conflicts button is active; there is no point
     * offering "Show Conflicts" when there are none.
     */
    private static final Tooltip CONFLICTS_TOOLTIP = Tooltip.create(
            Component.translatable("keybindery.tooltip.show_conflicts"));

    /** The outer list, so Reset can call {@code resetMappingAndUpdateButtons()} as vanilla does. */
    private final KeyBindsList list;
    private final Button conflictsButton;
    private final Button resetIconButton;

    /**
     * Vanilla's newly built chord text (with selection arrows and yellow
     * conflict brackets), taken on each refreshEntry.
     */
    private Component currentChordText = Component.empty();

    KeybinderyKeyEntry(KeyBindsList outer, KeyMapping km, Component name) {
        // outer.super(...) is Java's syntax for calling a non-static inner
        // class's constructor from a subclass outside the enclosing class.
        outer.super(km, name);
        this.list = outer;
        KeyEntryAccessor accessor = (KeyEntryAccessor) this;

        // The tooltip is attached or removed each frame depending on whether
        // there are conflicts (see extractContent).
        this.conflictsButton = Button.builder(
                CONFLICTS_GLYPH,
                btn -> KeybinderyKeyBindsScreen.openWithConflictsFilterFor(
                        accessor.keybindery$getKey(),
                        Minecraft.getInstance().gui.screen()))
                .bounds(0, 0, ICON_BTN_SIZE, ICON_BTN_SIZE)
                .build();

        // No tooltip; the ↻ glyph explains itself.
        this.resetIconButton = Button.builder(
                RESET_GLYPH,
                btn -> {
                    // The same as vanilla's reset: set the key back to its
                    // default, then have the list refresh every entry's
                    // hasCollision and button state.
                    KeyMapping k = accessor.keybindery$getKey();
                    k.setKey(k.getDefaultKey());
                    this.list.resetMappingAndUpdateButtons();
                })
                .bounds(0, 0, ICON_BTN_SIZE, ICON_BTN_SIZE)
                .build();

        // Refresh once so currentChordText is set and the vanilla button's
        // message is blanked before the first render.
        refreshEntry();
    }

    @Override
    public void refreshEntry() {   // 26.2: Entry.refreshEntry is public now
        super.refreshEntry();
        KeyEntryAccessor a = (KeyEntryAccessor) this;
        Button btn = a.keybindery$getChangeButton();
        // Keep vanilla's newly built chord text (with selection arrows while
        // this row is being rebound, and yellow brackets on a conflict), then
        // blank the button's own message so it doesn't draw over the name and
        // chord drawn here.
        this.currentChordText = btn.getMessage().copy();
        btn.setMessage(Component.empty());
    }

    @Override
    public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY,
                               boolean hovered, float partialTick) {   // renamed in 26.2
        KeyEntryAccessor a = (KeyEntryAccessor) this;
        Minecraft mc = Minecraft.getInstance();

        int contentX = this.getContentX();
        int contentY = this.getContentY();
        int contentWidth = this.getContentWidth();
        int btnY = contentY;
        int textY = contentY + (ICON_BTN_SIZE - 9) / 2;

        // Left to right: [wide chord button] [conflicts icon] [reset icon]
        int resetX = contentX + contentWidth - ICON_BTN_SIZE;
        int conflictsX = resetX - GAP - ICON_BTN_SIZE;
        int chordBtnRight = conflictsX - GAP;
        int chordBtnX = contentX;
        int chordBtnW = chordBtnRight - chordBtnX;

        // Vanilla's change button, background only (its message is blanked in
        // refreshEntry), across most of the row. Vanilla's click handler still
        // starts the rebind through the button's onPress.
        Button chordBtn = a.keybindery$getChangeButton();
        chordBtn.setX(chordBtnX);
        chordBtn.setY(btnY);
        chordBtn.setWidth(chordBtnW);
        chordBtn.extractRenderState(g, mouseX, mouseY, partialTick);

        // Chord text on the right, drawn first so its left edge is known
        // before laying out the name's clip rect.
        int chordWidth = mc.font.width(currentChordText);
        int chordLeft = chordBtnRight - TEXT_INSET - chordWidth;
        g.text(mc.font, currentChordText, chordLeft, textY, 0xFFFFFFFF, true);

        // Name on the left: left-aligned when it fits, and scrolling (vanilla's
        // marquee, which all stock Buttons use through
        // AbstractButton.renderDefaultLabel) only when the row is too narrow
        // for the whole name. Always using acceptScrollingWithDefaultCenter
        // would center short names in the clip rect.
        Component name = a.keybindery$getName();
        int nameLeft = chordBtnX + TEXT_INSET;
        // Gap so the scrolling name doesn't touch the chord text.
        int nameRight = chordLeft - GAP;
        int nameBoxWidth = nameRight - nameLeft;
        if (nameBoxWidth > 0) {
            if (mc.font.width(name) <= nameBoxWidth) {
                g.text(mc.font, name, nameLeft, textY, 0xFFFFFFFF, true);
            } else {
                g.textRenderer(GuiGraphicsExtractor.HoveredTextEffects.NONE)
                        .acceptScrollingWithDefaultCenter(
                                name, nameLeft, nameRight, contentY, contentY + ICON_BTN_SIZE);
            }
        }

        // Conflicts icon: active only if this mapping has a conflict (broader
        // than vanilla's hasCollision, since any shared key counts). The
        // tooltip only shows when it is active.
        conflictsButton.setX(conflictsX);
        conflictsButton.setY(btnY);
        boolean hasConflict = ChordConflicts.hasAnyConflict(a.keybindery$getKey());
        conflictsButton.active = hasConflict;
        conflictsButton.setTooltip(hasConflict ? CONFLICTS_TOOLTIP : null);
        conflictsButton.extractRenderState(g, mouseX, mouseY, partialTick);

        // Reset icon: active only when the key isn't already at its default.
        resetIconButton.setX(resetX);
        resetIconButton.setY(btnY);
        resetIconButton.active = !a.keybindery$getKey().isDefault();
        resetIconButton.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        // Replace vanilla's children (changeButton and resetButton). Vanilla's
        // changeButton (now wider, with a blank message) stays so clicks still
        // start a chord capture; vanilla's resetButton is left out (the ↻ icon
        // replaces it); and the ⚠ Conflicts icon is added.
        KeyEntryAccessor a = (KeyEntryAccessor) this;
        return ImmutableList.of(
                a.keybindery$getChangeButton(),
                conflictsButton,
                resetIconButton);
    }
}
