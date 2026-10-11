package com.trevorschoeny.keybindery.mixin;

import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for vanilla {@link KeyBindsScreen}'s private {@code keyBindsList}
 * field. {@link com.trevorschoeny.keybindery.screen.KeybinderyKeyBindsScreen}
 * uses it to swap vanilla's list for Keybindery's subclass, which can filter
 * and sort, after the vanilla constructor runs.
 */
@Mixin(KeyBindsScreen.class)
public interface KeyBindsScreenAccessor {

    @Accessor("keyBindsList")
    KeyBindsList keybindery$getKeyBindsList();

    @Accessor("keyBindsList")
    void keybindery$setKeyBindsList(KeyBindsList list);
}
