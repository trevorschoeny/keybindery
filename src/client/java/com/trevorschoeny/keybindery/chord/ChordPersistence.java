package com.trevorschoeny.keybindery.chord;

import com.trevorschoeny.keybindery.api.Chord;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Saving and loading chords in options.txt, called from two places:
 *
 * <ul>
 *   <li>{@code OptionsChordPersistenceMixin.save} TAIL, on every options save
 *       (vanilla's path).</li>
 *   <li>{@code KeybinderyClient}'s client-started hook, which applies chords
 *       once after Fabric mod init has collected every mod's keybinds into
 *       {@code Options.keyMappings}. Loading at {@code Options.load} TAIL
 *       would run too early for Fabric-registered keybinds, which are added
 *       later, during {@code ClientModInitializer.onInitializeClient}.</li>
 * </ul>
 *
 * <p>Idempotent: each call rewrites or re-reads, never appends or duplicates.
 */
public final class ChordPersistence {

    public static final String CHORD_LINE_PREFIX = "key_chord_";

    private static final Logger LOGGER = LoggerFactory.getLogger("keybindery");

    /**
     * True once {@link #applyChordsFromOptionsTxt} has run successfully.
     * The save-side mixin checks this. While it is false, the mixin skips
     * rewriting chord lines, so it can't wipe chords not yet loaded into memory.
     */
    public static volatile boolean appliedFromOptionsTxt = false;

    private ChordPersistence() {}

    /**
     * Reads {@code options.txt} and applies any {@code key_chord_*} lines
     * to matching key mappings via {@link IChordKeyMapping#updateFromChord}.
     * Safe to call more than once; applying a chord again changes nothing
     * (the duck interface just overwrites the field).
     */
    public static void applyChordsFromOptionsTxt(Options options) {
        Path optionsFile = optionsFilePath();
        if (optionsFile == null || !Files.exists(optionsFile)) {
            appliedFromOptionsTxt = true; // no file, nothing to load, so saving is safe
            return;
        }
        if (options == null || options.keyMappings == null) return;

        Map<String, KeyMapping> byName = new HashMap<>();
        for (KeyMapping m : options.keyMappings) byName.put(m.getName(), m);

        int[] applied = {0};
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
                applied[0]++;
            });
        } catch (IOException e) {
            LOGGER.warn("[Keybindery] Failed to load chord state from {}: {}",
                    optionsFile, e.getMessage());
            return; // leave the flag unset on failure, so save stays in safe mode
        }

        LOGGER.info("[Keybindery] Applied {} chord(s) from options.txt", applied[0]);
        appliedFromOptionsTxt = true;
    }

    private static Path optionsFilePath() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        return mc.gameDirectory.toPath().resolve("options.txt");
    }
}
