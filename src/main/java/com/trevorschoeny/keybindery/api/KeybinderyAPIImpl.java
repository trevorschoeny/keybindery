package com.trevorschoeny.keybindery.api;

import com.trevorschoeny.keybindery.chord.ChordController;
import com.trevorschoeny.keybindery.chord.IChordKeyMapping;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;

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
    public Option<?> createYACLChordOption(KeyMapping mapping, Component label, OptionDescription description) {
        return Option.<Chord>createBuilder()
                .name(label)
                .description(description)
                .binding(
                        Chord.UNBOUND,
                        () -> getChord(mapping),
                        chord -> setChord(mapping, chord))
                .customController(option -> new ChordController(option, mapping))
                .build();
    }
}
