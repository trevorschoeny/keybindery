package com.trevorschoeny.keybindery.mixin;

import com.trevorschoeny.keybindery.chord.ChordConflicts;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replaces vanilla's single-key {@link KeyMapping#same(KeyMapping)} check in
 * {@code KeyBindsList$KeyEntry.refreshEntry} with chord-aware overlap through
 * {@link ChordConflicts#hasAnyOverlap(KeyMapping, KeyMapping)}.
 *
 * <p>Vanilla's check (line 169 of {@code KeyBindsList}):
 * <pre>
 *   if (keyMapping != this.key
 *       && this.key.same(keyMapping)
 *       && (!keyMapping.isDefault() || !this.key.isDefault())) { ... hasCollision = true ... }
 * </pre>
 *
 * <p>Vanilla's {@code same()} is true only if the two mappings' primary key
 * codes match. That misses chord overlaps like {@code R} against {@code R+E}
 * when the chord's primary key isn't the single key. The {@code @Redirect}
 * swaps in {@code hasAnyOverlap} (true if the chords share any key), and
 * vanilla's default check after it runs on the result unchanged. Yellow
 * brackets then show for partial chord overlaps, but not for vanilla pairs
 * that are both at their defaults.
 *
 * <p>No {@code @Overwrite}: each change is a {@code @Redirect} on the
 * narrowest call site that works.
 */
@Mixin(KeyBindsList.KeyEntry.class)
public abstract class KeyEntryConflictMixin {

    @Redirect(method = "refreshEntry",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/KeyMapping;same(Lnet/minecraft/client/KeyMapping;)Z"))
    private boolean keybindery$chordAwareSame(KeyMapping self, KeyMapping other) {
        return ChordConflicts.hasAnyOverlap(self, other);
    }

    // Vanilla's conflict line is:
    //   if (... && this.key.same(km) && (!km.isDefault() || !this.key.isDefault())) { ... }
    // The both-at-default exception is meant for overlaps Mojang intended. But
    // isDefault() is true for any mapping at its own default, including a mod
    // that happened to ship the same default key as vanilla. For example, an
    // Inventory Plus keybind defaults to S, and vanilla's walk-back defaults to
    // S. Both are at default, so vanilla hides the conflict, though Mojang never
    // chose that overlap (the mod picked S on its own).
    //
    // So the two isDefault() calls in the loop condition are redirected to count
    // a mapping as at its default only when it is a vanilla mapping. A mod
    // mapping gets false, so vanilla's (!a || !b) check passes and the conflict
    // shows.
    //
    // ordinal=0 is the resetButton.active call earlier in the method. It is
    // left alone so reset still disables correctly for mod mappings at default.

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
