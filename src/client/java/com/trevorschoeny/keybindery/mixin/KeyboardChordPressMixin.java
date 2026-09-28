package com.trevorschoeny.keybindery.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.chord.ChordPressBuffer;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.platform.InputConstants.Type;

/**
 * Stamps {@link ChordPressBuffer} with the current time whenever a keyboard
 * key is initially pressed (KEY_DOWN). Used for simultaneous-mode chord
 * detection: a chord fires when all its keys' initial press timestamps fall
 * within the configured window.
 *
 * <p>Captures EVERY key press, including keys not bound to any KeyMapping —
 * because chord membership doesn't require any single chord key to be bound
 * elsewhere. (E.g., the chord {@code K+E+2}: the player might not have K
 * bound to anything alone, but pressing K still contributes to the chord.)
 *
 * <p>Mouse-button presses are NOT captured by this mixin. Mouse-button chord
 * components work in default mode (via GLFW poll), but not in simultaneous
 * mode. If a player wants simultaneous mode with a mouse-inclusive chord,
 * that's a follow-up. Scope kept tight per Trev's Section 1 spec.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardChordPressMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void keybindery$recordPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) return;
        int keyCode = event.key();
        if (keyCode == InputConstants.UNKNOWN.getValue()) return;
        InputConstants.Key key = Type.KEYSYM.getOrCreate(keyCode);
        ChordPressBuffer.get().recordPress(key, System.currentTimeMillis());
    }
}
