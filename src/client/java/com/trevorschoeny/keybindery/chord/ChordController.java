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
 * YACL {@link Controller} for {@link Chord} values: the in-config keybind
 * row. Each row holds the chord for one {@link KeyMapping}. The widget
 * ({@link ChordControllerWidget}) works like vanilla's keybind button: click
 * to start capturing, press a multi-key chord, release to apply. No modal
 * screens.
 *
 * <p>The {@code mapping} field travels with the controller so the widget can
 * register itself as the active capture target, which tells other parts of
 * Keybindery (for example the live-preview path in
 * {@link com.trevorschoeny.keybindery.mixin.KeyMappingChordMixin}) which
 * mapping is being bound.
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
        // Yellow brackets mark a conflict, matching the KeyBindsList rule
        // (chord-aware via ChordConflicts: any shared key with another mapping,
        // ignored when both are Mojang defaults).
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
