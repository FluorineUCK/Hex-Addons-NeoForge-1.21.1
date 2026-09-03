package org.eu.net.pool.hexic;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scala.runtime.BoxedUnit;
import scala.util.boundary;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class Interop extends BlockPos {
    public final @NotNull Entity e;
    public static @NotNull ResourceKey<Level> thoughtWorld;
    public static final @NotNull Block VOID_AIR = new Block(BlockBehaviour.Properties.of().noCollission());
    public static @NotNull BiConsumer<Player, List<ItemStack>> playerDeathHook = (player, out) -> {};
    
    public Interop(Entity e) {
        super(e.blockPosition());
        this.e = e;
    }

    public static void perhapsBreakpoint () {
        int x = 1;
    }
    
    public static void callScala(CallbackInfo ci, Consumer<boundary.Label<BoxedUnit>> body) {
        boundary.Label<BoxedUnit> label = new boundary.Label<>();
        try {
            body.accept(label);
        } catch (boundary.Break<BoxedUnit> lbl) {
            if (lbl.label() == label) {
                ci.cancel();
            } else {
                throw lbl;
            }
        }
    }

    public static <T> void callScalaReturnable(CallbackInfoReturnable<T> ci, Consumer<boundary.Label<T>> body) {
        boundary.Label<T> label = new boundary.Label<>();
        try {
            body.accept(label);
        } catch (boundary.Break<T> lbl) {
            if (lbl.label() == label) {
                ci.setReturnValue(lbl.value());
            } else {
                throw lbl;
            }
        }
    }
}
