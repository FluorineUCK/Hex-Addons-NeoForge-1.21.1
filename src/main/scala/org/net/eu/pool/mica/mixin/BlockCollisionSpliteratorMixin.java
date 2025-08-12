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
import org.net.eu.pool.mica.AbstractRuneStorage;
import org.net.eu.pool.mica.EmptyRune$;
import org.net.eu.pool.mica.RuneShift;
import org.net.eu.pool.mica.RuneShift$;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.BiFunction;
@Mixin(BlockCollisionSpliterator.class)
public class BlockCollisionSpliteratorMixin {
    @WrapOperation(method = "computeNext", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/ShapeContext;getCollisionShape(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/CollisionView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;"))
    VoxelShape addRuneShapes(ShapeContext instance, BlockState blockState, CollisionView collisionView, BlockPos blockPos, Operation<VoxelShape> original) {
        VoxelShape orig = original.call(instance, blockState, collisionView, blockPos);
        AbstractRuneStorage
        // forloop(n, 1, 768, <<check rune layer #n
        storage = AbstractRuneStorage.get((World) collisionView, eval(n-1));
        if (storage.apply(blockPos) != EmptyRune$.MODULE$) {
            Vec3d middle = blockPos.toCenterPos().add(pyeval(eval(n & 3) / 4 - .5), pyeval(eval(n shr 2 & 7) / 8 - .5), pyeval(eval(n shr 5 & 3) / 4 - .5));
            orig = VoxelShapes.union(orig, VoxelShapes.cuboid(middle.x - .25, middle.y, middle.z - .25, middle.x + .25, middle.y + .0625, middle.z + .25));
        }
        // >>)return after modifications
        return orig;
    }
}