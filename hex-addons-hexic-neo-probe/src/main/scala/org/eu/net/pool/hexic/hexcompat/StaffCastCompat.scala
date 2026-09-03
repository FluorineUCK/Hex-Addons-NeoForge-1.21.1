package org.eu.net.pool
package hexic.hexcompat

import at.petrak.hexcasting.api.casting.eval.{ExecutionClientView, ResolvedPatternType}
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.common.msgs.{MsgNewSpellPatternC2S, MsgNewSpellPatternS2C}
import at.petrak.hexcasting.xplat.IXplatAbstractions
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.Items
import org.eu.net.pool.hexic.{Pen, PenAccess}

import scala.annotation.static

/**
 * Compatibility seam for the two Hexic inputs that intercept staff drawing.
 *
 * The Mixin only chooses the injection point. Keeping the behavior here makes
 * it possible to exercise the exact production implementation from the
 * dedicated-server regression probe without duplicating it in test code.
 */
final class StaffCastCompat private ()

object StaffCastCompat:
  @static
  def intercept(sender: ServerPlayer, msg: MsgNewSpellPatternC2S, vm: CastingVM): Boolean =
    val ownStack = sender.getItemInHand(vm.getEnv.getCastingHand)
    ownStack.getItem match
      case pen: Pen =>
        vm.getEnv match
          case access: PenAccess =>
            access.getPen(pen.color).add(msg.pattern)
            finishCapture(
              sender,
              msg,
              vm,
              ResolvedPatternType.valueOf(s"HEXIC$$PEN_WITH_COLOR_${pen.color.getName}")
            )
            true
          case _ =>
            false
      case _ =>
        val offhandStack = sender.getItemInHand(vm.getEnv.getOtherHand)
        if offhandStack.is(Items.ECHO_SHARD) then
          EchoShardCompat.appendPattern(offhandStack, msg.pattern)
          sender.playSound(SoundEvents.SCULK_CLICKING, 1.0f, 1.0f)
          finishCapture(
            sender,
            msg,
            vm,
            ResolvedPatternType.valueOf("HEXIC$ECHO_SHARD_ABSORBED")
          )
          true
        else
          false

  private def finishCapture(
    sender: ServerPlayer,
    msg: MsgNewSpellPatternC2S,
    vm: CastingVM,
    resolution: ResolvedPatternType
  ): Unit =
    // pre-2 no longer exposes CastingVM.generateDescs(). Its normal execution
    // path sends the current stack and ravenmind directly, so do the same.
    val image = vm.getImage
    val view = ExecutionClientView(
      false,
      resolution,
      image.getStack,
      image.ravenmind().orElse(null)
    )
    IXplatAbstractions.INSTANCE.setStaffcastImage(sender, image.withOverriddenUsedOps(0L))

    val resolvedPatterns = msg.resolvedPatterns
    if !resolvedPatterns.isEmpty then
      resolvedPatterns.get(resolvedPatterns.size - 1).setType(resolution)
    IXplatAbstractions.INSTANCE.setPatterns(sender, resolvedPatterns)
    IXplatAbstractions.INSTANCE.sendPacketToPlayer(
      sender,
      MsgNewSpellPatternS2C(view, resolvedPatterns.size - 1)
    )
