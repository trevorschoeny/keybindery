package net.minecraft.client.gui.screens.options.controls;

import com.google.common.collect.ImmutableList;
import com.trevorschoeny.keybindery.chord.ChordConflicts;
import com.trevorschoeny.keybindery.mixin.KeyEntryAccessor;
import com.trevorschoeny.keybindery.screen.KeybinderyKeyBindsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Vanilla-package subclass of {@link KeyBindsList.KeyEntry} that renders
 * a one-row layout combining the keymapping name + current chord inside
 * a single wide button (vanilla-style click-to-rebind), with two
 * text-symbol icon buttons on the far right:
 *
 * <ul>
 *   <li><b>Chord button</b> (wide): name LEFT, chord RIGHT, click to rebind.</li>
 *   <li><b>⚠ Conflicts</b>: opens the controls screen filtered to the
 *       chord's conflict set. Grayed out when the mapping has no conflicts.</li>
 *   <li><b>↻ Reset</b>: restores the mapping's default. Grayed out when
 *       already at default. Uses YACL's text-symbol style (U+21BB CLOCKWISE
 *       OPEN CIRCLE ARROW).</li>
 * </ul>
 *
 * <p>Lives in vanilla's package so it can reach the package-private
 * {@code KeyEntry(KeyMapping, Component)} constructor, the
 * non-static-inner-class enclosing-instance syntax, and
 * {@link KeyBindsList#resetMappingAndUpdateButtons()} (package-private)
 * for the Reset action.
 *
 * <p>The chord text is captured every {@link #refreshEntry()} from
 * vanilla's freshly-built button message (which already includes
 * selection arrows when the row is being rebound and yellow brackets via
 * {@link com.trevorschoeny.keybindery.mixin.KeyEntryConflictMixin}); we
 * then BLANK the vanilla button's message so we can draw name+chord
 * ourselves with a left/right split inside the button.
 */
public class KeybinderyKeyEntry extends KeyBindsList.KeyEntry {

    /** Icon-button size — vanilla's standard button height. */
    private static final int ICON_BTN_SIZE = 20;
    /** Gap between widgets in the right cluster. */
    private static final int GAP = 4;
    /** Inset from the chord button's edges to its inner text. */
    private static final int TEXT_INSET = 6;

    /** YACL's reset glyph — U+21BB CLOCKWISE OPEN CIRCLE ARROW. Matches
     *  YACL's own reset button so the visual vocabulary is consistent. */
    private static final Component RESET_GLYPH = Component.literal("↻");
    /** Conflict glyph — U+26A0 WARNING SIGN. Text-rendered for consistency
     *  with the reset glyph (no sprite atlas). */
    private static final Component CONFLICTS_GLYPH = Component.literal("⚠");

    /** Cached tooltip — only attached when the Conflicts button is active
     *  (no point telling the user "Show Conflicts" when there are none). */
    private static final Tooltip CONFLICTS_TOOLTIP = Tooltip.create(
            Component.translatable("keybindery.tooltip.show_conflicts"));

    /** Captured outer list so the Reset action can call
     *  {@code resetMappingAndUpdateButtons()} the way vanilla does. */
    private final KeyBindsList list;
    private final Button conflictsButton;
    private final Button resetIconButton;

    /** Vanilla's freshly-computed chord text (with selection arrows +
     *  conflict yellow brackets), captured each refreshEntry. */
    private Component currentChordText = Component.empty();

    KeybinderyKeyEntry(KeyBindsList outer, KeyMapping km, Component name) {
        // outer.super(...) is the standard Java syntax for invoking a
        // non-static inner class's constructor from a subclass that lives
        // outside the enclosing class.
        outer.super(km, name);
        this.list = outer;
        KeyEntryAccessor accessor = (KeyEntryAccessor) this;

        // Tooltip attached/detached per-render based on active state — see
        // renderContent. No tooltip when there are no conflicts.
        this.conflictsButton = Button.builder(
                CONFLICTS_GLYPH,
                btn -> KeybinderyKeyBindsScreen.openWithConflictsFilterFor(
                        accessor.keybindery$getKey(),
                        Minecraft.getInstance().screen))
                .bounds(0, 0, ICON_BTN_SIZE, ICON_BTN_SIZE)
                .build();

        // No tooltip — the ↻ glyph is self-explanatory.
        this.resetIconButton = Button.builder(
                RESET_GLYPH,
                btn -> {
                    // Mirror vanilla's reset action exactly: set the key
                    // back to its default, then ask the list to refresh
                    // every entry's hasCollision + button-active state.
                    KeyMapping k = accessor.keybindery$getKey();
                    k.setKey(k.getDefaultKey());
                    this.list.resetMappingAndUpdateButtons();
                })
                .bounds(0, 0, ICON_BTN_SIZE, ICON_BTN_SIZE)
                .build();

        // Run our refresh once so currentChordText is populated AND the
        // vanilla button's message is blanked before the first render.
        refreshEntry();
    }

    @Override
    protected void refreshEntry() {
        super.refreshEntry();
        KeyEntryAccessor a = (KeyEntryAccessor) this;
        Button btn = a.keybindery$getChangeButton();
        // Capture vanilla's freshly-built chord text (incl. selection arrows
        // when this row is being rebound and yellow brackets when conflicts
        // exist), then blank the button's own message so it doesn't draw
        // over our manual name+chord text.
        this.currentChordText = btn.getMessage().copy();
        btn.setMessage(Component.empty());
    }

    @Override
    public void renderContent(GuiGraphics g, int mouseX, int mouseY,
                              boolean hovered, float partialTick) {
        KeyEntryAccessor a = (KeyEntryAccessor) this;
        Minecraft mc = Minecraft.getInstance();

        int contentX = this.getContentX();
        int contentY = this.getContentY();
        int contentWidth = this.getContentWidth();
        int btnY = contentY;
        int textY = contentY + (ICON_BTN_SIZE - 9) / 2;

        // Right cluster: [wide chord button] [conflicts icon] [reset icon]
        int resetX = contentX + contentWidth - ICON_BTN_SIZE;
        int conflictsX = resetX - GAP - ICON_BTN_SIZE;
        int chordBtnRight = conflictsX - GAP;
        int chordBtnX = contentX;
        int chordBtnW = chordBtnRight - chordBtnX;

        // Vanilla's change button — bg only (message blanked in
        // refreshEntry), spans most of the row. Vanilla's click handler
        // still drives chord-rebind via the button's onPress.
        Button chordBtn = a.keybindery$getChangeButton();
        chordBtn.setX(chordBtnX);
        chordBtn.setY(btnY);
        chordBtn.setWidth(chordBtnW);
        chordBtn.render(g, mouseX, mouseY, partialTick);

        // Chord text on the RIGHT, drawn first so we know its left edge
        // before laying out the name's clip rect.
        int chordWidth = mc.font.width(currentChordText);
        int chordLeft = chordBtnRight - TEXT_INSET - chordWidth;
        g.drawString(mc.font, currentChordText, chordLeft, textY, 0xFFFFFFFF, true);

        // Name on the LEFT — left-aligned when it fits, auto-scrolls
        // (vanilla's built-in marquee, used by all stock Buttons via
        // AbstractButton.renderDefaultLabel) ONLY when the row is too
        // narrow to fit the whole name. acceptScrollingWithDefaultCenter
        // would otherwise center short names within the clip rect.
        Component name = a.keybindery$getName();
        int nameLeft = chordBtnX + TEXT_INSET;
        // Gap so the scrolling name doesn't visually butt up against the
        // chord text.
        int nameRight = chordLeft - GAP;
        int nameBoxWidth = nameRight - nameLeft;
        if (nameBoxWidth > 0) {
            if (mc.font.width(name) <= nameBoxWidth) {
                g.drawString(mc.font, name, nameLeft, textY, 0xFFFFFFFF, true);
            } else {
                g.textRenderer(GuiGraphics.HoveredTextEffects.NONE)
                        .acceptScrollingWithDefaultCenter(
                                name, nameLeft, nameRight, contentY, contentY + ICON_BTN_SIZE);
            }
        }

        // Conflicts text-symbol icon — active iff this mapping has any
        // conflict (broader than vanilla's hasCollision; uses any-key-
        // overlap semantics). Tooltip only shows when active.
        conflictsButton.setX(conflictsX);
        conflictsButton.setY(btnY);
        boolean hasConflict = ChordConflicts.hasAnyConflict(a.keybindery$getKey());
        conflictsButton.active = hasConflict;
        conflictsButton.setTooltip(hasConflict ? CONFLICTS_TOOLTIP : null);
        conflictsButton.render(g, mouseX, mouseY, partialTick);

        // Reset text-symbol icon — active iff not already at default.
        resetIconButton.setX(resetX);
        resetIconButton.setY(btnY);
        resetIconButton.active = !a.keybindery$getKey().isDefault();
        resetIconButton.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        // Replace vanilla's children (changeButton + resetButton) with our
        // version: vanilla changeButton (now wider, message-blanked) stays
        // for chord-capture click routing; vanilla resetButton is HIDDEN
        // (our ↻ icon takes its place); plus our ⚠ Conflicts icon.
        KeyEntryAccessor a = (KeyEntryAccessor) this;
        return ImmutableList.of(
                a.keybindery$getChangeButton(),
                conflictsButton,
                resetIconButton);
    }
}
