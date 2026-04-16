package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.dimension.DimensionType;
import org.eu.net.pool.hexic.JavaPlaneAccess;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.beholderface.oneironaut.casting.patterns.spells.great.OpDimTeleport$Spell")
class OpDimTeleport$SpellMixin {
  @Shadow private Entity target;
  @Shadow @Final @Mutable private ServerWorld origin;
  @Shadow @Final private ServerWorld destination;
  @Shadow @Final @Mutable private Vec3d coords;
  @Unique private boolean fromDemiplane;
  @Unique private boolean toDemiplane;
  @Inject(method = "cast(Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)V", at = @At("HEAD"))
  void preCast(CastingEnvironment env, CallbackInfo ci) {
    Identifier sourceID = origin.getRegistryKey().getValue();
    fromDemiplane = sourceID.getNamespace().equals("hexic") && sourceID.getPath().startsWith("fresh-");
    Identifier targetID = destination.getRegistryKey().getValue();
    toDemiplane = targetID.getNamespace().equals("hexic") && targetID.getPath().startsWith("fresh-");
    if (toDemiplane && !fromDemiplane) {
      // entering a demiplane for the first time, try to make sure nobody goes to the milk dimension
      coords = new Vec3d(5.5, 1.0, 5.5);
      if (target instanceof ServerPlayerEntity sp) JavaPlaneAccess.logExcursion(sp);
    } else if (fromDemiplane && !toDemiplane) {
      // change ~origin~ and ~coords~ to the excursion; beholderface will handle the rest
      // this /definitely/ won't break the NG discount :clueless:
      final var dest = JavaPlaneAccess.findExcursion(target, env);
      origin = dest._1;
      coords = dest._2;
    }
  }

  @WrapOperation(method = "cast(Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)V", at = @At(value = "INVOKE", target = "Lkotlin/jvm/internal/Intrinsics;areEqual(Ljava/lang/Object;Ljava/lang/Object;)Z"), slice = @Slice(from = @At(value = "INVOKE", target = "Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;getPigment()Lat/petrak/hexcasting/api/pigment/FrozenPigment;"), to = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;sendMessage(Lnet/minecraft/text/Text;)V")))
  boolean lieAboutEquality(Object first, Object second, Operation<Boolean> original) {
    System.out.printf("considering lying (value=%s, from=%s, to=%s)\n", original, fromDemiplane, toDemiplane);
    return (!fromDemiplane || toDemiplane) && original.call(first, second);
  }

  @WrapOperation(method = "cast(Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/dimension/DimensionType;coordinateScale()D", ordinal = -1))
  double wrapCoordinateScale(DimensionType instance, Operation<Double> original) {
    return toDemiplane ? 1.0 : original.call(instance);
  }
}
