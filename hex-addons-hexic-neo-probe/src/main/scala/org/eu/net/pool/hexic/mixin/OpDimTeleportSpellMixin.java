package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import org.eu.net.pool.hexic.JavaPlaneAccess;
import org.eu.net.pool.hexic.hexcompat.DemiplaneCompat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import scala.Tuple2;

/**
 * Restores the original Hexic entry/exit semantics for Oneironaut's
 * dimensional teleport spell.
 */
@Pseudo
@Mixin(targets = "net.beholderface.oneironaut.casting.patterns.spells.great.OpDimTeleport$Spell")
public abstract class OpDimTeleportSpellMixin {
    @Shadow
    private Entity target;

    @Shadow
    @Final
    @Mutable
    private ServerLevel origin;

    @Shadow
    @Final
    private ServerLevel destination;

    @Shadow
    @Final
    @Mutable
    private Vec3 coords;

    @Unique
    private boolean hexic$fromDemiplane;

    @Unique
    private boolean hexic$toDemiplane;

    @Inject(
            method = "cast(Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)V",
            at = @At("HEAD")
    )
    private void hexic$prepareDemiplaneTransition(
            CastingEnvironment env,
            CallbackInfo ci
    ) {
        hexic$fromDemiplane = DemiplaneCompat.isDemiplane(origin);
        hexic$toDemiplane = DemiplaneCompat.isDemiplane(destination);

        if (hexic$toDemiplane && !hexic$fromDemiplane) {
            // Every demiplane has the same safe 11×11×11 local entry cell.
            coords = new Vec3(5.5, 1.0, 5.5);
            if (target instanceof ServerPlayer player) {
                JavaPlaneAccess.logExcursion(player);
            }
        } else if (hexic$fromDemiplane && !hexic$toDemiplane) {
            // Return to the exact location recorded on entry (or the tether
            // fallback), while keeping Oneironaut's destination validation.
            Tuple2<ServerLevel, Vec3> excursion =
                    JavaPlaneAccess.findExcursion(target, env);
            origin = excursion._1();
            coords = excursion._2();
        }
    }

    @WrapOperation(
            method = "cast(Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lkotlin/jvm/internal/Intrinsics;areEqual(Ljava/lang/Object;Ljava/lang/Object;)Z"
            ),
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;getPigment()Lat/petrak/hexcasting/api/pigment/FrozenPigment;"
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/server/level/ServerPlayer;sendSystemMessage(Lnet/minecraft/network/chat/Component;)V"
                    )
            )
    )
    private boolean hexic$doNotTreatDemiplaneExitAsSameDimension(
            Object first,
            Object second,
            Operation<Boolean> original
    ) {
        return (!hexic$fromDemiplane || hexic$toDemiplane)
                && original.call(first, second);
    }

    @WrapOperation(
            method = "cast(Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/dimension/DimensionType;coordinateScale()D"
            )
    )
    private double hexic$useLocalDemiplaneCoordinates(
            DimensionType instance,
            Operation<Double> original
    ) {
        return hexic$toDemiplane ? 1.0 : original.call(instance);
    }
}
