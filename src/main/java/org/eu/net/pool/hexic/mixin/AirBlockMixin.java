package org.eu.net.pool.hexic.mixin;

import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.eu.net.pool.hexic.WarCrime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AirBlock.class)
public class AirBlockMixin {
    @Unique long hexic$lastSampledTick;
    @Unique SimplexNoiseSampler hexic$sampler;

    @Unique
    private boolean hexic$isWorld(BlockView view) {
        return view instanceof World world && world.getRegistryKey().equals(WarCrime.thoughtWorld);
    }

    @Unique
    private boolean hexic$isBlockHere(World world, BlockPos pos) {
        if (hexic$isWorld(world)) {
            if (hexic$sampler == null || world.getTime() != hexic$lastSampledTick) {
                hexic$lastSampledTick = world.getTime();
                hexic$sampler = new SimplexNoiseSampler(world.getRandom());
            }
            return Math.sqrt(hexic$sampler.sample(pos.getX(), pos.getZ()) * 64*64) < pos.getY();
        } else {
            return false;
        }
    }

    @Inject(method = "getOutlineShape", at = @At("HEAD"), cancellable = true)
    void thisIsDefinitelyNotaBadIdea(BlockState state, BlockView world, BlockPos pos, ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (world instanceof World actualWorld && hexic$isBlockHere(actualWorld, pos)) {
            cir.setReturnValue(VoxelShapes.fullCube());
        }
    }
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    void whatCouldGoWrong(BlockState state, CallbackInfoReturnable<BlockRenderType> cir) {
        cir.setReturnValue(BlockRenderType.MODEL);
    }
}
