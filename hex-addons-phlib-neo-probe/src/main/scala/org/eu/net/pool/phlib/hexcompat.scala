package org.eu.net.pool
package phlib

import at.petrak.hexcasting.api.casting.iota.{Iota, IotaType}
import com.mojang.serialization.Codec
import net.minecraft.ChatFormatting
import net.minecraft.core.{Holder, MappedRegistry, Registry as MojRegistry}
import net.minecraft.commands.{CommandBuildContext, CommandSourceStack}
import net.minecraft.commands.arguments.{NbtTagArgument, ResourceArgument}
import net.minecraft.nbt.*
import net.minecraft.nbt.{Tag as MojTag}
import net.minecraft.network.chat.{Component, ComponentSerialization, MutableComponent, Style}
import net.minecraft.resources.{ResourceKey as MojResourceKey, ResourceLocation}
import net.minecraft.server.level.{ServerLevel, ServerPlayer}
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType

type Identifier = ResourceLocation
object Identifier:
  def of(namespace: String, path: String): Identifier = ResourceLocation.fromNamespaceAndPath(namespace, path)
  def tryParse(value: String): Identifier | Null = ResourceLocation.tryParse(value)

type Registry[T] = MojRegistry[T]
object Registry:
  def register[V, T <: V](registry: MojRegistry[V], id: Identifier, value: T): T =
    net.minecraft.core.Registry.register(registry, id, value)
  def register[V, T <: V](registry: MojRegistry[V], key: MojResourceKey[V], value: T): T =
    net.minecraft.core.Registry.register(registry, key, value)

type RegistryKey[T] = MojResourceKey[T]
object RegistryKey:
  def ofRegistry[T](id: Identifier): MojResourceKey[MojRegistry[T]] = MojResourceKey.createRegistryKey[T](id)
  def of[T](registry: MojResourceKey[_ <: MojRegistry[T]], id: Identifier): MojResourceKey[T] =
    MojResourceKey.create(registry, id)

type RegistryEntry[T] = Holder[T]
type SimpleRegistry[T] = MappedRegistry[T]
object SimpleRegistry:
  def apply[T](key: MojResourceKey[_ <: MojRegistry[T]], lifecycle: com.mojang.serialization.Lifecycle): MappedRegistry[T] =
    new MappedRegistry[T](key, lifecycle)

type NbtElement = MojTag
object NbtElement:
  val COMPOUND_TYPE: Int = MojTag.TAG_COMPOUND

type NbtCompound = CompoundTag
object NbtCompound:
  val TYPE: TagType[CompoundTag] = CompoundTag.TYPE
  def apply(): CompoundTag = new CompoundTag()

type NbtList = ListTag
object NbtList:
  val TYPE: TagType[ListTag] = ListTag.TYPE
  def apply(): ListTag = new ListTag()

type NbtString = StringTag
object NbtString:
  val TYPE: TagType[StringTag] = StringTag.TYPE
  def of(value: String): StringTag = StringTag.valueOf(value)

type NbtByte = ByteTag
object NbtByte:
  val TYPE: TagType[ByteTag] = ByteTag.TYPE

type NbtShort = ShortTag
object NbtShort:
  val TYPE: TagType[ShortTag] = ShortTag.TYPE

type NbtInt = IntTag
object NbtInt:
  val TYPE: TagType[IntTag] = IntTag.TYPE

type NbtLong = LongTag
object NbtLong:
  val TYPE: TagType[LongTag] = LongTag.TYPE

type NbtFloat = FloatTag
object NbtFloat:
  val TYPE: TagType[FloatTag] = FloatTag.TYPE

type NbtDouble = DoubleTag
object NbtDouble:
  val TYPE: TagType[DoubleTag] = DoubleTag.TYPE

type NbtByteArray = ByteArrayTag
object NbtByteArray:
  val TYPE: TagType[ByteArrayTag] = ByteArrayTag.TYPE

type NbtIntArray = IntArrayTag
object NbtIntArray:
  val TYPE: TagType[IntArrayTag] = IntArrayTag.TYPE

type NbtLongArray = LongArrayTag
object NbtLongArray:
  val TYPE: TagType[LongArrayTag] = LongArrayTag.TYPE

type NbtEnd = EndTag
object NbtEnd:
  val TYPE: TagType[EndTag] = EndTag.TYPE

type NbtType[T <: NbtElement] = TagType[T]
type AbstractNbtNumber = NumericTag

type ServerWorld = ServerLevel
type ServerPlayerEntity = ServerPlayer

type Hand = InteractionHand
object Hand:
  val MAIN_HAND: InteractionHand = InteractionHand.MAIN_HAND
  val OFF_HAND: InteractionHand = InteractionHand.OFF_HAND

type Arm = HumanoidArm
object Arm:
  val LEFT: HumanoidArm = HumanoidArm.LEFT
  val RIGHT: HumanoidArm = HumanoidArm.RIGHT

type Text = Component
type MutableText = MutableComponent
object Text:
  def literal(value: String): MutableComponent = Component.literal(value)
  def translatable(key: String, args: Any*): MutableComponent =
    Component.translatable(key, args.map(_.asInstanceOf[Object])*)
  def empty(): MutableComponent = Component.empty()

type Formatting = ChatFormatting
object Formatting:
  val RED: ChatFormatting = ChatFormatting.RED
  val GREEN: ChatFormatting = ChatFormatting.GREEN
  val GRAY: ChatFormatting = ChatFormatting.GRAY

object Codecs:
  val TEXT: Codec[Component] = ComponentSerialization.CODEC

object NbtOps:
  val INSTANCE: net.minecraft.nbt.NbtOps = net.minecraft.nbt.NbtOps.INSTANCE

extension (tag: StringTag)
  def asString: String = tag.getAsString

extension (tag: NumericTag)
  def doubleValue: Double = tag.getAsDouble
  def intValue: Int = tag.getAsInt

extension (component: MutableComponent)
  def formatted(formatting: ChatFormatting): MutableComponent = component.withStyle(formatting)
  def styled(f: Style => Style): MutableComponent =
    component.withStyle(new java.util.function.UnaryOperator[Style]:
      override def apply(style: Style): Style = f(style)
    )

extension (tag: CompoundTag)
  def apply(key: String): Tag | Null = tag.get(key)
  def update(key: String, value: Tag | Null): Unit =
    if value != null then tag.put(key, value)

def serialize(iota: Iota): CompoundTag =
  IotaType.TYPED_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, iota)
    .result()
    .orElseGet(() => new CompoundTag())
    .asInstanceOf[CompoundTag]

def deserialize(tag: CompoundTag, world: ServerLevel): Iota | Null =
  IotaType.TYPED_CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).result().orElse(null)

def getDisplay(tag: CompoundTag): Component =
  val decoded = IotaType.TYPED_CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).result().orElse(null)
  if decoded == null then IotaType.brokenIota() else decoded.display()

final class CommandException(message: String) extends RuntimeException(message)

object NbtElementArgumentType:
  def nbtElement(): NbtTagArgument = NbtTagArgument.nbtTag()

object RegistryEntryArgumentType:
  def registryEntry[T](context: CommandBuildContext, key: MojResourceKey[_ <: MojRegistry[T]]): ResourceArgument[T] =
    ResourceArgument.resource(context, key)

extension (source: CommandSourceStack)
  def getWorld: ServerLevel = source.getLevel
  def hasPermissionLevel(level: Int): Boolean = source.hasPermission(level)
  def sendFeedback(message: () => Component, broadcast: Boolean): Unit =
    source.sendSuccess(new java.util.function.Supplier[Component]:
      override def get(): Component = message()
    , broadcast)
  def sendMessage(message: Component): Unit = source.sendSystemMessage(message)
