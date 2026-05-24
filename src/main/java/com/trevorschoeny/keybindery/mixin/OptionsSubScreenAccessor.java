package com.trevorschoeny.keybindery.mixin;

import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for the parent-screen and options refs on {@link OptionsSubScreen}.
 * The setScreen swap-mixin reads these from the vanilla {@link net.minecraft.client.gui.screens.options.controls.KeyBindsScreen}
 * instance so it can hand them to the replacement {@code KeybinderyKeyBindsScreen}
 * constructor.
 */
@Mixin(OptionsSubScreen.class)
public interface OptionsSubScreenAccessor {

    @Accessor("lastScreen")
    Screen keybindery$getLastScreen();

    @Accessor("options")
    Options keybindery$getOptions();
}
