package com.trevorschoeny.keybindery.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.Chord;
import com.trevorschoeny.keybindery.chord.ChordCapture;
import com.trevorschoeny.keybindery.chord.IChordKeyMapping;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces vanilla's single-key capture in {@link KeyBindsScreen} with the
 * multi-key High Water Mark capture from {@link ChordCapture}. Same surface
 * that MK's {@code MKKeyBindsScreenMixin} provided, migrated for Keybindery
 * (2026-05-18).
 *
 * <p>{@link KeyBindsScreen} extension surface stays untouched; this is a
 * client-side mixin only. The replacement-via-{@code @ModifyVariable} mixin
 * for the screen itself (F4 controls-screen overhaul) lands in Section 3.
 *
 * <p>Concrete {@code keyReleased} and {@code mouseReleased} methods are
 * declared here. {@link KeyBindsScreen} doesn't declare them, so the JVM
 * resolves these concrete mixin methods over the inherited interface
 * defaults from {@code Screen}'s hierarchy. The {@code mouseReleased}
 * non-capturing branch replicates the default drag cleanup so the inherited
 * interface default isn't silently shadowed.
 */
@Mixin(KeyBindsScreen.class)
public abstract class KeyBindsScreenCaptureMixin extends Screen {

    protected KeyBindsScreenCaptureMixin() { super(Component.empty()); }

    @Shadow public KeyMapping selectedKey;
    @Shadow public long lastKeySelection;
    @Shadow private KeyBindsList keyBindsList;

    @Unique
    private ChordCapture keybindery$capture;

    @Unique
    private ChordCapture keybindery$createCapture() {
        return new ChordCapture(
                chord -> {
                    if (this.selectedKey != null) {
                        IChordKeyMapping.updateFromChord(this.selectedKey, chord);
                    }
                    this.selectedKey = null;
                    this.lastKeySelection = Util.getMillis();
                    if (this.keyBindsList != null) this.keyBindsList.resetMappingAndUpdateButtons();
                },
                () -> {
                    if (this.selectedKey != null) {
                        IChordKeyMapping.updateFromChord(this.selectedKey, Chord.UNBOUND);
                    }
                    this.selectedKey = null;
                    this.lastKeySelection = Util.getMillis();
                    if (this.keyBindsList != null) this.keyBindsList.resetMappingAndUpdateButtons();
                },
                () -> { if (this.keyBindsList != null) this.keyBindsList.refreshEntries(); }
        );
    }

    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z",
            at = @At("HEAD"), cancellable = true)
    private void keybindery$onKeyPressed(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        if (this.selectedKey == null) return;
        if (keybindery$capture == null || !keybindery$capture.isCapturing()) {
            keybindery$capture = keybindery$createCapture();
            keybindery$capture.start();
            ChordCapture.activeMapping = this.selectedKey;
            ChordCapture.activeCapture = keybindery$capture;
        }
        InputConstants.Key key = InputConstants.getKey(keyEvent);
        keybindery$capture.onKeyPressed(key);
        if (this.keyBindsList != null) this.keyBindsList.refreshEntries();
        cir.setReturnValue(true);
    }

    public boolean keyReleased(KeyEvent keyEvent) {
        if (keybindery$capture == null || !keybindery$capture.isCapturing()) return false;
        InputConstants.Key key = InputConstants.getKey(keyEvent);
        keybindery$capture.onKeyReleased(key);
        return true;
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void keybindery$onMouseClicked(MouseButtonEvent event, boolean doubleClick,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (this.selectedKey == null) return;
        if (keybindery$capture == null || !keybindery$capture.isCapturing()) {
            keybindery$capture = keybindery$createCapture();
            keybindery$capture.start();
            ChordCapture.activeMapping = this.selectedKey;
            ChordCapture.activeCapture = keybindery$capture;
        }
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(event.button());
        keybindery$capture.onMousePressed(key);
        if (this.keyBindsList != null) this.keyBindsList.refreshEntries();
        cir.setReturnValue(true);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (keybindery$capture != null && keybindery$capture.isCapturing()) {
            InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(event.button());
            keybindery$capture.onMouseReleased(key);
            return true;
        }
        this.setDragging(false);
        GuiEventListener focused = this.getFocused();
        if (focused != null) return focused.mouseReleased(event);
        return false;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void keybindery$pollReleases(GuiGraphics graphics, int mouseX, int mouseY,
                                          float partialTick, CallbackInfo ci) {
        if (keybindery$capture != null && keybindery$capture.isCapturing()) {
            long windowHandle = Minecraft.getInstance().getWindow().handle();
            keybindery$capture.pollReleases(windowHandle);
        }
    }
}
