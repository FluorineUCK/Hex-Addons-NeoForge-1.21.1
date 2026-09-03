package org.eu.net.pool.hexic.mixin.client;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mapping-safe access to the active chat input.
 *
 * <p>Using {@code getDeclaredField("input")} only works in a mapped
 * development runtime; the literal field name is not remapped in a production
 * client. The Mixin accessor is remapped with the rest of the mod.</p>
 */
@Mixin(ChatScreen.class)
public interface ChatScreenAccess {
    @Accessor("input")
    EditBox hexic$getInput();
}
