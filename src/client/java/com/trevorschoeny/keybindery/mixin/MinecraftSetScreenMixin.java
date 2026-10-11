package com.trevorschoeny.keybindery.mixin;

import com.trevorschoeny.keybindery.config.KeybinderyConfig;
import com.trevorschoeny.keybindery.screen.KeybinderyKeyBindsScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Replaces vanilla's {@link KeyBindsScreen} with Keybindery's
 * {@link KeybinderyKeyBindsScreen} when the screen is opened. The approach is
 * taken directly from Controlling (jaredlll08): one
 * {@link ModifyVariable @ModifyVariable} on {@link Gui#setScreen} at HEAD
 * intercepts the {@code Screen} argument, and if it is exactly
 * {@code KeyBindsScreen} (not a subclass, which matters because Keybindery's
 * own screen is a subclass), returns Keybindery's screen instead.
 *
 * <p>This changes the argument at HEAD instead of using @Overwrite, so other
 * mods injecting into setScreen still see the screen and can change it
 * further.
 *
 * <p>When {@link KeybinderyConfig#disableControlsScreenReplacement} is true,
 * the swap is skipped. That suits players running Controlling as well who
 * prefer its screen.
 */
@Mixin(Gui.class)   // 26.2: setScreen moved from Minecraft to Gui (the screen and overlay manager)
public abstract class MinecraftSetScreenMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen keybindery$swapKeyBindsScreen(Screen opened) {
        if (opened == null) return opened;
        // Exact class check, so Keybindery's own subclass isn't swapped again.
        if (opened.getClass() != KeyBindsScreen.class) return opened;
        if (KeybinderyConfig.get().disableControlsScreenReplacement) return opened;

        OptionsSubScreenAccessor accessor = (OptionsSubScreenAccessor) opened;
        return new KeybinderyKeyBindsScreen(
                accessor.keybindery$getLastScreen(),
                accessor.keybindery$getOptions());
    }
}
