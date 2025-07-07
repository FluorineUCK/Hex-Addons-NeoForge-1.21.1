package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.eu.net.pool.hexic.IotaDuck;
import org.eu.net.pool.hexic.IotaTypeHint;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import scala.collection.mutable.HashMap;
import scala.collection.mutable.Map;

@Mixin(value = Iota.class, remap = false)
public class IotaMixin implements IotaDuck {
    @Shadow @Mutable @Final @NotNull protected IotaType<?> type;
    @Unique private Map<Identifier, NbtCompound> hexic$annotations;

    @Override
    public Map<Identifier, NbtCompound> hexic$getAnnotations() {
        if (hexic$annotations == null) hexic$annotations = new HashMap<>();
        return hexic$annotations;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void modifyType(IotaType<?> type, Object payload, CallbackInfo ci) {
        if (this instanceof IotaTypeHint h) this.type = h.hexic$iotaType();
    }
}
