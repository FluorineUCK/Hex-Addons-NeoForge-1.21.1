package org.eu.net.pool.hexic.mixin;

import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ram.talia.moreiotas.api.mod.MoreIotasConfig;

@Mixin(ChatMessageC2SPacket.class)
public class ChatMessageC2SPacketMixin {
    @ModifyArg(at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketByteBuf;readString(I)Ljava/lang/String;"), method = "<init>(Lnet/minecraft/network/PacketByteBuf;)V")
    private static int modifyReadCap(int oldCap) {
        return Math.max(oldCap, Math.min(MoreIotasConfig.getServer().getMaxStringLength(), 32767));
    }
    @ModifyArg(at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketByteBuf;writeString(Ljava/lang/String;I)Lnet/minecraft/network/PacketByteBuf;"), method = "write")
    private int modifyWriteCap(int oldCap) {
        // we don't know what the server's cap is, so just use the max
        return 32767;
    }
}
