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
 * Finds keybind conflicts between a {@link Chord} and every registered
 * {@link KeyMapping}.
 *
 * <p><b>Any shared key is a conflict:</b> two chords conflict if they share at
 * least one key, however the rest of their keys differ. Examples:
 * <ul>
 *   <li>{@code Sneak=X+Z} and {@code Inventory=X}: conflict (shared: X)</li>
 *   <li>{@code Sneak=X+Z} and {@code Drop=Z+Q}: conflict (shared: Z)</li>
 *   <li>{@code Sneak=X+Z} and {@code Forward=W}: no conflict</li>
 *   <li>{@code Sneak=X+Z} and {@code Sneak=X+Z} (the same mapping): skipped
 *       through the exclude parameter</li>
 * </ul>
 *
 * <p>This is broader than vanilla's rule, which only compares the base key.
 * Two chords that share a key can both fire at once (every matching chord
 * fires), so the player needs to see the overlap.
 *
 * <p><b>Default overlaps:</b> Mojang overlaps some defaults on purpose, so an
 * overlap is ignored only when <i>both</i> mappings are vanilla (the
 * translation key has the two-part {@code "key.<action>"} shape) <i>and</i>
 * both are at their Mojang default. Mod defaults get no such pass, since mod
 * authors picked them without knowing vanilla's, so a default collision
 * between a mod and vanilla or between two mods (for example IP's "S" and
 * vanilla's walk-back "S") still shows as a conflict.
 */
public final class ChordConflicts {

    private ChordConflicts() {}

    /**
     * A conflict between the queried chord and another registered keymapping.
     * {@link #sharedKeys} is the intersection of the queried chord's keys
     * and the other mapping's chord keys: the keys that actually collide.
     */
    public record Conflict(KeyMapping mapping, String label, String category,
                           Set<InputConstants.Key> sharedKeys) {}

    /**
     * Returns every {@link KeyMapping} (other than {@code exclude}) that
     * conflicts with the queried chord: any shared key, unless both are
     * vanilla defaults. Empty if the queried chord is unbound.
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
            // Both vanilla and both at default: an overlap Mojang intended, so skip it.
            // (Mod defaults get no such pass, so a mod and vanilla default
            //  collision still shows as a conflict.)
            if (exclude != null && isVanilla(exclude) && exclude.isDefault()
                                && isVanilla(km)      && km.isDefault()) continue;
            String label = Component.translatable(km.getName()).getString();
            String category = km.getCategory().label().getString();
            conflicts.add(new Conflict(km, label, category, shared));
        }
        return conflicts;
    }

    /** True if the mapping's chord conflicts with any other registered key mapping. */
    public static boolean hasAnyConflict(KeyMapping mapping) {
        Chord chord = IChordKeyMapping.getChord(mapping);
        return !findConflicts(chord, mapping).isEmpty();
    }

    /**
     * Chord-aware overlap test, without the default-overlap exception. Used by
     * the {@code KeyBindsList$KeyEntry.refreshEntry} mixin in place of
     * vanilla's single-key {@code KeyMapping.same()}; vanilla's own
     * both-at-default check still applies on top of this result.
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
            lines.add(Component.literal("  - " + c.label() + " (" + c.category() + "), shares: " + sharedStr)
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
     * Returns true if {@code km} is a vanilla key mapping, recognized by the
     * two-part {@code "key.<action>"} translation key vanilla uses for
     * everything in {@code Options}. Modded mappings use the three-part
     * {@code "key.<modid>.<action>"} by Fabric convention.
     */
    public static boolean isVanilla(KeyMapping km) {
        String name = km.getName();
        if (name == null || !name.startsWith("key.")) return false;
        return name.indexOf('.', 4) == -1;
    }
}
