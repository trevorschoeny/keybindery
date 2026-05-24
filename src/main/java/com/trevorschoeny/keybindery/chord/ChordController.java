package com.trevorschoeny.keybindery.chord;

import com.trevorschoeny.keybindery.api.Chord;
import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

/**
 * YACL {@link Controller} for {@link Chord} values — F1's in-config keybind
 * row. Each row holds the chord for one {@link KeyMapping}. The widget
 * ({@link ChordControllerWidget}) mimics vanilla's keybind button: click
 * to enter capture mode, press a multi-key chord, release to apply.
 * No modal screens.
 *
 * <p>The {@code mapping} field travels with the controller so the widget
 * can register itself as the "active capture" target (so other parts of
 * Keybindery's machinery — e.g., {@link com.trevorschoeny.keybindery.mixin.KeyMappingChordMixin}'s
 * live-preview path — know which mapping is being bound).
 */
public class ChordController implements Controller<Chord> {

    private final Option<Chord> option;
    private final KeyMapping mapping;

    public ChordController(Option<Chord> option, KeyMapping mapping) {
        this.option = option;
        this.mapping = mapping;
    }

    @Override
    public Option<Chord> option() {
        return option;
    }

    @Override
    public Component formatValue() {
        Chord chord = option.pendingValue();
        if (chord == null || chord.isUnbound()) {
            return Component.literal(">> click to bind <<");
        }
        Component chordDisplay = chord.getDisplayName();
        // Yellow-bracket conflict indicator — matches the vanilla KeyBindsList
        // conflict rule (chord-aware via ChordConflicts: any-key overlap with
        // another non-self mapping, suppressed when both are Mojang-default).
        if (ChordConflicts.hasAnyConflict(mapping)) {
            return Component.literal("[ ")
                    .append(chordDisplay.copy().withStyle(ChatFormatting.WHITE))
                    .append(" ]")
                    .withStyle(ChatFormatting.YELLOW);
        }
        return chordDisplay;
    }

    @Override
    public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> widgetDimension) {
        return new ChordControllerWidget(this, screen, widgetDimension);
    }

    public KeyMapping mapping() {
        return mapping;
    }
}
