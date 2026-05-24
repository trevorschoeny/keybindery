package com.trevorschoeny.keybindery.mixin;

import com.trevorschoeny.keybindery.chord.ChordConflicts;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replaces vanilla's single-key {@link KeyMapping#same(KeyMapping)} equality
 * inside {@code KeyBindsList$KeyEntry.refreshEntry} with chord-aware overlap
 * via {@link ChordConflicts#hasAnyOverlap(KeyMapping, KeyMapping)}.
 *
 * <p>Vanilla's check (line 169 of {@code KeyBindsList}):
 * <pre>
 *   if (keyMapping != this.key
 *       && this.key.same(keyMapping)
 *       && (!keyMapping.isDefault() || !this.key.isDefault())) { ... hasCollision = true ... }
 * </pre>
 *
 * <p>Vanilla's {@code same()} returns true iff the two mappings' primary
 * vanilla key codes match (single-key equality). That misses chord overlap
 * cases like {@code R} vs {@code R+E} when the chord's primary differs
 * from the single key. The {@code @Redirect} swaps in {@code hasAnyOverlap}
 * — chord-aware any-key intersection — and lets vanilla's downstream
 * default-suppression check fire on the result unchanged. Yellow brackets
 * then surface for partial-chord overlaps but stay suppressed for
 * Mojang-blessed both-at-default pairings.
 *
 * <p>Precise injection per §0030 (no {@code @Overwrite}); a single
 * {@code @Redirect} on the narrowest viable call site.
 */
@Mixin(KeyBindsList.KeyEntry.class)
public abstract class KeyEntryConflictMixin {

    @Redirect(method = "refreshEntry",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/KeyMapping;same(Lnet/minecraft/client/KeyMapping;)Z"))
    private boolean keybindery$chordAwareSame(KeyMapping self, KeyMapping other) {
        return ChordConflicts.hasAnyOverlap(self, other);
    }
}
