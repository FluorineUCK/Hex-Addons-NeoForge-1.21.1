package org.eu.net.pool.hexxytounge

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.ChatScreen

given Conversion[ChatScreen, mixin.ChatScreenAccess] = _.asInstanceOf

var lastMurmur = None: Option[String]
def tick() =
  val currentMurmur = MinecraftClient.getInstance.currentScreen match
    case null => None
    case c: ChatScreen => Some(c.chatField.getText)
    case _ => None
  if currentMurmur != lastMurmur then
    lastMurmur = currentMurmur
    val buf = PacketByteBufs.create()
    buf.writeBoolean(currentMurmur.isDefined)
    currentMurmur.foreach(buf.writeString)
    try
      ClientPlayNetworking.send("murmur", buf)
    catch
      case _: IllegalStateException =>


package mixin:
  import net.minecraft.client.MinecraftClient
  import net.minecraft.client.gui.screen.ChatScreen
  import net.minecraft.client.gui.widget.TextFieldWidget
  import org.eu.net.pool.hexxytounge
  import org.spongepowered.asm.mixin.Mixin
  import org.spongepowered.asm.mixin.gen.Accessor
  import org.spongepowered.asm.mixin.injection.{At, Inject}
  import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

  import scala.reflect.ClassTag
  @Mixin(Array(classOf[MinecraftClient]))
  private[mixin] class MinecraftClientMixin:
    @Inject(at = Array(new At(value = "HEAD")), method = Array("tick"))
    def tick(using CallbackInfo) =
      hexxytounge.tick()
  @Mixin(Array(classOf[ChatScreen]))
  trait ChatScreenAccess:
    @Accessor("chatField") val chatField: TextFieldWidget