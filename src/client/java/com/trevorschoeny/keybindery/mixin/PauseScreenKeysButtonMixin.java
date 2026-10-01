package com.trevorschoeny.keybindery.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Adds a <b>Keys</b> icon button to the pause menu, at the right end of
 * vanilla's row of square buttons (Report Bugs, Give Feedback, Friends,
 * Player Reporting).
 *
 * <p>Vanilla builds that row as one horizontal {@link LinearLayout} and hands
 * it to the pause grid. This catches the hand-off and appends one more child,
 * so the strip spaces and re-centres itself; nothing is positioned by hand.
 * MenuKit leaves the pause menu's own layout alone (same call as Sandboxes,
 * 2026-09-11), so the mod owns this mixin.
 *
 * <p>Clicking opens vanilla {@link KeyBindsScreen}; {@link MinecraftSetScreenMixin}
 * swaps in Keybindery's screen, so the replacement kill-switch still applies.
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenKeysButtonMixin {

    /** Placeholder art until Trev draws the real one; replace the PNG, no code change. */
    private static final Identifier KEYS_SPRITE =
            Identifier.fromNamespaceAndPath("keybindery", "pause_menu/keys");

    // Both of createPauseMenu's three-arg addChild calls pass through here
    // (Return to Game, then the icon strip); only the strip is a LinearLayout.
    @ModifyArg(method = "createPauseMenu",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;ILnet/minecraft/client/gui/layouts/LayoutSettings;)Lnet/minecraft/client/gui/layouts/LayoutElement;"),
            index = 0)
    private LayoutElement keybindery$addKeysButton(LayoutElement element) {
        if (!(element instanceof LinearLayout strip)) return element;

        Screen pause = (Screen) (Object) this;
        Minecraft minecraft = Minecraft.getInstance();
        // Same builder and sizes as vanilla's four neighbours: 20 wide, 15x15 sprite,
        // label shown as the hover tooltip.
        strip.addChild(SpriteIconButton.builder(
                        Component.translatable("keybindery.pause.keys"),
                        b -> minecraft.gui.setScreen(new KeyBindsScreen(pause, minecraft.options)),
                        true)
                .width(20)
                .sprite(KEYS_SPRITE, 15, 15)
                .withTootip()
                .build());
        return element;
    }
}
