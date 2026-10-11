package com.trevorschoeny.keybindery.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.Chord;
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
 * The core mixin that adds multi-key chord support to every vanilla
 * {@link KeyMapping}. Implements the {@link IChordKeyMapping} duck interface so
 * any KeyMapping can carry a chord.
 *
 * <p><b>How it differs from MenuKit's earlier {@code MKKeyMappingMixin}:</b>
 * <ul>
 *   <li><b>No subset suppression.</b> MenuKit's version kept shorter chords
 *       from firing when a longer chord matched (Shift+K held, so K doesn't
 *       fire). Here every matching chord fires, so those suppression methods
 *       are gone.</li>
 *   <li><b>Two detection modes.</b> With {@code simultaneousMode} off (the
 *       default), {@code setDown(true)} polls GLFW to check all keys are held.
 *       With it on, it checks the {@link ChordPressBuffer} times against the
 *       configured window. Both decide the same vanilla setDown call.</li>
 *   <li><b>Fires again on a new press.</b> A chord fires again when its
 *       triggering key is pressed again. setDown runs on each press, and no
 *       "already fired" state is kept, so this just happens.</li>
 * </ul>
 *
 * <p>{@code @Inject(at=@At("HEAD"), cancellable=true)} followed by
 * {@code ci.cancel()} is allowed here. Unlike an @Overwrite, it still lets
 * other mods inject earlier and prevent the cancel, so mods can coexist.
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

    // setDown: when a multi-key chord exists, check it in the configured mode.
    // If the chord isn't satisfied, cancel, so vanilla won't mark the mapping
    // as down.

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
            active = keybindery$chord.isActiveSimultaneous(k -> ChordPressBuffer.get().lastPress(k), windowMs, now);
        } else {
            long windowHandle = Minecraft.getInstance().getWindow().handle();
            active = keybindery$chord.isActiveHeld(windowHandle);
        }

        if (!active) {
            ci.cancel();
        }
    }

    // click: vanilla's KeyMapping.click() increments clickCount for every mapping
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
                    ? chord.isActiveSimultaneous(k -> ChordPressBuffer.get().lastPress(k), windowMs, now)
                    : chord.isActiveHeld(windowHandle);

            if (!active) {
                ((KeyMappingAccessor) mapping).keybindery$setClickCount(0);
            }
        }
    }

    // resetMapping: after vanilla rebuilds its key-to-List<KeyMapping> map,
    // register every multi-key chord under each of its keys. That way vanilla's
    // dispatch reaches the chord whichever of its keys triggered the event
    // (pressing 2 last for the binding K+E+2 still reaches the KeyMapping that
    // vanilla would only register under its base key).

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

    // Display name: show the full chord.

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

    // Conflict detection: compare the full chord, not a single key.

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
        // vanilla mapping on the same base key, because setDown's chord check
        // already stops the false fire.
        if (thisChord != null && !thisChord.isUnbound() && thisChord.size() > 1) {
            cir.setReturnValue(false);
            return;
        }
        if (otherChord != null && !otherChord.isUnbound() && otherChord.size() > 1) {
            cir.setReturnValue(false);
            return;
        }
    }

    // Keep the chord in sync when vanilla's setKey is called. For example, the
    // controls screen's Reset button calls setKey(getDefaultKey()). Without
    // this, the base key resets but the chord field keeps the old multi-key
    // binding, and the keybind silently misbehaves.

    @Inject(method = "setKey", at = @At("TAIL"))
    private void keybindery$onSetKey(InputConstants.Key newKey, CallbackInfo ci) {
        if (newKey.equals(InputConstants.UNKNOWN)) {
            this.keybindery$chord = Chord.UNBOUND;
        } else {
            // Skip the sync while a capture is active for this mapping; the
            // capture sets the chord itself when it finishes.
            if (ChordCapture.activeMapping != (Object) this) {
                this.keybindery$chord = new Chord(java.util.Set.of(newKey));
            }
        }
    }
}
