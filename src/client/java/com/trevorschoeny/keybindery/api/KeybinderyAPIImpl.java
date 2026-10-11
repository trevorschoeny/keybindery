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
 * The real {@link KeybinderyAPI} implementation, registered by Keybindery at
 * client init via {@link KeybinderyAPIHolder#install(KeybinderyAPI)}.
 *
 * <p>Connects the public API to Keybindery's internal chord code:
 * <ul>
 *   <li>{@code getChord} and {@code setChord} delegate to the
 *       {@link IChordKeyMapping} duck interface mixed into every vanilla
 *       {@link KeyMapping}.</li>
 *   <li>{@code createYACLChordOption} returns an {@link Option}{@code <Chord>}
 *       with the custom {@link ChordController}: inline capture, no modal
 *       screen, like vanilla's keybind button.</li>
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
        // Using this widget claims the mapping for the consumer's own UI, so
        // the auto-append fallback skips it elsewhere.
        ClaimRegistry.mark(mapping);
        // Two rows: the label on top, three buttons below. The javadoc on
        // KeybinderyAPI#createYACLChordOption explains why the API returns a
        // collection instead of a single Option.
        return List.of(
                LabelOption.create(label),
                Option.<Chord>createBuilder()
                        .name(Component.empty())
                        .description(description)
                        .binding(
                                // YACL's "reset to default" uses this as the
                                // target. It must be the key mapping's actual
                                // default, not UNBOUND, or Reset would clear
                                // the binding instead of restoring it.
                                IChordKeyMapping.defaultChord(mapping),
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

    @Override
    public boolean isHeld(KeyMapping mapping) {
        long windowHandle = Minecraft.getInstance().getWindow().handle();
        return getChord(mapping).isActiveHeld(windowHandle);
    }
}
