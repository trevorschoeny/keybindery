package com.trevorschoeny.keybindery.api;

import com.trevorschoeny.keybindery.chord.ChordController;
import com.trevorschoeny.keybindery.chord.ClaimRegistry;
import com.trevorschoeny.keybindery.chord.IChordKeyMapping;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.List;

/**
 * Real {@link KeybinderyAPI} implementation, registered by keybindery main
 * at client init via {@link KeybinderyAPIHolder#install(KeybinderyAPI)}.
 *
 * <p>Bridges the public api to keybindery main's internal chord engine:
 * <ul>
 *   <li>{@code getChord} / {@code setChord} delegate to the
 *       {@link IChordKeyMapping} duck interface mixed into every vanilla
 *       {@link KeyMapping}.</li>
 *   <li>{@code createYACLChordOption} returns an {@link Option}{@code <Chord>}
 *       with the custom {@link ChordController} — inline capture, no modal
 *       screen, mirrors vanilla's keybind-button UX.</li>
 * </ul>
 */
@ApiStatus.Internal
public final class KeybinderyAPIImpl implements KeybinderyAPI {

    @Override
    public Chord getChord(KeyMapping mapping) {
        return IChordKeyMapping.getChord(mapping);
    }

    @Override
    public void setChord(KeyMapping mapping, Chord chord) {
        IChordKeyMapping.updateFromChord(mapping, chord);
        // Persist immediately so the new chord survives a crash before the
        // next vanilla-triggered options save.
        if (Minecraft.getInstance().options != null) {
            Minecraft.getInstance().options.save();
        }
    }

    @Override
    public Collection<? extends Option<?>> createYACLChordOption(KeyMapping mapping, Component label, OptionDescription description) {
        // Using this widget claims the mapping for the consumer's own UI —
        // auto-append (F2) will skip it elsewhere.
        ClaimRegistry.mark(mapping);
        // Two-row layout — label on top, three buttons below. See javadoc
        // on KeybinderyAPI#createYACLChordOption for why the API returns
        // a collection instead of a single Option.
        return List.of(
                LabelOption.create(label),
                Option.<Chord>createBuilder()
                        .name(Component.empty())
                        .description(description)
                        .binding(
                                Chord.UNBOUND,
                                () -> getChord(mapping),
                                chord -> setChord(mapping, chord))
                        .customController(option -> new ChordController(option, mapping))
                        .build());
    }

    @Override
    public void markClaimed(KeyMapping mapping) {
        ClaimRegistry.mark(mapping);
    }

    @Override
    public boolean isClaimed(KeyMapping mapping) {
        return ClaimRegistry.isClaimed(mapping);
    }
}
