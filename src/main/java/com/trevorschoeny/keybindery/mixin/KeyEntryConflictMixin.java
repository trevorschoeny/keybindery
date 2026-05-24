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

    // Vanilla's conflict-suppression line is:
    //   if (... && this.key.same(km) && (!km.isDefault() || !this.key.isDefault())) { ... }
    // The both-at-default suppression there is supposed to be "Mojang-blessed
    // intentional overlap." But vanilla's `isDefault()` returns true for any
    // mapping at its OWN default — including a mod that happened to ship its
    // default at the same key vanilla uses. Trev's example: IP keybind defaults
    // to S; vanilla walk-back defaults to S. Both isDefault → vanilla suppresses,
    // even though Mojang didn't bless that overlap (the mod chose S unilaterally).
    //
    // Fix: redirect the two isDefault() calls inside the loop condition so they
    // only treat a mapping as "at default" when it's actually a VANILLA mapping.
    // Mod-default mappings get false, which makes vanilla's (!a || !b) check
    // trivially pass and the conflict surfaces.
    //
    // ordinal=0 is the resetButton.active call earlier in the method — left
    // alone so reset still disables correctly on mod-default-bound mappings.

    @Redirect(method = "refreshEntry",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/KeyMapping;isDefault()Z",
                     ordinal = 1))
    private boolean keybindery$vanillaOnlyDefault_other(KeyMapping other) {
        return ChordConflicts.isVanilla(other) && other.isDefault();
    }

    @Redirect(method = "refreshEntry",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/KeyMapping;isDefault()Z",
                     ordinal = 2))
    private boolean keybindery$vanillaOnlyDefault_self(KeyMapping self) {
        return ChordConflicts.isVanilla(self) && self.isDefault();
    }
}
