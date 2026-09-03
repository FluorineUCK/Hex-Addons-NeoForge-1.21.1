package org.eu.net.pool
package hexic

import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic
import at.petrak.hexcasting.api.casting.circles.BlockEntityAbstractImpetus
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv
import at.petrak.hexcasting.api.casting.iota.{DoubleIota, EntityIota, Iota, IotaType, ListIota, NullIota, Vec3Iota}
import at.petrak.hexcasting.api.casting.mishaps.{MishapInvalidIota, MishapOthersName}
import com.mojang.datafixers.util.{Either as DFUEither}
import com.mojang.serialization.{Codec, DataResult, Dynamic, DynamicOps, MapCodec, MapLike, RecordBuilder}
import net.minecraft.SharedConstants
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.{BuiltInRegistries, Registries}
import net.minecraft.nbt.{CompoundTag, NbtOps}
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.{ByteBufCodecs, StreamCodec}
import net.minecraft.resources.{ResourceKey, ResourceLocation}
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.datafix.DataFixers
import net.minecraft.util.datafix.fixes.References
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.{Item, ItemStack, Items}
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.AbstractFurnaceBlock
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.world.level.material.{Fluid, Fluids}
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.FluidType
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.items.IItemHandler
import org.eu.net.pool.hexic.mixin.AbstractFurnaceBlockEntityAccess
import org.eu.net.pool.hexic.hexcompat.HexicViewApi
import org.eu.net.pool.phlib.{*, given}
import ram.talia.hexal.api.casting.iota.MoteIota
import ram.talia.hexal.api.mediafieditems.MediafiedItemManager
import ram.talia.moreiotas.api.casting.iota.{ItemStackIota, ItemTypeIota}

import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.util.Try

def initViews(): Unit =
  iotaTypeRegistry("transfer_type") = VariantIota.TYPE
  iotaTypeRegistry("inventory_view") = BoxedView.TYPE

  hexXplat.getArithmeticRegistry("view") = arith("view",
    Arithmetic.ADD -> ((left: BoxedView.Instance, right: BoxedView.Instance) => Seq(BoxedView.Instance(BoxedView.SumView(Seq(left.view, right.view)))))
  )

  Patterns.register("findview", e"addaadewewedaaddqwawqddaadewewedaaddqwawdeeweee"):
    Patterns.mkConstAction(1):
      case Seq(iota) =>
        val env = summon[CastingEnvironment]
        Option(HexicViewApi.resolve(env, iota)) match
          case Some(BoxedView.Instance(view)) => Seq(BoxedView.Instance(view))
          case Some(other) =>
            throw new IllegalStateException(
              s"Hexic view resolver returned ${other.getClass.getName}; expected BoxedView.Instance"
            )
          case None =>
            iota match
              case v: Vec3Iota =>
                val pos = BlockPos.containing(v.getVec3)
                env.assertPosInRangeForEditing(pos)
                Seq(BoxedView.Instance(BoxedView.BlockView(env.getWorld.dimension().location(), pos)))
              case e: EntityIota =>
                val entity = Option(e.getEntity(env.getWorld)).orElse:
                  val server = env.getWorld.getServer
                  Option(server.getPlayerList.getPlayer(e.getEntityId)).map(_.asInstanceOf[Entity]).orElse:
                    server.getAllLevels.asScala.iterator
                      .flatMap(level => Option(level.getEntity(e.getEntityId)))
                      .toSeq
                      .headOption
                entity match
                  case Some(player: Player) =>
                    env match
                      case playerEnv: PlayerBasedCastEnv if playerEnv.getCaster != player =>
                        throw MishapOthersName(player)
                      case _ =>
                  case _ =>
                Seq(BoxedView.Instance(BoxedView.EntityView(entity.map(_.getUUID).getOrElse(e.getEntityId))))
              case other =>
                throw MishapInvalidIota.ofType(other, 0, "hexic:view")

  Patterns.register("conceptavailable", sw"wedwqwdewwaqaa"):
    Patterns.mkConstAction(2):
      case Seq(BoxedView.Instance(view), variant: VariantIota) =>
        Seq(DoubleIota(variant.toConcepts(view.available(summon[CastingEnvironment].getWorld.getServer, variant))))
      case Seq(_: BoxedView.Instance, other) =>
        throw MishapInvalidIota.ofType(other, 0, "hexic:variant")
      case Seq(other, _) =>
        throw MishapInvalidIota.ofType(other, 1, "hexic:view")

  Patterns.register("conceptremaining", sw"wedwqwdewadedd"):
    Patterns.mkConstAction(2):
      case Seq(BoxedView.Instance(view), variant: VariantIota) =>
        Seq(DoubleIota(variant.toConcepts(view.remaining(summon[CastingEnvironment].getWorld.getServer, variant))))
      case Seq(_: BoxedView.Instance, other) =>
        throw MishapInvalidIota.ofType(other, 0, "hexic:variant")
      case Seq(other, _) =>
        throw MishapInvalidIota.ofType(other, 1, "hexic:view")

  Patterns.register("moveconcept", se"wawdwawqdewewedqwawdwaw"):
    Patterns.mkConstAction(4):
      case Seq(BoxedView.Instance(from), BoxedView.Instance(into), variant: VariantIota, amountIota: DoubleIota) =>
        val server = summon[CastingEnvironment].getWorld.getServer
        val amount = variant.fromConcepts(amountIota.getDouble)
        val extracted = from.extract(server, variant, amount, simulate = true)
        val inserted = into.insert(server, variant, extracted, simulate = true)
        val moved = Math.min(extracted, inserted)
        if moved > 0 then
          val actuallyExtracted = from.extract(server, variant, moved, simulate = false)
          val actuallyInserted = into.insert(server, variant, actuallyExtracted, simulate = false)
          if actuallyInserted < actuallyExtracted then
            from.insert(server, variant, actuallyExtracted - actuallyInserted, simulate = false)
          Seq(DoubleIota(variant.toConcepts(actuallyInserted)))
        else
          Seq(DoubleIota(0.0))
      case Seq(_: BoxedView.Instance, _: BoxedView.Instance, _: VariantIota, other) =>
        throw MishapInvalidIota.of(other, 0, "number")
      case Seq(_: BoxedView.Instance, _: BoxedView.Instance, other, _) =>
        throw MishapInvalidIota.ofType(other, 1, "hexic:variant")
      case Seq(_: BoxedView.Instance, other, _, _) =>
        throw MishapInvalidIota.ofType(other, 2, "hexic:view")
      case Seq(other, _, _, _) =>
        throw MishapInvalidIota.ofType(other, 3, "hexic:view")

  Patterns.register("moveentity", se"edeeewawdweaaddaqwqwqaddaaewdwawewdqd"):
    Patterns.mkConstAction(3):
      case Seq(BoxedView.Instance(from), BoxedView.Instance(into), _: DoubleIota) =>
        val server = summon[CastingEnvironment].getWorld.getServer
        val moved = from.entities(server).count(into.teleportEntity(server, _))
        Seq(DoubleIota(moved.toDouble))
      case Seq(_: BoxedView.Instance, _: BoxedView.Instance, other) =>
        throw MishapInvalidIota.of(other, 0, "number")
      case Seq(_: BoxedView.Instance, other, _) =>
        throw MishapInvalidIota.ofType(other, 1, "hexic:view")
      case Seq(other, _, _) =>
        throw MishapInvalidIota.ofType(other, 2, "hexic:view")

  Patterns.register("thinkaboutit", e"awqawewaqw"):
    Patterns.mkConstAction(1):
      case Seq(BoxedView.Instance(view)) =>
        Seq(ListIota(view.contents(summon[CastingEnvironment].getWorld.getServer).asJava))
      case Seq(variant: VariantIota) =>
        Seq(variant)
      case Seq(i: ItemStackIota) =>
        val stack = i.getItemStack
        if stack == null || stack.isEmpty || stack.getItem == Items.AIR then Seq(NullIota())
        else Seq(VariantIota.fromStack(stack))
      case Seq(i: ItemTypeIota) =>
        val item = i.getItem
        if item == null || item == Items.AIR then Seq(NullIota())
        else Seq(VariantIota.ofItem(item))
      case Seq(v: Vec3Iota) =>
        val env = summon[CastingEnvironment]
        val pos = BlockPos.containing(v.getVec3)
        env.assertPosInRange(pos)
        Seq(VariantIota.fromBlockAt(env.getWorld, pos))
      case Seq(e: EntityIota) =>
        val env = summon[CastingEnvironment]
        e.getEntity(env.getWorld) match
          case item: ItemEntity if !item.getItem.isEmpty => Seq(VariantIota.fromStack(item.getItem))
          case frame: ItemFrame if !frame.getItem.isEmpty => Seq(VariantIota.fromStack(frame.getItem))
          case _ => throw MishapInvalidIota.ofType(e, 0, "hexic:not_eldritch_horror")
      case Seq(mote: MoteIota) if MediafiedItemManager.contains(mote.getItemIndex) =>
        val item = mote.getItem
        if item == null || item == Items.AIR then Seq(NullIota())
        else
          val stack = ItemStack(item)
          stack.applyComponents(mote.getComponents)
          Seq(VariantIota.fromStack(stack))
      case Seq(other) =>
        throw MishapInvalidIota.ofType(other, 0, "hexic:not_eldritch_horror")

final class VariantIota private (
  val kind: String,
  private val itemPrototype: ItemStack,
  private val fluidPrototype: FluidStack
) extends Iota(() => VariantIota.TYPE):
  def valueId: ResourceLocation =
    if isItem then BuiltInRegistries.ITEM.getKey(item)
    else if isFluid then BuiltInRegistries.FLUID.getKey(fluid)
    else if isMedia then VariantIota.MediaId
    else VariantIota.HeatId

  def itemId: ResourceLocation = valueId
  def isItem: Boolean = kind == VariantIota.ItemKind
  def isFluid: Boolean = kind == VariantIota.FluidKind
  def isMedia: Boolean = kind == VariantIota.MediaKind
  def isHeat: Boolean = kind == VariantIota.HeatKind
  def item: Item = if isItem && !itemPrototype.isEmpty then itemPrototype.getItem else Items.AIR
  def fluid: Fluid = if isFluid && !fluidPrototype.isEmpty then fluidPrototype.getFluid else Fluids.EMPTY
  def stackPrototype: ItemStack = if isItem then itemPrototype.copy() else ItemStack.EMPTY
  def fluidStackPrototype: FluidStack = if isFluid then fluidPrototype.copy() else FluidStack.EMPTY

  def toStack(count: Int): ItemStack =
    if !isItem || itemPrototype.isEmpty || count <= 0 then ItemStack.EMPTY
    else itemPrototype.copyWithCount(count)

  def toFluidStack(amount: Int): FluidStack =
    if !isFluid || fluidPrototype.isEmpty || amount <= 0 then FluidStack.EMPTY
    else fluidPrototype.copyWithAmount(amount)

  def matchesItem(stack: ItemStack): Boolean =
    isItem && stack != null && !stack.isEmpty && ItemStack.isSameItemSameComponents(itemPrototype, stack)

  def matchesFluid(stack: FluidStack): Boolean =
    isFluid && stack != null && !stack.isEmpty && FluidStack.isSameFluidSameComponents(fluidPrototype, stack)

  def unitsPerConcept: Long =
    if isFluid then FluidType.BUCKET_VOLUME.toLong
    else if isMedia then 10000L
    else if isHeat then 20L
    else 1L

  def toConcepts(storageAmount: Long): Double =
    storageAmount.toDouble / unitsPerConcept.toDouble

  def fromConcepts(concepts: Double): Long =
    if concepts.isNaN || concepts <= 0.0 then 0L
    else
      val storageAmount = concepts * unitsPerConcept.toDouble
      if storageAmount.isInfinite || storageAmount >= Long.MaxValue.toDouble then Long.MaxValue
      else storageAmount.toLong

  // Transfer variants are values even when a malformed legacy payload resolves to air/empty.
  override def isTruthy: Boolean = true

  override def toleratesOther(other: Iota): Boolean =
    other match
      case variant: VariantIota if kind != variant.kind => false
      case variant: VariantIota if isItem => ItemStack.isSameItemSameComponents(itemPrototype, variant.itemPrototype)
      case variant: VariantIota if isFluid => FluidStack.isSameFluidSameComponents(fluidPrototype, variant.fluidPrototype)
      case _: VariantIota => true
      case _ => false

  override def equals(other: Any): Boolean =
    other match
      case variant: VariantIota => toleratesOther(variant)
      case _ => false

  override def hashCode(): Int =
    val payloadHash =
      if isItem then ItemStack.hashItemAndComponents(itemPrototype)
      else if isFluid then FluidStack.hashFluidAndComponents(fluidPrototype)
      else 0
    31 * kind.hashCode + payloadHash

  override def display(): Component =
    if isHeat then Component.literal("⌠Heat⌡").withColor(0xe08355)
    else if isMedia then Component.literal("⌠Media⌡").withColor(0x74b3f2)
    else if isFluid then Component.literal("⌠").append(fluidPrototype.getHoverName).append("⌡").withColor(0x3c5e34)
    else Component.literal("⌠").append(itemPrototype.getHoverName).append("⌡").withColor(0x7c7145)

object VariantIota:
  private val log = org.slf4j.LoggerFactory.getLogger("hexic")
  val ItemKind = "item"
  val FluidKind = "fluid"
  val MediaKind = "media"
  val HeatKind = "heat"
  private val AirId = ResourceLocation.fromNamespaceAndPath("minecraft", "air")
  private val EmptyFluidId = ResourceLocation.fromNamespaceAndPath("minecraft", "empty")
  private[hexic] val MediaId = ResourceLocation.fromNamespaceAndPath("hexic", "media")
  private[hexic] val HeatId = ResourceLocation.fromNamespaceAndPath("hexic", "heat")

  private def normalizeItem(stack: ItemStack): ItemStack =
    if stack == null || stack.isEmpty || stack.getItem == Items.AIR then ItemStack.EMPTY
    else stack.copyWithCount(1)

  private def normalizeFluid(stack: FluidStack): FluidStack =
    if stack == null || stack.isEmpty || stack.getFluid == Fluids.EMPTY then FluidStack.EMPTY
    else stack.copyWithAmount(1)

  private def make(kind: String, item: ItemStack = ItemStack.EMPTY, fluid: FluidStack = FluidStack.EMPTY): VariantIota =
    kind match
      case ItemKind => new VariantIota(ItemKind, normalizeItem(item), FluidStack.EMPTY)
      case FluidKind => new VariantIota(FluidKind, ItemStack.EMPTY, normalizeFluid(fluid))
      case MediaKind => new VariantIota(MediaKind, ItemStack.EMPTY, FluidStack.EMPTY)
      case HeatKind => new VariantIota(HeatKind, ItemStack.EMPTY, FluidStack.EMPTY)
      case _ => new VariantIota(ItemKind, ItemStack.EMPTY, FluidStack.EMPTY)

  private def safeId(raw: String, fallback: ResourceLocation): ResourceLocation =
    Option(ResourceLocation.tryParse(raw)).getOrElse(fallback)

  private def parsePayload(payload: String): VariantIota =
    payload.split("\\|", 2).toList match
      case HeatKind :: _ => ofHeat
      case MediaKind :: _ => ofMedia
      case FluidKind :: id :: Nil => ofFluid(BuiltInRegistries.FLUID.get(safeId(id, EmptyFluidId)))
      case ItemKind :: id :: Nil => ofItem(BuiltInRegistries.ITEM.get(safeId(id, AirId)))
      case _ => ofItem(BuiltInRegistries.ITEM.get(safeId(payload, AirId)))

  private def writePayload(variant: VariantIota): String =
    s"${variant.kind}|${variant.valueId}"

  private val CurrentCodec: MapCodec[VariantIota] =
    RecordCodecBuilder.mapCodec[VariantIota]: instance =>
      instance.group(
        Codec.STRING.fieldOf("kind").forGetter((variant: VariantIota) => variant.kind),
        ItemStack.OPTIONAL_CODEC.optionalFieldOf("stack", ItemStack.EMPTY).forGetter((variant: VariantIota) => variant.itemPrototype),
        FluidStack.OPTIONAL_CODEC.optionalFieldOf("fluid", FluidStack.EMPTY).forGetter((variant: VariantIota) => variant.fluidPrototype)
      ).apply(instance, (kind: String, item: ItemStack, fluid: FluidStack) => make(kind, item, fluid))

  // The first NeoForge probe encoded "kind|id" in a single "item" string. Keep
  // that reader so worlds created during the port do not lose their stored iotas.
  private val ProbeLegacyCodec: MapCodec[VariantIota] =
    Codec.STRING.xmap[VariantIota](parsePayload, writePayload).fieldOf("item")

  private val ProbeCompatibleCodec: MapCodec[VariantIota] =
    Codec
      .mapEither(CurrentCodec, ProbeLegacyCodec)
      .xmap[VariantIota](
        (value: DFUEither[VariantIota, VariantIota]) =>
          value.map((current: VariantIota) => current, (legacy: VariantIota) => legacy),
        (variant: VariantIota) => DFUEither.left[VariantIota, VariantIota](variant)
      )

  private val CompatibleCodec: MapCodec[VariantIota] = new MapCodec[VariantIota]:
    override def keys[T](ops: DynamicOps[T]): java.util.stream.Stream[T] =
      java.util.stream.Stream.concat(
        ProbeCompatibleCodec.keys(ops),
        java.util.stream.Stream.of(
          ops.createString("type"),
          ops.createString("item"),
          ops.createString("fluid"),
          ops.createString("tag")
        )
      ).distinct()

    override def encode[T](
      input: VariantIota,
      ops: DynamicOps[T],
      prefix: RecordBuilder[T]
    ): RecordBuilder[T] =
      ProbeCompatibleCodec.encode(input, ops, prefix)

    override def decode[T](ops: DynamicOps[T], input: MapLike[T]): DataResult[VariantIota] =
      // Fabric's 1.20 ItemVariant format also has an "item" string, so the
      // earliest NeoForge probe codec would otherwise accept it first and
      // silently discard the old "tag". The explicit "type" discriminator is
      // authoritative for original Hexic data.
      val legacyKind = Option(input.get("type"))
        .flatMap(value => Option(ops.getStringValue(value).result().orElse(null)))
        .map(raw => Option(ResourceLocation.tryParse(raw)).map(_.getPath).getOrElse(raw))
      if legacyKind.exists(Set(ItemKind, FluidKind, MediaKind, HeatKind)) then
        decodeFabricLegacy(ops, input) match
          case Right(variant) => DataResult.success(variant)
          case Left(error) => DataResult.error(() => error)
      else
        ProbeCompatibleCodec.decode(ops, input)

  val TYPE: IotaType[VariantIota] = new IotaType[VariantIota]:
    override def codec(): MapCodec[VariantIota] = CompatibleCodec

    override def streamCodec(): StreamCodec[RegistryFriendlyByteBuf, VariantIota] =
      ByteBufCodecs.fromCodecWithRegistries(CompatibleCodec.codec())

    override def color(): Int = 0x720a0a

  def ofItem(item: Item): VariantIota =
    fromStack(ItemStack(item))

  def ofFluid(fluid: Fluid): VariantIota =
    fromFluidStack(FluidStack(fluid, 1))

  def ofHeat: VariantIota =
    make(HeatKind)

  def ofMedia: VariantIota =
    make(MediaKind)

  def fromStack(stack: ItemStack): VariantIota =
    make(ItemKind, stack)

  def fromFluidStack(stack: FluidStack): VariantIota =
    make(FluidKind, fluid = stack)

  def fromBlockAt(level: Level, pos: BlockPos): Iota =
    val fluid = level.getFluidState(pos)
    if !fluid.isEmpty then
      ofFluid(fluid.getType)
    else
      val state = level.getBlockState(pos)
      val item = state.getBlock.asItem
      if state.isAir then
        NullIota()
      else if item != null && item != Items.AIR then
        ofItem(item)
      else
        val clone = state.getBlock.getCloneItemStack(level, pos, state)
        if clone == null || clone.isEmpty || clone.getItem == Items.AIR then NullIota()
        else fromStack(clone)

  private def decodeFabricLegacy[T](
    ops: DynamicOps[T],
    input: MapLike[T]
  ): scala.Either[String, VariantIota] =
    def string(key: String): Option[String] =
      Option(input.get(key)).flatMap: value =>
        val result = ops.getStringValue(value).result()
        if result.isPresent then Some(result.get) else None

    val rawType = string("type").getOrElse("")
    val kind = Option(ResourceLocation.tryParse(rawType)).map(_.getPath).getOrElse(rawType)
    kind match
      case ItemKind =>
        for
          itemRaw <- string("item").toRight("Legacy Hexic item variant is missing 'item'")
          itemId <- Option(ResourceLocation.tryParse(itemRaw))
            .toRight(s"Invalid legacy Hexic item id '$itemRaw'")
          item = BuiltInRegistries.ITEM.get(itemId)
          _ <- scala.Either.cond(item != null && item != Items.AIR, (), s"Unknown legacy Hexic item '$itemId'")
          stack <- decodeLegacyItemStack(ops, input, itemId, item)
        yield fromStack(stack)

      case FluidKind =>
        for
          fluidRaw <- string("fluid").toRight("Legacy Hexic fluid variant is missing 'fluid'")
          fluidId <- Option(ResourceLocation.tryParse(fluidRaw))
            .toRight(s"Invalid legacy Hexic fluid id '$fluidRaw'")
          fluid = BuiltInRegistries.FLUID.get(fluidId)
          _ <- scala.Either.cond(fluid != null && fluid != Fluids.EMPTY, (), s"Unknown legacy Hexic fluid '$fluidId'")
        yield
          val stack = FluidStack(fluid, 1)
          Option(input.get("tag")).foreach: rawTag =>
            ops.convertTo(NbtOps.INSTANCE, rawTag) match
              case tag: CompoundTag if !tag.isEmpty =>
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
              case _ =>
          fromFluidStack(stack)

      case MediaKind => Right(ofMedia)
      case HeatKind => Right(ofHeat)
      case other => Left(s"Unknown legacy Hexic transfer variant type '$other'")

  private def decodeLegacyItemStack[T](
    ops: DynamicOps[T],
    input: MapLike[T],
    itemId: ResourceLocation,
    item: Item
  ): scala.Either[String, ItemStack] =
    Option(input.get("tag")) match
      case None => Right(ItemStack(item))
      case Some(rawTag) =>
        ops.convertTo(NbtOps.INSTANCE, rawTag) match
          case legacyItemTag: CompoundTag =>
            try
              val legacyStack = CompoundTag()
              legacyStack.putString("id", itemId.toString)
              legacyStack.putByte("Count", 1.toByte)
              legacyStack.put("tag", legacyItemTag.copy())
              val updated = DataFixers.getDataFixer
                .update(
                  References.ITEM_STACK,
                  Dynamic(NbtOps.INSTANCE, legacyStack),
                  3465,
                  SharedConstants.getCurrentVersion.getDataVersion.getVersion
                )
                .getValue
              val converted = NbtOps.INSTANCE.convertTo(ops, updated)
              val parseResult = ItemStack.CODEC.parse(ops, converted)
              val parsed = parseResult.result()
              if parsed.isPresent then Right(parsed.get)
              else
                log.warn(
                  "Failed to decode data-fixed legacy Hexic item variant; preserving its raw tag. updated={} error={}",
                  updated,
                  parseResult.error().map(_.message()).orElse("unknown")
                )
                val fallback = ItemStack(item)
                fallback.set(DataComponents.CUSTOM_DATA, CustomData.of(legacyItemTag))
                Right(fallback)
            catch
              case failure: Throwable =>
                log.warn("Failed to data-fix legacy Hexic item variant; preserving its raw tag", failure)
                val fallback = ItemStack(item)
                fallback.set(DataComponents.CUSTOM_DATA, CustomData.of(legacyItemTag))
                Right(fallback)
          case _ => Left("Legacy Hexic item variant 'tag' is not a compound")

object BoxedView:
  val MaxHeat: Int = 5 * 60 * 20
  val MaxMedia: Long = 9000000000000000000L

  private[hexic] def saturatingAdd(left: Long, right: Long): Long =
    if left <= 0L then Math.max(0L, right)
    else if right <= 0L then left
    else if left >= Long.MaxValue - right then Long.MaxValue
    else left + right

  private[hexic] def extractItems(
    handler: IItemHandler,
    variant: VariantIota,
    amount: Long,
    simulate: Boolean
  ): Long =
    var remaining = Math.max(0L, amount)
    var extracted = 0L
    var slot = 0
    while remaining > 0L && slot < handler.getSlots do
      val stack = handler.getStackInSlot(slot)
      if variant.matchesItem(stack) then
        val requested = Math.min(remaining, Int.MaxValue.toLong).toInt
        val got = handler.extractItem(slot, requested, simulate)
        val count =
          if got.isEmpty || !variant.matchesItem(got) then 0L
          else Math.min(requested.toLong, got.getCount.toLong)
        extracted = saturatingAdd(extracted, count)
        remaining -= count
      slot += 1
    extracted

  private[hexic] def insertItems(
    handler: IItemHandler,
    variant: VariantIota,
    amount: Long,
    simulate: Boolean
  ): Long =
    var remaining = Math.max(0L, amount)
    var inserted = 0L
    var slot = 0
    while remaining > 0L && slot < handler.getSlots do
      val maxStackSize = Math.max(1, variant.stackPrototype.getMaxStackSize)
      val requested = Math.min(remaining, maxStackSize.toLong).toInt
      val stack = variant.toStack(requested)
      val leftover = handler.insertItem(slot, stack, simulate)
      val left =
        if leftover.isEmpty then 0L
        else if variant.matchesItem(leftover) then Math.min(requested.toLong, leftover.getCount.toLong)
        else requested.toLong
      val accepted = requested.toLong - left
      inserted = saturatingAdd(inserted, accepted)
      remaining -= accepted
      slot += 1
    inserted

  private[hexic] def extractFluid(
    handler: IFluidHandler,
    variant: VariantIota,
    amount: Long,
    simulate: Boolean
  ): Long =
    val requested = Math.min(Math.max(0L, amount), Int.MaxValue.toLong).toInt
    if requested <= 0 then 0L
    else
      val action = if simulate then IFluidHandler.FluidAction.SIMULATE else IFluidHandler.FluidAction.EXECUTE
      val drained = handler.drain(variant.toFluidStack(requested), action)
      if drained.isEmpty || !variant.matchesFluid(drained) then 0L
      else Math.min(requested, drained.getAmount).toLong

  private[hexic] def insertFluid(
    handler: IFluidHandler,
    variant: VariantIota,
    amount: Long,
    simulate: Boolean
  ): Long =
    val requested = Math.min(Math.max(0L, amount), Int.MaxValue.toLong).toInt
    if requested <= 0 then 0L
    else
      val action = if simulate then IFluidHandler.FluidAction.SIMULATE else IFluidHandler.FluidAction.EXECUTE
      Math.max(0, Math.min(requested, handler.fill(variant.toFluidStack(requested), action))).toLong

  trait View:
    def serialize: String
    def isTruthy: Boolean = true
    def contents(server: MinecraftServer): Seq[VariantIota] = Seq.empty
    def available(server: MinecraftServer, variant: VariantIota): Long =
      extract(server, variant, Long.MaxValue, simulate = true)
    def remaining(server: MinecraftServer, variant: VariantIota): Long =
      insert(server, variant, Long.MaxValue, simulate = true)
    def extract(server: MinecraftServer, variant: VariantIota, amount: Long, simulate: Boolean): Long = 0L
    def insert(server: MinecraftServer, variant: VariantIota, amount: Long, simulate: Boolean): Long = 0L
    def entities(server: MinecraftServer): Seq[Entity] = Seq.empty
    def teleportEntity(server: MinecraftServer, entity: Entity): Boolean = false

  case class BlockView(dimension: ResourceLocation, pos: BlockPos) extends View:
    override def serialize: String =
      s"block|${dimension.toString}|${pos.getX}|${pos.getY}|${pos.getZ}"

    private def level(server: MinecraftServer): Option[ServerLevel] =
      Option(server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension)))

    private def itemHandler(server: MinecraftServer): Option[IItemHandler] =
      level(server).flatMap(level => Option(level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null.asInstanceOf[Direction])))

    private def fluidHandler(server: MinecraftServer): Option[IFluidHandler] =
      level(server).flatMap(level => Option(level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null.asInstanceOf[Direction])))

    private def furnace(server: MinecraftServer): Option[AbstractFurnaceBlockEntity] =
      level(server).flatMap(level => Option(level.getBlockEntity(pos))).collect:
        case furnace: AbstractFurnaceBlockEntity => furnace

    private def furnaceAccess(server: MinecraftServer): Option[AbstractFurnaceBlockEntityAccess] =
      furnace(server).map(_.asInstanceOf[AbstractFurnaceBlockEntityAccess])

    private def impetus(server: MinecraftServer): Option[BlockEntityAbstractImpetus] =
      level(server).flatMap(level => Option(level.getBlockEntity(pos))).collect:
        case impetus: BlockEntityAbstractImpetus => impetus

    private def extensionHandlers(server: MinecraftServer): Seq[HexicViewApi.Handler] =
      level(server).toSeq.flatMap: level =>
        HexicViewApi.blockHandlers(level, pos, level.getBlockState(pos)).asScala.toSeq

    private def setFurnaceLit(server: MinecraftServer, lit: Boolean): Unit =
      level(server).foreach: level =>
        val state = level.getBlockState(pos)
        if state.hasProperty(AbstractFurnaceBlock.LIT) && state.getValue(AbstractFurnaceBlock.LIT) != lit then
          level.setBlockAndUpdate(pos, state.setValue(AbstractFurnaceBlock.LIT, Boolean.box(lit)))

    override def contents(server: MinecraftServer): Seq[VariantIota] =
      val items = itemHandler(server).toSeq.flatMap: handler =>
        (0 until handler.getSlots).flatMap: slot =>
          val stack = handler.getStackInSlot(slot)
          Option.when(!stack.isEmpty)(VariantIota.fromStack(stack))
      val fluids = fluidHandler(server).toSeq.flatMap: handler =>
        (0 until handler.getTanks).flatMap: tank =>
          val stack = handler.getFluidInTank(tank)
          Option.when(!stack.isEmpty)(VariantIota.fromFluidStack(stack))
      val media = impetus(server).toSeq.map(_ => VariantIota.ofMedia)
      val heat = furnaceAccess(server).toSeq.map(_ => VariantIota.ofHeat)
      val extensionContents =
        extensionHandlers(server)
          .flatMap(_.contents(server).asScala)
          .collect:
            case variant: VariantIota => variant
      (items ++ fluids ++ media ++ heat ++ extensionContents).distinct

    override def available(server: MinecraftServer, variant: VariantIota): Long =
      // Fabric Storage's transaction-backed availability query measured what
      // can actually be extracted, not merely what is visible in slots/tanks.
      // Capability implementations may expose locked or output-restricted
      // contents, so always honor their SIMULATE contract here.
      extract(server, variant, Long.MaxValue, simulate = true)

    override def remaining(server: MinecraftServer, variant: VariantIota): Long =
      insert(server, variant, Long.MaxValue, simulate = true)

    private def extractBuiltIn(
      server: MinecraftServer,
      variant: VariantIota,
      amount: Long,
      simulate: Boolean
    ): Long =
      if variant.isHeat then
        furnaceAccess(server).map: access =>
          val extracted = Math.max(0L, Math.min(amount, access.hexic$getLitTime().toLong)).toInt
          if !simulate && extracted > 0 then
            val next = access.hexic$getLitTime() - extracted
            access.hexic$setLitTime(next)
            furnace(server).foreach(_.setChanged())
            setFurnaceLit(server, next > 0)
          extracted.toLong
        .getOrElse(0L)
      else if variant.isMedia then
        impetus(server).map: impetus =>
          val extracted = Math.max(0L, Math.min(amount, impetus.getMedia))
          if !simulate && extracted > 0L then
            impetus.setMedia(impetus.getMedia - extracted)
            impetus.setChanged()
          extracted
        .getOrElse(0L)
      else if variant.isFluid then
        fluidHandler(server)
          .map(handler => extractFluid(handler, variant, amount, simulate))
          .getOrElse(0L)
      else
        itemHandler(server)
          .map(handler => extractItems(handler, variant, amount, simulate))
          .getOrElse(0L)

    override def extract(
      server: MinecraftServer,
      variant: VariantIota,
      amount: Long,
      simulate: Boolean
    ): Long =
      var remaining = Math.max(0L, amount)
      var extracted = Math.min(remaining, extractBuiltIn(server, variant, remaining, simulate))
      remaining -= extracted
      val handlers = extensionHandlers(server).iterator
      while remaining > 0L && handlers.hasNext do
        val got = handlers.next().extract(server, variant, remaining, simulate)
        val accepted = Math.max(0L, Math.min(remaining, got))
        extracted = saturatingAdd(extracted, accepted)
        remaining -= accepted
      extracted

    private def insertBuiltIn(
      server: MinecraftServer,
      variant: VariantIota,
      amount: Long,
      simulate: Boolean
    ): Long =
      if variant.isHeat then
        furnaceAccess(server).map: access =>
          val capacity = Math.max(0L, BoxedView.MaxHeat.toLong - access.hexic$getLitTime().toLong)
          val inserted = Math.max(0L, Math.min(amount, capacity)).toInt
          if !simulate && inserted > 0 then
            val next = access.hexic$getLitTime() + inserted
            access.hexic$setLitTime(next)
            access.hexic$setLitDuration(Math.max(access.hexic$getLitDuration(), next))
            furnace(server).foreach(_.setChanged())
            setFurnaceLit(server, next > 0)
          inserted.toLong
        .getOrElse(0L)
      else if variant.isMedia then
        impetus(server).map: impetus =>
          val capacity = Math.max(0L, BoxedView.MaxMedia - impetus.getMedia)
          val inserted = Math.max(0L, Math.min(amount, capacity))
          if !simulate && inserted > 0L then
            impetus.setMedia(impetus.getMedia + inserted)
            impetus.setChanged()
          inserted
        .getOrElse(0L)
      else if variant.isFluid then
        fluidHandler(server)
          .map(handler => insertFluid(handler, variant, amount, simulate))
          .getOrElse(0L)
      else
        itemHandler(server)
          .map(handler => insertItems(handler, variant, amount, simulate))
          .getOrElse(0L)

    override def insert(
      server: MinecraftServer,
      variant: VariantIota,
      amount: Long,
      simulate: Boolean
    ): Long =
      var remaining = Math.max(0L, amount)
      var inserted = Math.min(remaining, insertBuiltIn(server, variant, remaining, simulate))
      remaining -= inserted
      val handlers = extensionHandlers(server).iterator
      while remaining > 0L && handlers.hasNext do
        val got = handlers.next().insert(server, variant, remaining, simulate)
        val accepted = Math.max(0L, Math.min(remaining, got))
        inserted = saturatingAdd(inserted, accepted)
        remaining -= accepted
      inserted

    override def entities(server: MinecraftServer): Seq[Entity] =
      val builtIn = level(server).toSeq.flatMap: level =>
        if isEntitySpaceOpen(level) then
          val box = net.minecraft.world.phys.AABB.ofSize(pos.getCenter, 1.0, 1.0, 1.0)
          val predicate = new java.util.function.Predicate[Entity]:
            override def test(entity: Entity): Boolean = true
          level.getEntities(null.asInstanceOf[Entity], box, predicate).asScala.toSeq
        else
          Seq.empty
      val extended = extensionHandlers(server).flatMap(_.entities(server).asScala)
      (builtIn ++ extended).distinct

    override def teleportEntity(server: MinecraftServer, entity: Entity): Boolean =
      val builtIn = level(server) match
        case Some(dest) if isEntitySpaceOpen(dest) =>
          entity.teleportTo(dest, pos.getX + 0.5, pos.getY + 0.25, pos.getZ + 0.5, Set.empty[net.minecraft.world.entity.RelativeMovement].asJava, entity.getYRot, entity.getXRot)
          true
        case None => false
        case _ => false
      builtIn || extensionHandlers(server).exists(_.teleportEntity(server, entity))

    private def isEntitySpaceOpen(level: ServerLevel): Boolean =
      level.getBlockState(pos).propagatesSkylightDown(level, pos)

  case class EntityView(uuid: UUID) extends View:
    override def serialize: String = s"entity|$uuid"

    private def entity(server: MinecraftServer): Option[Entity] =
      Option(server.getPlayerList.getPlayer(uuid)).map(_.asInstanceOf[Entity]).orElse:
        server.getAllLevels.asScala.iterator.flatMap(level => Option(level.getEntity(uuid))).toSeq.headOption

    private def extensionHandlers(server: MinecraftServer): Seq[HexicViewApi.Handler] =
      entity(server).toSeq.flatMap: entity =>
        entity.level() match
          case level: ServerLevel =>
            HexicViewApi.entityHandlers(level, entity).asScala.toSeq
          case _ => Seq.empty

    override def contents(server: MinecraftServer): Seq[VariantIota] =
      extensionHandlers(server)
        .flatMap(_.contents(server).asScala)
        .collect:
          case variant: VariantIota => variant
        .distinct

    override def extract(
      server: MinecraftServer,
      variant: VariantIota,
      amount: Long,
      simulate: Boolean
    ): Long =
      var remaining = Math.max(0L, amount)
      var extracted = 0L
      val handlers = extensionHandlers(server).iterator
      while remaining > 0L && handlers.hasNext do
        val got = handlers.next().extract(server, variant, remaining, simulate)
        val accepted = Math.max(0L, Math.min(remaining, got))
        extracted = saturatingAdd(extracted, accepted)
        remaining -= accepted
      extracted

    override def insert(
      server: MinecraftServer,
      variant: VariantIota,
      amount: Long,
      simulate: Boolean
    ): Long =
      var remaining = Math.max(0L, amount)
      var inserted = 0L
      val handlers = extensionHandlers(server).iterator
      while remaining > 0L && handlers.hasNext do
        val got = handlers.next().insert(server, variant, remaining, simulate)
        val accepted = Math.max(0L, Math.min(remaining, got))
        inserted = saturatingAdd(inserted, accepted)
        remaining -= accepted
      inserted

    override def entities(server: MinecraftServer): Seq[Entity] =
      (entity(server).toSeq ++ extensionHandlers(server).flatMap(_.entities(server).asScala)).distinct

    override def teleportEntity(server: MinecraftServer, entity: Entity): Boolean =
      extensionHandlers(server).exists(_.teleportEntity(server, entity))

  case class SumView(views: Seq[View]) extends View:
    private def flattenedViews: Seq[View] =
      views.flatMap:
        case sum: SumView => sum.flattenedViews
        case view => Seq(view)

    override def isTruthy: Boolean = flattenedViews.exists(_.isTruthy)

    override def serialize: String =
      flattenedViews.map(view => escape(view.serialize)).mkString("sum|", ";", "")

    override def contents(server: MinecraftServer): Seq[VariantIota] =
      flattenedViews.flatMap(_.contents(server)).distinct

    override def available(server: MinecraftServer, variant: VariantIota): Long =
      flattenedViews.foldLeft(0L)((total, view) =>
        saturatingAdd(total, view.available(server, variant))
      )

    override def remaining(server: MinecraftServer, variant: VariantIota): Long =
      flattenedViews.foldLeft(0L)((total, view) =>
        saturatingAdd(total, view.remaining(server, variant))
      )

    override def extract(server: MinecraftServer, variant: VariantIota, amount: Long, simulate: Boolean): Long =
      var remaining = amount
      var extracted = 0L
      val iterator = flattenedViews.iterator
      while remaining > 0 && iterator.hasNext do
        val got = iterator.next().extract(server, variant, remaining, simulate)
        extracted += got
        remaining -= got
      extracted

    override def insert(server: MinecraftServer, variant: VariantIota, amount: Long, simulate: Boolean): Long =
      var remaining = amount
      var inserted = 0L
      val iterator = flattenedViews.iterator
      while remaining > 0 && iterator.hasNext do
        val put = iterator.next().insert(server, variant, remaining, simulate)
        inserted += put
        remaining -= put
      inserted

    override def entities(server: MinecraftServer): Seq[Entity] =
      flattenedViews.flatMap(_.entities(server)).distinct

    override def teleportEntity(server: MinecraftServer, entity: Entity): Boolean =
      flattenedViews.exists(_.teleportEntity(server, entity))

  case class Instance(view: View) extends Iota(() => TYPE):
    override def isTruthy: Boolean = view.isTruthy

    override def toleratesOther(other: Iota): Boolean =
      other match
        case that: Instance => view.serialize == that.view.serialize
        case _ => false

    override def display(): Component =
      Component.literal(s"[View:${view.serialize}]")

  private val ViewMapCodec: MapCodec[Instance] = new MapCodec[Instance]:
    override def keys[T](ops: DynamicOps[T]): java.util.stream.Stream[T] =
      java.util.stream.Stream.of(
        ops.createString("view"),
        ops.createString("id"),
        ops.createString("m"),
        ops.createString("l"),
        ops.createString("w"),
        ops.createString("p"),
        ops.createString("c")
      )

    override def encode[T](
      input: Instance,
      ops: DynamicOps[T],
      prefix: RecordBuilder[T]
    ): RecordBuilder[T] =
      prefix.add("view", ops.createString(input.view.serialize))

    override def decode[T](ops: DynamicOps[T], input: MapLike[T]): DataResult[Instance] =
      Option(input.get("view")) match
        case Some(serialized) =>
          val result = ops.getStringValue(serialized).result()
          if result.isEmpty then
            DataResult.error(() => "Hexic inventory view field 'view' is not a string")
          else
            parseValidated(result.get) match
              case Right(view) => DataResult.success(Instance(view))
              case Left(error) => DataResult.error(() => error)
        case None =>
          parseLegacy(ops, input) match
            case Right(view) => DataResult.success(Instance(view))
            case Left(error) => DataResult.error(() => error)

  val TYPE: IotaType[Instance] = new IotaType[Instance]:
    override def codec(): MapCodec[Instance] = ViewMapCodec

    override def streamCodec(): StreamCodec[RegistryFriendlyByteBuf, Instance] =
      ByteBufCodecs.STRING_UTF8
        .asInstanceOf[StreamCodec[RegistryFriendlyByteBuf, String]]
        .map(s => Instance(parse(s)), _.view.serialize)

    override def color(): Int = 0xa59e7c

  private def escape(serialized: String): String =
    val out = StringBuilder(serialized.length)
    serialized.foreach:
      case '\\' => out.append("\\\\")
      case ';' => out.append("\\;")
      case character => out.append(character)
    out.toString

  private def splitEscaped(serialized: String): Seq[String] =
    val values = scala.collection.mutable.ArrayBuffer.empty[String]
    val current = StringBuilder()
    var escaped = false
    serialized.foreach: character =>
      if escaped then
        current.append(character)
        escaped = false
      else
        character match
          case '\\' => escaped = true
          case ';' =>
            values += current.toString
            current.clear()
          case other => current.append(other)
    if escaped then current.append('\\')
    values += current.toString
    values.toSeq.filter(_.nonEmpty)

  def parseValidated(serialized: String): Either[String, View] =
    if serialized == null then
      Left("Hexic inventory view is null")
    else if serialized.startsWith("block|") then
      serialized.split("\\|", -1).toList match
        case "block" :: dimensionRaw :: xRaw :: yRaw :: zRaw :: Nil =>
          for
            dimension <- Option(ResourceLocation.tryParse(dimensionRaw))
              .toRight(s"Invalid Hexic inventory-view dimension '$dimensionRaw'")
            x <- Try(xRaw.toInt).toEither.left.map(_ => s"Invalid Hexic inventory-view x '$xRaw'")
            y <- Try(yRaw.toInt).toEither.left.map(_ => s"Invalid Hexic inventory-view y '$yRaw'")
            z <- Try(zRaw.toInt).toEither.left.map(_ => s"Invalid Hexic inventory-view z '$zRaw'")
          yield BlockView(dimension, BlockPos(x, y, z))
        case _ => Left(s"Malformed Hexic block inventory view '$serialized'")
    else if serialized.startsWith("entity|") then
      serialized.split("\\|", -1).toList match
        case "entity" :: uuidRaw :: Nil =>
          Try(UUID.fromString(uuidRaw)).toEither
            .left.map(_ => s"Invalid Hexic entity-view UUID '$uuidRaw'")
            .map(EntityView.apply)
        case _ => Left(s"Malformed Hexic entity inventory view '$serialized'")
    else if serialized == "sum" || serialized.startsWith("sum|") then
      val childrenRaw = if serialized == "sum" then "" else serialized.substring(4)
      splitEscaped(childrenRaw).foldLeft[Either[String, Vector[View]]](Right(Vector.empty)):
        case (Right(views), child) => parseValidated(child).map(views :+ _)
        case (error @ Left(_), _) => error
      .map(views => SumView(views))
    else
      Left(s"Unknown Hexic inventory view '$serialized'")

  /**
   * Fail-soft entry retained for packet decoding and callers that used the
   * original total parser. Persistent codec decoding uses parseValidated and
   * reports malformed data instead of throwing.
   */
  def parse(serialized: String): View =
    parseValidated(serialized).getOrElse(SumView(Seq.empty))

  private def stringValue[T](ops: DynamicOps[T], input: MapLike[T], key: String): Option[String] =
    Option(input.get(key)).flatMap: value =>
      val result = ops.getStringValue(value).result()
      if result.isPresent then Some(result.get) else None

  private def longValue[T](ops: DynamicOps[T], input: MapLike[T], key: String): Option[Long] =
    Option(input.get(key)).flatMap: value =>
      val result = ops.getNumberValue(value).result()
      if result.isPresent then Some(result.get.longValue()) else None

  /**
   * Reads the exact NBT shape emitted by Hexic 2.1.0 on Fabric:
   * block={id,m?,w?,p}, entity={id,m,l}, sum={c:[...]}.
   */
  private def parseLegacy[T](ops: DynamicOps[T], input: MapLike[T]): Either[String, View] =
    val idPath = stringValue(ops, input, "id")
      .flatMap(raw => Option(ResourceLocation.tryParse(raw)).map(_.getPath))
    val hasChildren = input.get("c") != null
    val kind = idPath.orElse(Option.when(hasChildren)("sum"))

    kind match
      case Some("block") =>
        val namespace = stringValue(ops, input, "m").filter(value => !value.isBlank).getOrElse("minecraft")
        val path = stringValue(ops, input, "w").filter(value => !value.isBlank).getOrElse("overworld")
        for
          dimension <- Option(ResourceLocation.tryBuild(namespace, path))
            .toRight(s"Invalid legacy Hexic view dimension '$namespace:$path'")
          packed <- longValue(ops, input, "p")
            .toRight("Legacy Hexic block view is missing packed position 'p'")
        yield BlockView(dimension, BlockPos.of(packed))

      case Some("entity") =>
        for
          most <- longValue(ops, input, "m")
            .toRight("Legacy Hexic entity view is missing UUID high bits 'm'")
          least <- longValue(ops, input, "l")
            .toRight("Legacy Hexic entity view is missing UUID low bits 'l'")
        yield EntityView(UUID(most, least))

      case Some("sum") =>
        Option(input.get("c")) match
          case None => Right(SumView(Seq.empty))
          case Some(value) =>
            val stream = ops.getStream(value).result()
            if stream.isEmpty then
              Left("Legacy Hexic sum view field 'c' is not a list")
            else
              stream.get.iterator().asScala
                .foldLeft[Either[String, Vector[View]]](Right(Vector.empty)):
                  case (Right(views), childValue) =>
                    val childMap = ops.getMap(childValue).result()
                    if childMap.isEmpty then Left("Legacy Hexic sum view contains a non-compound child")
                    else parseLegacy(ops, childMap.get).map(views :+ _)
                  case (error @ Left(_), _) => error
              .map(views => SumView(views))

      case Some(other) => Left(s"Unknown legacy Hexic inventory view type '$other'")
      case None => Left("Legacy Hexic inventory view is missing type 'id'")
