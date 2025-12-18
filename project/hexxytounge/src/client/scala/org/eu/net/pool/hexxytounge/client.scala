package org.eu.net.pool.hexxytounge

def tick() =
  println("awawa")

package mixin:
  import net.minecraft.client.MinecraftClient
  import org.eu.net.pool.hexxytounge
  import org.spongepowered.asm.mixin.Mixin
  import org.spongepowered.asm.mixin.injection.{Inject, At}
  import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
  import scala.reflect.ClassTag
  @Mixin(Array(classOf[MinecraftClient]))
  class MinecraftClientMixin:
    @Inject(at = Array(new At(value = "HEAD")), method = Array("tick"))
    def tick(using CallbackInfo) =
      hexxytounge.tick()