package org.eu.net.pool.hexic.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.eu.net.pool.hexic.hexcompat.HexicClientEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Restores Greater Reveal as permanent chat lines, including chat hit testing,
 * instead of rendering a six-line detached HUD approximation.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @ModifyExpressionValue(
        method = {
            "render",
            "getClickedComponentStyleAt",
            "getMessageTagAt"
        },
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/gui/components/ChatComponent;trimmedMessages:Ljava/util/List;"
        )
    )
    private List<GuiMessage.Line> hexic$prependRevealLines(List<GuiMessage.Line> original) {
        return HexicClientEvents.patchRevealMessages(original);
    }
}
