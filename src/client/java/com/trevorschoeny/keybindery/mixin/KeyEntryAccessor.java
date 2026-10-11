package com.trevorschoeny.keybindery.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes vanilla {@code KeyBindsList$KeyEntry}'s private fields so
 * {@code KeybinderyKeyEntry} (a subclass in the vanilla package, so it can
 * reach the constructor) can read the change and reset buttons, the key
 * mapping, the name and the hasCollision flag during render.
 */
@Mixin(KeyBindsList.KeyEntry.class)
public interface KeyEntryAccessor {

    @Accessor("key")
    KeyMapping keybindery$getKey();

    @Accessor("name")
    Component keybindery$getName();

    @Accessor("changeButton")
    Button keybindery$getChangeButton();

    @Accessor("resetButton")
    Button keybindery$getResetButton();

    @Accessor("hasCollision")
    boolean keybindery$getHasCollision();
}
