package com.trevorschoeny.keybindery.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.HeldMouseButtons;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps {@link HeldMouseButtons} current, so a chord with a mouse button in it
 * can tell whether that button is down. The game can poll the keyboard but has
 * no poll for the mouse, so every press and release is recorded here as it
 * arrives, before any screen or key mapping gets to handle it.
 */
@Mixin(MouseHandler.class)
public abstract class MouseButtonStateMixin {

    @Inject(method = "onButton", at = @At("HEAD"))
    private void keybindery$trackButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        HeldMouseButtons.set(info.button(), action == InputConstants.PRESS);
    }
}
