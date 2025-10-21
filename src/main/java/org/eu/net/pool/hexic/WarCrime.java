package org.eu.net.pool.hexic;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.RegistryEntryArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

public class WarCrime extends BlockPos {
    public final @NotNull Entity e;
    public static @NotNull RegistryKey<World> thoughtWorld;
    
    public WarCrime(Entity e) {
        super(e.getBlockPos());
        this.e = e;
    }

    public static <T> RegistryEntry.Reference<T> gre(CommandContext<ServerCommandSource> context, String name, RegistryKey<Registry<T>> ref) throws CommandSyntaxException {
        return RegistryEntryArgumentType.getRegistryEntry(context, name, ref);
    }

    public static <T> ArgumentType<RegistryEntry.Reference<T>> reat(CommandRegistryAccess registryAccess, RegistryKey<Registry<T>> registry) {
        return new RegistryEntryArgumentType<>(registryAccess, registry);
    }
}
