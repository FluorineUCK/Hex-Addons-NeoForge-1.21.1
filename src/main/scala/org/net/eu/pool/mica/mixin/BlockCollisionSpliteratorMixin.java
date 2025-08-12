package org.net.eu.pool.mica.mixin; // seeded

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockCollisionSpliterator;
import net.minecraft.world.CollisionView;
import net.minecraft.world.World;
import org.net.eu.pool.mica.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.function.BiFunction;
@Mixin(BlockCollisionSpliterator.class)
public class BlockCollisionSpliteratorMixin {
    @WrapOperation(method = "computeNext", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/ShapeContext;getCollisionShape(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/CollisionView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;"))
    VoxelShape addRuneShapes(ShapeContext instance, BlockState blockState, CollisionView collisionView, BlockPos blockPos, Operation<VoxelShape> original) {
        VoxelShape orig = original.call(instance, blockState, collisionView, blockPos);
        ArrayList<VoxelShape> shapes = new ArrayList<>();
        if (collisionView instanceof World world) {
            for (int i = 0; i < 768; i++) {
                AbstractRuneStorage storage = AbstractRuneStorage.get(world, i);
                if (storage.apply(blockPos) != EmptyRune$.MODULE$)
                    shapes.add(RuneShift$.MODULE$.shapeCache()[i]);
            }
            if (!shapes.isEmpty()) {
                VoxelShape merged = VoxelShapes.union(VoxelShapes.empty(), shapes.toArray(new VoxelShape[0])).offset(blockPos.toCenterPos());
                return VoxelShapes.union(orig, merged);
            }
        }
        return orig;
    }
}