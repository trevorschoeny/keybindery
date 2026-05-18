package com.trevorschoeny.keybindery.mixin;

import com.trevorschoeny.keybindery.chord.Chord;
import com.trevorschoeny.keybindery.chord.IChordKeyMapping;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Persists each {@link KeyMapping}'s multi-key chord to vanilla's
 * {@code options.txt} per Trev's Q5 directive (2026-05-18): invade
 * {@code options.txt} rather than a sidecar file, so chord state lives WITH
 * the keybind state and is visible to anyone inspecting the options file.
 *
 * <p>Line format added by this mixin: {@code key_chord_<keymap_name>:<serialized>}.
 * Vanilla ignores unknown lines on load (no crash), so the additions are
 * forward-compatible with vanilla and other mods reading the same file.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>{@link Options#save} TAIL — re-reads the file vanilla just wrote,
 *       strips any pre-existing {@code key_chord_*} lines (idempotency: each
 *       save rewrites our lines fresh, not append-duplicates), then writes
 *       back vanilla's lines + the current chord state.</li>
 *   <li>{@link Options#load} TAIL — reads the file, finds {@code key_chord_*}
 *       lines, deserializes the chord and applies via
 *       {@link IChordKeyMapping#updateFromChord} so the in-memory KeyMapping
 *       carries the chord. Vanilla has already populated the base key from
 *       its own {@code key_*} line by this point; our update overrides where
 *       needed.</li>
 * </ul>
 *
 * <p>Per §0003 (Keybindery canon): no {@code cancellable} here — both
 * injections are pure side-effect TAIL hooks, the safest mixin shape.
 */
@Mixin(Options.class)
public abstract class OptionsChordPersistenceMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("keybindery");

    private static final String CHORD_LINE_PREFIX = "key_chord_";

    @Inject(method = "save", at = @At("TAIL"))
    private void keybindery$saveChordState(CallbackInfo ci) {
        Path optionsFile = optionsFilePath();
        if (optionsFile == null || !Files.exists(optionsFile)) return;

        try {
            // Read what vanilla wrote, strip our prior chord lines (idempotency).
            List<String> existing;
            try (Stream<String> stream = Files.lines(optionsFile)) {
                existing = stream
                        .filter(line -> !line.startsWith(CHORD_LINE_PREFIX))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            }

            // Append fresh chord lines for every KeyMapping with a multi-key chord.
            Minecraft mc = Minecraft.getInstance();
            if (mc.options != null && mc.options.keyMappings != null) {
                for (KeyMapping mapping : mc.options.keyMappings) {
                    Chord chord = ((IChordKeyMapping) mapping).keybindery$getChord();
                    if (chord == null || chord.isUnbound() || chord.size() <= 1) continue;
                    existing.add(CHORD_LINE_PREFIX + mapping.getName() + ":" + chord.serialize());
                }
            }

            Files.write(optionsFile, existing);
        } catch (IOException e) {
            LOGGER.warn("[Keybindery] Failed to persist chord state to {}: {}",
                    optionsFile, e.getMessage());
        }
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void keybindery$loadChordState(CallbackInfo ci) {
        Path optionsFile = optionsFilePath();
        if (optionsFile == null || !Files.exists(optionsFile)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || mc.options.keyMappings == null) return;

        // Build a name → KeyMapping lookup once. KeyMapping names are unique
        // by Fabric registration contract, so a flat map is safe.
        java.util.Map<String, KeyMapping> byName = new java.util.HashMap<>();
        for (KeyMapping m : mc.options.keyMappings) byName.put(m.getName(), m);

        try (Stream<String> stream = Files.lines(optionsFile)) {
            stream.forEach(line -> {
                if (!line.startsWith(CHORD_LINE_PREFIX)) return;
                int colon = line.indexOf(':');
                if (colon < 0) return;
                String name = line.substring(CHORD_LINE_PREFIX.length(), colon);
                String serialized = line.substring(colon + 1);
                KeyMapping mapping = byName.get(name);
                if (mapping == null) return;
                Chord chord = Chord.deserialize(serialized);
                if (chord.isUnbound() || chord.size() <= 1) return;
                IChordKeyMapping.updateFromChord(mapping, chord);
            });
        } catch (IOException e) {
            LOGGER.warn("[Keybindery] Failed to load chord state from {}: {}",
                    optionsFile, e.getMessage());
        }
    }

    private static Path optionsFilePath() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        return mc.gameDirectory.toPath().resolve("options.txt");
    }
}
