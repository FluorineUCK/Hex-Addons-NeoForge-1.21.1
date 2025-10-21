package org.eu.net.pool.hexic.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import org.eu.net.pool.hexic.Interop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

@Mixin(PlayerInventory.class)
class PlayerInventoryMixin {
  @Shadow public PlayerEntity player;

  @WrapOperation(method = "dropAll", at = @At(value = "FIELD", target = "combinedInventory"))
  List foo(PlayerInventory instance, Operation<List> orig) {
    List o = orig.call(instance);
    ArrayList m = new ArrayList<>();
    BiConsumer<PlayerEntity, List<ItemStack>> c = Interop.playerDeathHook;
    c.accept(player, m);
    if (!m.isEmpty()) {
      o = new ArrayList<>(o);
      o.add(m);
      System.out.println(o);
    }
    return o;
  }
}
