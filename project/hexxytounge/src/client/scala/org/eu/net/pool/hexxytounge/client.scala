package org.eu.net.pool.hexxytounge

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.ChatScreen

import scala.annotation.experimental

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
  import com.llamalad7.mixinextras.injector.ModifyExpressionValue
  import com.llamalad7.mixinextras.sugar.Local
  import net.minecraft.client.MinecraftClient
  import net.minecraft.client.gui.hud.{ChatHud, ChatHudLine, MessageIndicator}
  import net.minecraft.client.gui.screen.ChatScreen
  import net.minecraft.client.gui.widget.TextFieldWidget
  import org.eu.net.pool.hexxytounge
  import org.eu.net.pool.phlib.given
  import org.spongepowered.asm.mixin.{Mixin, Unique}
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
  @Mixin(Array(classOf[ChatHud]))
  @experimental
  private[mixin] class ChatHudMixin:
    @Unique private def patch(original: java.util.List[ChatHudLine.Visible], currentTick: Int): java.util.List[ChatHudLine.Visible] = {
      val p = MinecraftClient.getInstance.player
      if p != null then
        val lines = p.component[RevealComponent].lines.map(line => new ChatHudLine.Visible(currentTick, line.asOrderedText, MessageIndicator.system, true))
        println(lines)
        if lines.nonEmpty then
          lines.reverse ++ original
        else
          original
      else
        original
    }

    @ModifyExpressionValue(method = Array("render"), at = Array(new At(value = "FIELD", target = "Lnet/minecraft/client/gui/hud/ChatHud;visibleMessages:Ljava/util/List;")))
    def modifyDrawnMessages(original: java.util.List[ChatHudLine.Visible], @Local(ordinal = 0, argsOnly = true) currentTick: Int) = patch(original, currentTick)
    @ModifyExpressionValue(method = Array("getTextStyleAt", "getIndicatorAt"), at = Array(new At(value = "FIELD", target = "Lnet/minecraft/client/gui/hud/ChatHud;visibleMessages:Ljava/util/List;")))
    def modifyLookedUpMessages(original: java.util.List[ChatHudLine.Visible]) = patch(original, 0)
