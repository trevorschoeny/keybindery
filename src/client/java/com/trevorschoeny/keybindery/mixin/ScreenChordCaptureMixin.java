package com.trevorschoeny.keybindery.mixin;

import com.trevorschoeny.keybindery.chord.ChordCaptureWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives a capturing {@link ChordCaptureWidget} every key before the screen
 * handles it, so Escape clears the binding instead of closing the screen and
 * a button nested in a list gets keys without focus. Does nothing unless a
 * capture widget is capturing.
 */
@Mixin(Screen.class)
public abstract class ScreenChordCaptureMixin {

    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void keybindery$routeToCaptureWidget(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (ChordCaptureWidget.routeKey((Screen) (Object) this, event)) cir.setReturnValue(true);
    }
}
