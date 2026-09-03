package org.eu.net.pool.hexic.hexcompat

import at.petrak.hexcasting.api.casting.eval.vm.{CastingImage, ContinuationFrame}
import at.petrak.hexcasting.api.casting.iota.{Iota, IotaType}
import at.petrak.hexcasting.api.pigment.FrozenPigment
import net.minecraft.nbt.{CompoundTag, NbtOps, Tag}
import net.minecraft.network.chat.Component

def serializeIota(iota: Iota): Tag =
  IotaType.TYPED_CODEC.encodeStart(NbtOps.INSTANCE, iota).getOrThrow

def deserializeIota(tag: Tag): Iota | Null =
  val parsed = IotaType.TYPED_CODEC.parse(NbtOps.INSTANCE, tag).result()
  if parsed.isPresent then parsed.get() else null

def displayIotaTag(tag: Tag): Component =
  Option(deserializeIota(tag)).fold(Component.literal(tag.toString))(_.display())

def frozenPigmentFromNbt(tag: Tag): FrozenPigment =
  FrozenPigment.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow

def frozenPigmentToNbt(pigment: FrozenPigment): Tag =
  FrozenPigment.CODEC.encodeStart(NbtOps.INSTANCE, pigment).getOrThrow

def castingImageFromNbt(tag: CompoundTag): CastingImage =
  CastingImage.getCODEC.parse(NbtOps.INSTANCE, tag).getOrThrow

def castingImageToNbt(image: CastingImage): Tag =
  CastingImage.getCODEC.encodeStart(NbtOps.INSTANCE, image).getOrThrow

def continuationFrameFromNbt(tag: CompoundTag): ContinuationFrame =
  ContinuationFrame.Type.getTYPED_CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow

def continuationFrameToNbt(frame: ContinuationFrame): Tag =
  ContinuationFrame.Type.getTYPED_CODEC.encodeStart(NbtOps.INSTANCE, frame).getOrThrow
