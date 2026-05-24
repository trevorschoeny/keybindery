package com.trevorschoeny.keybindery.mixin;

import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for vanilla {@link KeyBindsScreen}'s private {@code keyBindsList}
 * field — needed by {@link com.trevorschoeny.keybindery.screen.KeybinderyKeyBindsScreen}
 * to swap vanilla's list for our filter/sort-capable subclass after the
 * vanilla constructor runs.
 */
@Mixin(KeyBindsScreen.class)
public interface KeyBindsScreenAccessor {

    @Accessor("keyBindsList")
    KeyBindsList keybindery$getKeyBindsList();

    @Accessor("keyBindsList")
    void keybindery$setKeyBindsList(KeyBindsList list);
}
