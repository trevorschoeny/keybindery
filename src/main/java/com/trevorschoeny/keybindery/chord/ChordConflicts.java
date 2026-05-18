package com.trevorschoeny.keybindery.chord;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects keybind conflicts between a {@link Chord} and all registered
 * {@link KeyMapping} instances. Compares full key sets — two bindings with
 * different key combos are NOT considered conflicting even if they share a key.
 *
 * <p>Migrated from MenuKit's {@code MKKeybindConflicts} (2026-05-18).
 * Translation key updated to {@code key.keybindery.conflicts_header}.
 *
 * <p>Conflict semantics:
 * <ul>
 *   <li>Both mappings have chords: exact key-set match = conflict.</li>
 *   <li>One has a chord, the other doesn't: the vanilla single key wraps as a
 *       one-key Chord for comparison. Conflicts only if exact match.</li>
 * </ul>
 *
 * <p>Note: per Trev's all-match-fire directive (Q2, 2026-05-18), subset/superset
 * chords (e.g., {@code K} and {@code K+E+2}) are NOT conflicts at runtime —
 * both fire when their key sets are matched. Keep that in mind when surfacing
 * conflict tooltips: a "soft conflict" between subset chords isn't a bug.
 */
public final class ChordConflicts {

    private ChordConflicts() {}

    public record Conflict(KeyMapping mapping, String label, String category) {}

    public static List<Conflict> findConflicts(Chord chord, KeyMapping exclude) {
        List<Conflict> conflicts = new ArrayList<>();
        if (chord == null || chord.isUnbound()) return conflicts;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || mc.options.keyMappings == null) return conflicts;
        for (KeyMapping km : mc.options.keyMappings) {
            if (km == exclude) continue;
            if (km.isUnbound()) continue;
            Chord otherChord = IChordKeyMapping.getChord(km);
            if (otherChord.isUnbound()) continue;
            if (!chord.equals(otherChord)) continue;
            String label = Component.translatable(km.getName()).getString();
            String category = km.getCategory().label().getString();
            conflicts.add(new Conflict(km, label, category));
        }
        return conflicts;
    }

    public static List<Component> buildTooltipLines(List<Conflict> conflicts) {
        if (conflicts.isEmpty()) return List.of();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("key.keybindery.conflicts_header")
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
        for (Conflict c : conflicts) {
            lines.add(Component.literal("  - " + c.label() + " (" + c.category() + ")")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return lines;
    }
}
