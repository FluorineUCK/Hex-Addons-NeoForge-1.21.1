package org.eu.net.pool.hexic.mixin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import org.eu.net.pool.hexic.hexcompat.MoreIotasCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Restores Hexic's MoreIotas-sized signed chat payloads on the 1.21.1 packet
 * codec. Commands use a separate packet and deliberately retain vanilla
 * bounds.
 */
@Mixin(ServerboundChatPacket.class)
public abstract class ServerboundChatPacketMixin {
    @ModifyArg(
        method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/FriendlyByteBuf;readUtf(I)Ljava/lang/String;"
        ),
        index = 0
    )
    private static int hexic$moreIotasReadLimit(int originalLimit) {
        return MoreIotasCompat.serverboundChatReadLimit(originalLimit);
    }

    @ModifyArg(
        method = "write",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/FriendlyByteBuf;writeUtf(Ljava/lang/String;I)Lnet/minecraft/network/FriendlyByteBuf;"
        ),
        index = 1
    )
    private int hexic$moreIotasWriteLimit(int originalLimit) {
        return MoreIotasCompat.serverboundChatWriteLimit(originalLimit);
    }
}
