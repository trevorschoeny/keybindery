package com.trevorschoeny.keybindery.chord;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.Chord;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects keybind conflicts between a {@link Chord} and all registered
 * {@link KeyMapping} instances.
 *
 * <p><b>Any-key-match rule (Trev 2026-05-18):</b> two chords conflict iff
 * they share AT LEAST ONE key, regardless of whether the rest of the keys
 * differ. Examples:
 * <ul>
 *   <li>{@code Sneak=X+Z} vs {@code Inventory=X} → conflict (shared: X)</li>
 *   <li>{@code Sneak=X+Z} vs {@code Drop=Z+Q} → conflict (shared: Z)</li>
 *   <li>{@code Sneak=X+Z} vs {@code Forward=W} → not a conflict</li>
 *   <li>{@code Sneak=X+Z} vs {@code Sneak=X+Z} (same mapping) → skipped via exclude param</li>
 * </ul>
 *
 * <p>This is broader than vanilla's "exact base-key match" rule. Two chords
 * sharing a key may both fire simultaneously (per Trev's all-match-fire
 * directive) — the conflict surface flags that the player needs to know
 * about the overlap.
 *
 * <p><b>Default-suppression rule (Trev 2026-05-24, refined same day):</b>
 * matches Mojang's "intentional default overlap" semantics. Suppression
 * fires only when BOTH mappings are VANILLA (translation key matches the
 * two-segment {@code "key.<action>"} shape) AND both are at their Mojang
 * default. Mod-default collisions are NOT Mojang-blessed (mod authors
 * picked their defaults without knowing vanilla's), so a mod-vs-vanilla or
 * mod-vs-mod default-collision (e.g. IP's "S" vs vanilla walk-back "S")
 * still shows as a conflict.
 */
public final class ChordConflicts {

    private ChordConflicts() {}

    /**
     * A conflict between the queried chord and another registered keymapping.
     * {@link #sharedKeys} is the intersection of the queried chord's keys
     * and the other mapping's chord keys — the actual collision surface.
     */
    public record Conflict(KeyMapping mapping, String label, String category,
                           Set<InputConstants.Key> sharedKeys) {}

    /**
     * Returns every {@link KeyMapping} (other than {@code exclude}) that
     * conflicts with the queried chord. Conflict =
     * any-key-overlap AND not both-at-default. Empty if the queried chord
     * is unbound.
     */
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
            Set<InputConstants.Key> shared = intersection(chord.getKeys(), otherChord.getKeys());
            if (shared.isEmpty()) continue;
            // Both VANILLA and both at default → Mojang-blessed overlap; suppress.
            // (Mod defaults aren't Mojang-blessed, so a mod-vs-vanilla default
            //  collision still surfaces as a conflict.)
            if (exclude != null && isVanilla(exclude) && exclude.isDefault()
                                && isVanilla(km)      && km.isDefault()) continue;
            String label = Component.translatable(km.getName()).getString();
            String category = km.getCategory().label().getString();
            conflicts.add(new Conflict(km, label, category, shared));
        }
        return conflicts;
    }

    /** Convenience: true iff the chord conflicts with any other registered keymapping. */
    public static boolean hasAnyConflict(KeyMapping mapping) {
        Chord chord = IChordKeyMapping.getChord(mapping);
        return !findConflicts(chord, mapping).isEmpty();
    }

    /**
     * Chord-aware overlap predicate WITHOUT the default-suppression rule.
     * Used by the {@code KeyBindsList$KeyEntry.refreshEntry} mixin as a drop-in
     * for vanilla's single-key {@code KeyMapping.same()} — vanilla's
     * existing both-at-default check still fires on top of this result.
     */
    public static boolean hasAnyOverlap(KeyMapping a, KeyMapping b) {
        if (a == null || b == null) return false;
        Chord ca = IChordKeyMapping.getChord(a);
        Chord cb = IChordKeyMapping.getChord(b);
        if (ca.isUnbound() || cb.isUnbound()) return false;
        for (InputConstants.Key k : ca.getKeys()) {
            if (cb.getKeys().contains(k)) return true;
        }
        return false;
    }

    public static List<Component> buildTooltipLines(List<Conflict> conflicts) {
        if (conflicts.isEmpty()) return List.of();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("key.keybindery.conflicts_header")
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
        for (Conflict c : conflicts) {
            String sharedStr = c.sharedKeys().stream()
                    .map(k -> k.getDisplayName().getString())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            lines.add(Component.literal("  - " + c.label() + " (" + c.category() + ") — shares: " + sharedStr)
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return lines;
    }

    private static Set<InputConstants.Key> intersection(Set<InputConstants.Key> a, Set<InputConstants.Key> b) {
        Set<InputConstants.Key> out = new LinkedHashSet<>();
        for (InputConstants.Key k : a) if (b.contains(k)) out.add(k);
        return out;
    }

    /**
     * Returns true if {@code km} is a vanilla (Mojang-shipped) keymapping —
     * identified by the two-segment {@code "key.<action>"} translation key
     * shape vanilla uses for everything in {@code Options}. Modded mappings
     * use three-segment {@code "key.<modid>.<action>"} per Fabric convention.
     */
    public static boolean isVanilla(KeyMapping km) {
        String name = km.getName();
        if (name == null || !name.startsWith("key.")) return false;
        return name.indexOf('.', 4) == -1;
    }
}
