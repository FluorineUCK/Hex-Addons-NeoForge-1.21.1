package org.eu.net.pool.hexic.hexcompat

import net.minecraft.nbt.{NbtOps, StringTag, Tag}
import net.minecraft.network.chat.{Component, ComponentSerialization}

/** Keeps Hexic reveal lines as structured chat components in persistent NBT. */
object ComponentTagCompat:
  def encode(component: Component): Tag =
    ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, component).getOrThrow

  def decode(tag: Tag | Null): Component =
    if tag == null then
      Component.empty()
    else
      val decoded = ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, tag).result()
      if decoded.isPresent then
        decoded.get()
      else
        // Builds made before this adapter stored only Component#getString in
        // a StringTag. Keep those worlds readable while writing the complete
        // component structure from now on.
        tag match
          case string: StringTag => Component.literal(string.getAsString)
          case _ =>
            throw IllegalArgumentException(s"Invalid serialized chat component: $tag")
