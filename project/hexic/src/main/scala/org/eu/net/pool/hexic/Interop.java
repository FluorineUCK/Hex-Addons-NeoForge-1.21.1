package org.eu.net.pool.hexic;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.RegistryEntryArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
    public static @NotNull RegistryKey<World> thoughtWorld;
    public static final @NotNull Block VOID_AIR = new Block(FabricBlockSettings.create().noCollision());
    public static @NotNull BiConsumer<PlayerEntity, List<ItemStack>> playerDeathHook;
    
    public Interop(Entity e) {
        super(e.getBlockPos());
        this.e = e;
    }

    public static <T> RegistryEntry.Reference<T> gre(CommandContext<ServerCommandSource> context, String name, RegistryKey<Registry<T>> ref) throws CommandSyntaxException {
        return RegistryEntryArgumentType.getRegistryEntry(context, name, ref);
    }

    public static <T> ArgumentType<RegistryEntry.Reference<T>> reat(CommandRegistryAccess registryAccess, RegistryKey<Registry<T>> registry) {
        return new RegistryEntryArgumentType<>(registryAccess, registry);
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
