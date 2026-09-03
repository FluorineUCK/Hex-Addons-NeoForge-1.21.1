package org.eu.net.pool.hexic.hexcompat

import at.petrak.hexcasting.api.casting.iota.{Iota, IotaType}
import com.mojang.serialization.{Codec, MapCodec}
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.{ByteBufCodecs, StreamCodec}

case class StringIota(private val value: String) extends Iota(() => StringIota.TYPE):
  def getString: String = value

  override def isTruthy: Boolean = value.nonEmpty

  override def toleratesOther(other: Iota): Boolean =
    other match
      case s: StringIota => value == s.value
      case _ => false

  override def display(): Component = Component.literal(value)

  override def hashCode(): Int = value.hashCode

object StringIota:
  val TYPE: IotaType[StringIota] = new IotaType[StringIota]:
    override def codec(): MapCodec[StringIota] =
      Codec.STRING.xmap[StringIota](StringIota(_), _.getString).fieldOf("value")

    override def streamCodec(): StreamCodec[RegistryFriendlyByteBuf, StringIota] =
      ByteBufCodecs.STRING_UTF8
        .asInstanceOf[StreamCodec[RegistryFriendlyByteBuf, String]]
        .map(StringIota(_), _.getString)

    override def color(): Int = 0x7fc7ff

  def make(value: String): StringIota = StringIota(value)
