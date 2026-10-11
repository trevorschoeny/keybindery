package com.trevorschoeny.keybindery.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/**
 * Accessor mixin for {@link KeyMapping}'s private fields. The chord code needs
 * them for the same reasons MenuKit did (clickCount cleanup, reading and
 * writing the key, rebuilding MAP when a chord is registered).
 *
 * <p>Moved here from MenuKit's {@code KeyMappingAccessor}, with the
 * {@code menuKit$} prefix renamed to {@code keybindery$}.
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("clickCount")
    int keybindery$getClickCount();

    @Accessor("clickCount")
    void keybindery$setClickCount(int count);

    @Accessor("key")
    InputConstants.Key keybindery$getKey();

    @Mutable
    @Accessor("key")
    void keybindery$setKey(InputConstants.Key key);

    @Accessor("MAP")
    static Map<InputConstants.Key, List<KeyMapping>> keybindery$getMap() {
        throw new AssertionError("mixin");
    }
}
