package com.trevorschoeny.keybindery.mixin;

import com.trevorschoeny.keybindery.api.Chord;
import com.trevorschoeny.keybindery.chord.ChordPersistence;
import com.trevorschoeny.keybindery.chord.IChordKeyMapping;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
 * Saves each {@link KeyMapping}'s multi-key chord in vanilla's
 * {@code options.txt} rather than a separate file, so chords live with the
 * rest of the keybinds and anyone reading the options file can see them.
 *
 * <p>Line format added by this mixin: {@code key_chord_<keymap_name>:<serialized>}.
 * Vanilla ignores unknown lines on load (no crash), so the extra lines are
 * safe for vanilla and for other mods reading the same file.
 *
 * <h3>Saving in two steps</h3>
 * Vanilla's {@code save()} rewrites {@code options.txt} completely, and every
 * line vanilla doesn't write is lost. If a save happens before
 * {@link ChordPersistence#applyChordsFromOptionsTxt} has run, every chord in
 * memory is still UNBOUND, so writing chord lines from memory would wipe the
 * saved chords.
 *
 * <p>So the save takes a snapshot and restores it:
 * <ul>
 *   <li><b>HEAD inject:</b> reads the existing {@code key_chord_*} lines from
 *       {@code options.txt} before vanilla rewrites it, and keeps them in a
 *       {@code @Unique} field on the Options instance.</li>
 *   <li><b>TAIL inject:</b> after vanilla has written its content, the chord
 *       lines are added back.
 *       <ul>
 *         <li>If {@link ChordPersistence#appliedFromOptionsTxt} is true (the
 *             chords were loaded), it writes fresh chord lines from the key
 *             mappings in memory, which are the source of truth.</li>
 *         <li>If false (the save ran before loading, for example during the
 *             Options constructor), it restores the HEAD snapshot, keeping
 *             chords that haven't been loaded yet.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>The load side (chord application from options.txt) is in
 * {@link ChordPersistence#applyChordsFromOptionsTxt}, invoked from
 * {@code KeybinderyClient} on {@code ClientLifecycleEvents.CLIENT_STARTED}
 * (after Fabric mod init).
 *
 * <p>Neither inject is {@code cancellable}; both only add to what vanilla does.
 */
@Mixin(Options.class)
public abstract class OptionsChordPersistenceMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("keybindery");

    private static final String CHORD_LINE_PREFIX = ChordPersistence.CHORD_LINE_PREFIX;

    /**
     * Chord lines read from options.txt at save HEAD, restored at save TAIL
     * when the chords haven't been loaded yet.
     */
    @Unique
    private List<String> keybindery$chordLinesSnapshot;

    @Inject(method = "save", at = @At("HEAD"))
    private void keybindery$snapshotChordLines(CallbackInfo ci) {
        Path optionsFile = optionsFilePath();
        keybindery$chordLinesSnapshot = new ArrayList<>();
        if (optionsFile == null || !Files.exists(optionsFile)) return;
        try (Stream<String> stream = Files.lines(optionsFile)) {
            stream.filter(line -> line.startsWith(CHORD_LINE_PREFIX))
                    .forEach(keybindery$chordLinesSnapshot::add);
        } catch (IOException e) {
            LOGGER.warn("[Keybindery] Failed to snapshot chord lines from {}: {}",
                    optionsFile, e.getMessage());
        }
    }

    @Inject(method = "save", at = @At("TAIL"))
    private void keybindery$saveChordState(CallbackInfo ci) {
        Path optionsFile = optionsFilePath();
        if (optionsFile == null || !Files.exists(optionsFile)) return;

        Options self = (Options) (Object) this;
        if (self.keyMappings == null) return;

        // Decide which chord lines to write back.
        List<String> chordLinesToWrite;
        if (ChordPersistence.appliedFromOptionsTxt) {
            chordLinesToWrite = new ArrayList<>();
            for (KeyMapping mapping : self.keyMappings) {
                Chord chord = ((IChordKeyMapping) mapping).keybindery$getChord();
                if (chord == null || chord.isUnbound() || chord.size() <= 1) continue;
                chordLinesToWrite.add(CHORD_LINE_PREFIX + mapping.getName() + ":" + chord.serialize());
            }
        } else {
            chordLinesToWrite = keybindery$chordLinesSnapshot != null
                    ? keybindery$chordLinesSnapshot
                    : new ArrayList<>();
        }

        try {
            // Read what vanilla wrote, drop any existing chord lines (so none are
            // duplicated), then append the chord lines.
            List<String> existing;
            try (Stream<String> stream = Files.lines(optionsFile)) {
                existing = stream
                        .filter(line -> !line.startsWith(CHORD_LINE_PREFIX))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            }
            existing.addAll(chordLinesToWrite);
            Files.write(optionsFile, existing);
        } catch (IOException e) {
            LOGGER.warn("[Keybindery] Failed to persist chord state to {}: {}",
                    optionsFile, e.getMessage());
        }
    }

    private static Path optionsFilePath() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        return mc.gameDirectory.toPath().resolve("options.txt");
    }
}
