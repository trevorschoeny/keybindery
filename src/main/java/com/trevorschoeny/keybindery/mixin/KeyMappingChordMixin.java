package com.trevorschoeny.keybindery.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.chord.Chord;
import com.trevorschoeny.keybindery.chord.ChordCapture;
import com.trevorschoeny.keybindery.chord.ChordPressBuffer;
import com.trevorschoeny.keybindery.chord.IChordKeyMapping;
import com.trevorschoeny.keybindery.config.KeybinderyConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/**
 * The core mixin that adds multi-key chord support to ANY vanilla {@link KeyMapping}.
 * Implements the {@link IChordKeyMapping} duck interface so any KeyMapping
 * instance can carry a chord field.
 *
 * <p><b>Differences vs. MenuKit's prior {@code MKKeyMappingMixin} (2026-05-18 migration):</b>
 * <ul>
 *   <li><b>No subset-suppression.</b> MK's version suppressed shorter chords when
 *       a longer chord matched ("Shift+K active → don't fire K"). Trev's Q2
 *       directive (all-match-fire) reverses this: every matching subset fires.
 *       So the suppression methods are gone.</li>
 *   <li><b>Dual-mode dispatch.</b> When {@code simultaneousMode} is OFF (default),
 *       {@code setDown(true)} checks GLFW poll (all keys held). When ON, it
 *       checks the {@link ChordPressBuffer} timestamps against the configured
 *       window. Both paths gate the same vanilla setDown call.</li>
 *   <li><b>Re-fire on re-press.</b> Per Trev's #1: chord fires again when the
 *       triggering key is re-pressed. setDown is called on each press, so as
 *       long as we don't track "already-fired-this-buildup" state, re-fires
 *       happen naturally.</li>
 * </ul>
 *
 * <p>Per §0003 (Keybindery canon, accepted 2026-05-18):
 * {@code @Inject(at=@At("HEAD"), cancellable=true)} followed by {@code ci.cancel()}
 * is permitted here. The cancellation preserves other mods' ability to inject
 * earlier and prevent it — the mod-coexistence property §0030 protects.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingChordMixin implements IChordKeyMapping {

    @Shadow protected InputConstants.Key key;

    /** The multi-key chord, or null for vanilla single-key behavior. */
    @Unique
    private Chord keybindery$chord;

    @Override
    public Chord keybindery$getChord() { return keybindery$chord; }

    @Override
    public void keybindery$setChord(Chord chord) { this.keybindery$chord = chord; }

    // ── setDown gate ─────────────────────────────────────────────────────────
    //
    // When a multi-key chord exists, dispatch to the configured mode. If the
    // chord isn't satisfied, cancel — vanilla won't mark the mapping as down.
    // §0003 permits HEAD-cancellable inject here.

    @Inject(method = "setDown(Z)V", at = @At("HEAD"), cancellable = true)
    private void keybindery$onSetDown(boolean isDown, CallbackInfo ci) {
        if (!isDown || keybindery$chord == null || keybindery$chord.isUnbound() || keybindery$chord.size() <= 1) {
            return;
        }

        boolean simultaneous = KeybinderyConfig.get().simultaneousMode;
        boolean active;

        if (simultaneous) {
            long windowMs = KeybinderyConfig.get().simultaneousWindowMs;
            long now = System.currentTimeMillis();
            active = keybindery$chord.isActiveSimultaneous(ChordPressBuffer.get(), windowMs, now);
        } else {
            long windowHandle = Minecraft.getInstance().getWindow().handle();
            active = keybindery$chord.isActiveHeld(windowHandle);
        }

        if (!active) {
            ci.cancel();
        }
    }

    // ── click guard ──────────────────────────────────────────────────────────
    //
    // Vanilla's KeyMapping.click() increments clickCount for every mapping
    // matching the pressed key. For multi-key chords, that increment can be
    // a false positive when the full chord isn't satisfied. Clear those at
    // TAIL so consumeClick() doesn't see them.

    @Inject(method = "click", at = @At("TAIL"))
    private static void keybindery$onClickTail(InputConstants.Key key, CallbackInfo ci) {
        Map<InputConstants.Key, List<KeyMapping>> map = KeyMappingAccessor.keybindery$getMap();
        List<KeyMapping> mappings = map.get(key);
        if (mappings == null) return;

        boolean simultaneous = KeybinderyConfig.get().simultaneousMode;
        long windowMs = KeybinderyConfig.get().simultaneousWindowMs;
        long now = System.currentTimeMillis();
        long windowHandle = Minecraft.getInstance().getWindow().handle();

        for (KeyMapping mapping : mappings) {
            Chord chord = ((IChordKeyMapping) mapping).keybindery$getChord();
            if (chord == null || chord.isUnbound() || chord.size() <= 1) continue;

            boolean active = simultaneous
                    ? chord.isActiveSimultaneous(ChordPressBuffer.get(), windowMs, now)
                    : chord.isActiveHeld(windowHandle);

            if (!active) {
                ((KeyMappingAccessor) mapping).keybindery$setClickCount(0);
            }
        }
    }

    // ── resetMapping: register chord under all its keys ──────────────────────
    //
    // After vanilla rebuilds its KEY → List<KeyMapping> map, register every
    // multi-key chord under EACH constituent key. That way vanilla's dispatch
    // reaches the chord regardless of which key in the chord triggered the
    // event (e.g., pressing 2 last when binding is K+E+2 still hits the
    // KeyMapping that vanilla would only register under its base key).

    @Inject(method = "resetMapping", at = @At("TAIL"))
    private static void keybindery$onResetMappingTail(CallbackInfo ci) {
        Map<InputConstants.Key, List<KeyMapping>> map = KeyMappingAccessor.keybindery$getMap();
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || mc.options.keyMappings == null) return;

        for (KeyMapping mapping : mc.options.keyMappings) {
            Chord chord = ((IChordKeyMapping) mapping).keybindery$getChord();
            if (chord == null || chord.isUnbound() || chord.size() <= 1) continue;

            InputConstants.Key baseKey = ((KeyMappingAccessor) mapping).keybindery$getKey();
            for (InputConstants.Key chordKey : chord.getKeys()) {
                if (chordKey.equals(baseKey)) continue;
                List<KeyMapping> list = map.computeIfAbsent(chordKey, k -> new java.util.ArrayList<>());
                if (!list.contains(mapping)) list.add(mapping);
            }
        }
    }

    // ── Display name: show the full chord ────────────────────────────────────

    @Inject(method = "getTranslatedKeyMessage", at = @At("HEAD"), cancellable = true)
    private void keybindery$onGetTranslatedKeyMessage(CallbackInfoReturnable<Component> cir) {
        if (ChordCapture.activeMapping == (Object) this && ChordCapture.activeCapture != null) {
            cir.setReturnValue(ChordCapture.activeCapture.getPreviewText());
            return;
        }
        if (keybindery$chord != null && !keybindery$chord.isUnbound() && keybindery$chord.size() > 1) {
            cir.setReturnValue(keybindery$chord.getDisplayName());
        }
    }

    // ── Conflict detection: full chord comparison, not single-key ────────────

    @Inject(method = "same", at = @At("HEAD"), cancellable = true)
    private void keybindery$onSame(KeyMapping other, CallbackInfoReturnable<Boolean> cir) {
        Chord thisChord = this.keybindery$chord;
        Chord otherChord = ((IChordKeyMapping) other).keybindery$getChord();

        if (thisChord != null && !thisChord.isUnbound()
                && otherChord != null && !otherChord.isUnbound()) {
            cir.setReturnValue(thisChord.equals(otherChord));
            return;
        }
        // Asymmetric: a multi-key chord doesn't conflict with a single-key
        // vanilla mapping on the same base key — setDown's chord check
        // already prevents the false fire.
        if (thisChord != null && !thisChord.isUnbound() && thisChord.size() > 1) {
            cir.setReturnValue(false);
            return;
        }
        if (otherChord != null && !otherChord.isUnbound() && otherChord.size() > 1) {
            cir.setReturnValue(false);
            return;
        }
    }

    // ── Keep chord in sync when vanilla setKey is called ────────────────────
    //
    // E.g., the controls screen's "Reset" button calls setKey(getDefaultKey()).
    // Without this, the vanilla base key resets but the chord field retains
    // the old multi-key binding — silent malfunction.

    @Inject(method = "setKey", at = @At("TAIL"))
    private void keybindery$onSetKey(InputConstants.Key newKey, CallbackInfo ci) {
        if (newKey.equals(InputConstants.UNKNOWN)) {
            this.keybindery$chord = Chord.UNBOUND;
        } else {
            // Skip auto-sync while a capture session is active for this mapping —
            // the capture engine sets the chord explicitly after finalizing.
            if (ChordCapture.activeMapping != (Object) this) {
                this.keybindery$chord = new Chord(java.util.Set.of(newKey));
            }
        }
    }
}
