package org.eu.net.pool.hexic.hexcompat

import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.eval.{CastingEnvironment, OperationResult}
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.mishaps.{MishapBadOffhandItem, MishapInvalidIota, MishapNotEnoughArgs}
import at.petrak.hexcasting.api.pigment.FrozenPigment
import at.petrak.hexcasting.common.items.magic.ItemPackagedHex
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.eu.net.pool.hexic.{*, given}
import org.eu.net.pool.hexic.hexcompat.frozenPigmentToNbt
import org.eu.net.pool.phlib.{Patterns, *, given}
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters.*
import scala.util.Try

/**
 * Optional Hexical integration.
 *
 * Keep Hexical classes out of Hexic's always-loaded class descriptors: Hexical is
 * optional, and directly linking PigmentIota made dedicated servers without
 * Hexical fail during class verification.  The only value crossing this boundary
 * is Hex Casting's public FrozenPigment API.
 */
object HexicalCompat:
  private val log = LoggerFactory.getLogger("Hexic/HexicalCompat")
  private val PigmentIotaClassName = "miyucomics.hexical.features.pigments.PigmentIota"

  private lazy val pigmentGetter =
    val cls = Class.forName(PigmentIotaClassName)
    cls.getMethod("getPigment")

  private def pigmentFrom(iota: Iota): Option[FrozenPigment] =
    if iota.getClass.getName != PigmentIotaClassName then None
    else
      Try(pigmentGetter.invoke(iota).asInstanceOf[FrozenPigment]).recover:
        case error =>
          log.error("Failed to read Hexical PigmentIota through its public getter", error)
          null
      .toOption
      .filter(_ != null)

  def registerDyeOffpaw(): Unit =
    Patterns.register("dye_offpaw", w"eqdeeqdweeqddqdwwdew"):
      Patterns.mkAction: (img, cont) =>
        val stack = img.getStack.asScala
        val input = stack.lastOption.getOrElse(throw MishapNotEnoughArgs(1, 0))
        val pigment = pigmentFrom(input).getOrElse:
          throw MishapInvalidIota.ofType(input, 0, "pigment")

        val env = summon[CastingEnvironment]
        val held = Option(env.getHeldItemToOperateOn: candidate =>
          candidate != null &&
            !candidate.isEmpty &&
            (candidate.getItem.isInstanceOf[ItemPackagedHex] ||
              candidate.getItem.isInstanceOf[Stringworm])
        ).getOrElse:
          throw MishapBadOffhandItem(
            null,
            Component.translatable("text.hexic.pigment_holder_item")
          )

        OperationResult(
          img.withStack(_.init),
          Seq(
            OperatorSideEffect.AttemptSpell(
              new RenderedSpell:
                override def cast(castEnv: CastingEnvironment): Unit =
                  held.stack.getItem match
                    case packaged: ItemPackagedHex =>
                      PigmentHolderItem.packagedHex(packaged).setPigment(held.stack)(pigment)
                    case _: Stringworm =>
                      held.stack.setItem(dyedStringworm)
                      held.stack.getOrCreateNbt().put("pigment", frozenPigmentToNbt(pigment))
                    case _ =>
                      // The held-item predicate above makes this unreachable.
                      throw MishapBadOffhandItem(
                        held.stack,
                        Component.translatable("text.hexic.pigment_holder_item")
                      )

                override def cast(castEnv: CastingEnvironment, image: at.petrak.hexcasting.api.casting.eval.vm.CastingImage) =
                  cast(castEnv)
                  image,
              true,
              true
            )
          ),
          cont,
          HexEvalSounds.SPELL.get()
        )

  def registerHopperEndpoints(): Unit =
    HexicalHopperCompat.register()
