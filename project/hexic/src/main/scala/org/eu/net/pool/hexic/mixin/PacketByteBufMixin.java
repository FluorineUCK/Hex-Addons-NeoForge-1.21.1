package org.eu.net.pool.hexic.mixin;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(PacketByteBuf.class)
public abstract class PacketByteBufMixin {
    @Shadow public abstract int readInt();

    @Redirect(method = "writeItemStack", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketByteBuf;writeByte(I)Lio/netty/buffer/ByteBuf;"))
    ByteBuf writeCountAsInt(PacketByteBuf instance, int value) {
        return instance.writeInt(value);
    }
    @Redirect(method = "readItemStack", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketByteBuf;readByte()B"))
    byte readCountAsInt(PacketByteBuf instance) {
        return 0;
    }
    @ModifyArg(method = "readItemStack", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;<init>(Lnet/minecraft/item/ItemConvertible;I)V"), index = 1)
    int actuallyReadCount(int count) {
        return readInt();
    }
}
