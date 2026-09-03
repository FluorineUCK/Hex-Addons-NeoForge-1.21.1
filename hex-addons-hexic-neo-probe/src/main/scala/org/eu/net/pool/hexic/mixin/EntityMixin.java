package org.eu.net.pool.hexic.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.eu.net.pool.hexic.Interop;
import org.eu.net.pool.hexic.JavaPlaneAccess;
import org.eu.net.pool.hexic.hexcompat.DemiplaneCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores Hexic's demiplane boundary and void-air behavior. This was an active
 * 2.1.0 mixin and cannot be replaced by runtime-dimension creation alone.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    public abstract Level level();

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    public abstract void discard();

    @Shadow
    protected abstract void onBelowWorld();

    @Inject(method = "checkBelowWorld", at = @At("TAIL"), cancellable = true)
    private void hexic$checkDemiplaneBoundsAndVoidAir(CallbackInfo callback) {
        Entity self = (Entity)(Object)this;
        AABB box = this.getBoundingBox();

        if (this.level() instanceof ServerLevel serverLevel
            && DemiplaneCompat.isDemiplane(serverLevel)
            && (!(self instanceof Player player) || (!player.isCreative() && !player.isSpectator()))
            && (box.minX < 0.0
                || box.minY < 0.0
                || box.minZ < 0.0
                || box.maxX > 11.0
                || box.maxY > 11.0
                || box.maxZ > 11.0)) {
            if (self instanceof ServerPlayer player) {
                JavaPlaneAccess.sendDirectlyToHell(player);
            } else {
                this.discard();
            }
            callback.cancel();
            return;
        }

        if (BlockPos.betweenClosedStream(box)
            .anyMatch(pos -> this.level().getBlockState(pos).is(Interop.VOID_AIR))) {
            this.onBelowWorld();
            callback.cancel();
        }
    }
}
