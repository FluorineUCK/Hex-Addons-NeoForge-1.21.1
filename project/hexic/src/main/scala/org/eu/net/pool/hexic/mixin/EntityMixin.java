package org.eu.net.pool.hexic.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.CuboidBlockIterator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.eu.net.pool.hexic.JavaPlaneAccess;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.eu.net.pool.hexic.Interop.VOID_AIR;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Unique BlockPos.Mutable hexic$scanPos = new BlockPos.Mutable();

    @Shadow public abstract World getWorld();
    @Shadow public abstract Box getBoundingBox();
    @Shadow private World world;

    @Shadow protected abstract void tickInVoid();
    @Shadow public abstract void remove(Entity.RemovalReason reason);

    @Shadow public abstract void discard();

    @Shadow public abstract boolean damage(DamageSource source, float amount);

    @Shadow public abstract boolean isPlayer();

    @Inject(at = @At("TAIL"), method = {"attemptTickInVoid", "method_31473"}, cancellable = true)
    void attemptTickInVoidBlocks(CallbackInfo ci) {
        Box box = getBoundingBox();
        if (!((Object) this instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()))) {
            var id = getWorld().getRegistryKey().getValue();
            if (id.getNamespace().equals("hexic") && id.getPath().startsWith("fresh-") && (box.minX < 0 || box.minY < 0 || box.minZ < 0 || box.maxX > 11 || box.maxY > 11 || box.maxZ > 11)) {
                if ((Object) this instanceof PlayerEntity p) {
                    if (p instanceof ServerPlayerEntity sp) {
                        JavaPlaneAccess.sendDirectlyToHell(sp);
                    }
                } else {
                    discard();
                }
                ci.cancel();
                return;
            }
        }
        CuboidBlockIterator iter = new CuboidBlockIterator(MathHelper.ceil(box.minX), MathHelper.ceil(box.minY), MathHelper.ceil(box.minZ), MathHelper.ceil(box.maxX), MathHelper.ceil(box.maxY), MathHelper.ceil(box.maxZ));
        while (iter.step()) {
            hexic$scanPos.set(iter.getX(), iter.getY(), iter.getZ());
            if (world.getBlockState(hexic$scanPos).isOf(VOID_AIR)) {
                tickInVoid();
                ci.cancel();
                return;
            }
        }
    }

    // stolen from trickster
    @ModifyExpressionValue(method = {"handleFallDamage", "isFireImmune", "isInvulnerableTo"}, at = {@At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;type:Lnet/minecraft/entity/EntityType;", opcode = Opcodes.GETFIELD), @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getType()Lnet/minecraft/entity/EntityType;")})
    private EntityType<?> modifyEntityType(EntityType<?> original) {
        if ((Object) this instanceof PlayerEntity) {
            
        }
        return original;
    }
}
