package com.trevorschoeny.keybindery.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.chord.ChordPressBuffer;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.platform.InputConstants.Type;

/**
 * Records the current time in {@link ChordPressBuffer} whenever a keyboard key
 * is first pressed (KEY_DOWN). Used by Simultaneous Mode: a chord fires when
 * the first presses of all its keys fall within the configured window.
 *
 * <p>Records every key press, including keys not bound to any KeyMapping,
 * because a chord key doesn't have to be bound on its own. (For the chord
 * {@code K+E+2}, the player might not have K bound to anything alone, but
 * pressing K still counts toward the chord.)
 *
 * <p>Mouse-button presses are not recorded here. Mouse buttons in a chord
 * work in the default mode (through the held-button state) but not in Simultaneous
 * Mode. Supporting Simultaneous Mode with mouse buttons is left for later.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardChordPressMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void keybindery$recordPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action != InputConstants.PRESS) return;
        int keyCode = event.key();
        if (keyCode == InputConstants.UNKNOWN.getValue()) return;
        InputConstants.Key key = Type.KEYBOARD.getOrCreate(keyCode);
        ChordPressBuffer.get().recordPress(key, System.currentTimeMillis());
    }
}
