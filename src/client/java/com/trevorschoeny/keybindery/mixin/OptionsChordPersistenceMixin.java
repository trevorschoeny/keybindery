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
 * Persists each {@link KeyMapping}'s multi-key chord to vanilla's
 * {@code options.txt} per Trev's Q5 directive (2026-05-18): invade
 * {@code options.txt} rather than a sidecar file, so chord state lives WITH
 * the keybind state and is visible to anyone inspecting the options file.
 *
 * <p>Line format added by this mixin: {@code key_chord_<keymap_name>:<serialized>}.
 * Vanilla ignores unknown lines on load (no crash), so the additions are
 * forward-compatible with vanilla and other mods reading the same file.
 *
 * <h3>Two-phase save (preserve-across-save)</h3>
 * Vanilla's {@code save()} completely rewrites {@code options.txt} — every
 * line vanilla doesn't write is lost. If save fires before
 * {@link ChordPersistence#applyChordsFromOptionsTxt} has run, the in-memory
 * chord field is UNBOUND for everything, so writing fresh chord lines from
 * memory would wipe persisted chord state.
 *
 * <p>To survive this, save uses a snapshot-and-restore pattern:
 * <ul>
 *   <li><b>HEAD inject:</b> reads existing {@code key_chord_*} lines from
 *       {@code options.txt} BEFORE vanilla rewrites it. Snapshot stored on
 *       the Options instance via {@code @Unique} field.</li>
 *   <li><b>TAIL inject:</b> after vanilla wrote its content, our chord
 *       lines need to be re-added.
 *       <ul>
 *         <li>If {@link ChordPersistence#appliedFromOptionsTxt} is true
 *             (apply ran), write fresh chord lines from in-memory keymap
 *             state — that's the canonical source of truth.</li>
 *         <li>If false (save fired before apply, e.g., during Options
 *             constructor), restore the HEAD snapshot — preserves chord
 *             state we haven't yet loaded.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>The load side (chord application from options.txt) is in
 * {@link ChordPersistence#applyChordsFromOptionsTxt}, invoked from
 * {@code KeybinderyClient} on {@code ClientLifecycleEvents.CLIENT_STARTED}
 * (after Fabric mod init).
 *
 * <p>Per §0003 (Keybindery canon): no {@code cancellable} on either inject —
 * both are pure side-effect hooks.
 */
@Mixin(Options.class)
public abstract class OptionsChordPersistenceMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("keybindery");

    private static final String CHORD_LINE_PREFIX = ChordPersistence.CHORD_LINE_PREFIX;

    /** Chord lines snapshotted from options.txt at save HEAD, used to
     *  restore at save TAIL when apply hasn't run yet. */
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
            // Read what vanilla wrote, strip any existing chord lines (so we
            // don't duplicate), then append our chord lines.
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
