package org.eu.net.pool.hexic.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.util.CuboidBlockIterator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.eu.net.pool.hexic.WarCrime.VOID_AIR;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Unique BlockPos.Mutable hexic$scanPos = new BlockPos.Mutable();

    @Shadow protected abstract boolean doesNotCollide(Box box);
    @Shadow public abstract World getWorld();
    @Shadow public abstract Box getBoundingBox();
    @Shadow private World world;

    @Shadow protected abstract void tickInVoid();

    @Inject(at = @At("TAIL"), method = "attemptTickInVoid")
    void attemptTickInVoidBlocks(CallbackInfo ci) {
        Box box = getBoundingBox();

        CuboidBlockIterator iter = new CuboidBlockIterator(MathHelper.floor(box.minX), MathHelper.floor(box.minY), MathHelper.floor(box.minZ), MathHelper.ceil(box.maxX), MathHelper.ceil(box.maxY), MathHelper.ceil(box.maxZ));
        while (iter.step()) {
            hexic$scanPos.set(iter.getX(), iter.getY(), iter.getZ());
            if (world.getBlockState(hexic$scanPos).isOf(VOID_AIR)) {
                tickInVoid();
                ci.cancel();
            }
        }
    }
}
