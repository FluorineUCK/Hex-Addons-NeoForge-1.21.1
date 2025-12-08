package org.eu.net.pool.hexic

import at.petrak.hexcasting.api.casting.iota.Iota
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.argument.RegistryEntryArgumentType
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier

fun deserializeHook(tag: NbtCompound, world: ServerWorld, original: Operation<Iota>): Iota =
    original.call(tag, world).apply {
        check(this is IotaDuck)
        val a = `hexic$getAnnotations`()
        tag.keys
            .mapNotNull(Identifier::tryParse)
            .filter { it.namespace != "hexcasting" }
            .forEach { a.put(it, tag[it.toString()]) }
    }

fun serializeHook(iota: Iota, original: Operation<NbtCompound>): NbtCompound =
    original.call(iota).also { c ->
        check(iota is IotaDuck)
        iota.`hexic$getAnnotations`().foreach {
            c.put(it._1.toString(), it._2)
        }
    }
