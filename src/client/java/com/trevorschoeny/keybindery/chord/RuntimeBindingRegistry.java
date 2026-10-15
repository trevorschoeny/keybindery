package com.trevorschoeny.keybindery.chord;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.KeybinderyClient;
import com.trevorschoeny.keybindery.api.BindingConflict;
import com.trevorschoeny.keybindery.api.Chord;
import com.trevorschoeny.keybindery.api.RuntimeBindings;
import com.trevorschoeny.keybindery.config.KeybinderyConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The real {@link RuntimeBindings}: bindings a mod adds while the game runs,
 * kept in memory and fired from the same place vanilla fires key mappings.
 *
 * <p>Firing: vanilla calls {@code KeyMapping.click(key)} for every key or
 * mouse press it hands to key mappings (in game, no screen open).
 * {@code KeyMappingChordMixin} passes each of those presses to
 * {@link #onClick}. A binding fires when the pressed key is one of its keys
 * and the whole chord is satisfied, in the same mode as a chorded
 * KeyMapping: all keys held, or all first presses inside Simultaneous Mode's
 * window. So pressing the last key of a chord fires it, whichever key that
 * is, and every matching binding fires (no subset suppression), as for
 * KeyMappings.
 */
public final class RuntimeBindingRegistry implements RuntimeBindings {

    public static final RuntimeBindingRegistry INSTANCE = new RuntimeBindingRegistry();

    private record Binding(Component label, Chord chord, Runnable onPress) {}

    /** Insertion order, so conflicts list in the order mods registered them. */
    private final Map<Identifier, Binding> bindings = new LinkedHashMap<>();

    private RuntimeBindingRegistry() {}

    @Override
    public void register(Identifier id, Component label, Chord chord, Runnable onPress) {
        bindings.put(id, new Binding(label, chord == null ? Chord.UNBOUND : chord, onPress));
    }

    @Override
    public void unregister(Identifier id) {
        bindings.remove(id);
    }

    @Override
    public List<BindingConflict> conflicts(Chord chord, @Nullable Identifier except) {
        List<BindingConflict> out = new ArrayList<>();
        if (chord == null || chord.isUnbound()) return out;
        // The player's key mappings, by the Controls screen's own rule.
        for (ChordConflicts.Conflict c : ChordConflicts.findConflicts(chord, null)) {
            out.add(new BindingConflict(
                    Component.literal(c.label() + " (" + c.category() + ")"),
                    c.sharedKeys(), c.mapping(), null));
        }
        // Other runtime bindings: any shared key, same rule.
        bindings.forEach((id, b) -> {
            if (id.equals(except) || b.chord().isUnbound()) return;
            Set<InputConstants.Key> shared = new LinkedHashSet<>(chord.getKeys());
            shared.retainAll(b.chord().getKeys());
            if (!shared.isEmpty()) out.add(new BindingConflict(b.label(), shared, null, id));
        });
        return out;
    }

    /** One key or mouse press vanilla is handing to key mappings. */
    public void onClick(InputConstants.Key key) {
        if (bindings.isEmpty()) return;
        boolean simultaneous = KeybinderyConfig.get().simultaneousMode;
        long windowMs = KeybinderyConfig.get().simultaneousWindowMs;
        long now = System.currentTimeMillis();
        // Copy first: a callback may register or unregister bindings.
        for (Binding b : List.copyOf(bindings.values())) {
            Chord chord = b.chord();
            if (chord.isUnbound() || !chord.getKeys().contains(key)) continue;
            boolean active = chord.size() == 1 || (simultaneous
                    ? chord.isActiveSimultaneous(k -> ChordPressBuffer.get().lastPress(k), windowMs, now)
                    : chord.isActiveHeld());
            if (!active) continue;
            // One mod's broken callback mustn't take the player's input down with it.
            try {
                b.onPress().run();
            } catch (RuntimeException e) {
                KeybinderyClient.LOGGER.error("Runtime binding {} threw on press", b.label().getString(), e);
            }
        }
    }
}
