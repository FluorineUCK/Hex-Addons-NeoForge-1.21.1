package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.world.ServerWorld;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.beholderface.oneironaut.casting.patterns.spells.great.OpDimTeleport$Spell")
class OpDimTeleport$SpellMixin {
  @Shadow @Final private ServerWorld destination;
  @Shadow @Final @Mutable private Vec3d coords;
  @Unique private boolean toDemiplane;
  @Inject(method = "cast(Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)V", at = @At("HEAD"))
  void preCast(CastingEnvironment env, CallbackInfo ci) {
    Identifier targetID = destination.getRegistryKey().getValue();
    toDemiplane = targetID.getNamespace().equals("hexic") && targetID.getPath().startsWith("fresh-");
    if (toDemiplane) {
      coords = new Vec3d(5.5, 1.0, 5.5);
    }
  }
  @WrapOperation(method = "cast(Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/dimension/DimensionType;coordinateScale()D", ordinal = -1))
  double wrapCoordinateScale(DimensionType instance, Operation<Double> original) {
    return toDemiplane ? 1.0 : original.call(instance);
  }
}
