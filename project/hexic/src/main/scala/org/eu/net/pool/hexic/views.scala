package org.eu.net.pool.hexic

import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic
import at.petrak.hexcasting.api.casting.circles.BlockEntityAbstractImpetus
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv
import at.petrak.hexcasting.api.casting.iota.*
import at.petrak.hexcasting.api.casting.mishaps.{Mishap, MishapInvalidIota, MishapOthersName}
import com.mojang.serialization.Codec
import com.samsthenerd.inline.api.InlineAPI
import com.samsthenerd.inline.api.data.ItemInlineData
import com.samsthenerd.inline.impl.InlineStyle
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions
import net.fabricmc.fabric.api.event.{Event, EventFactory}
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant
import net.fabricmc.fabric.api.transfer.v1.item.{ItemStorage, ItemVariant}
import net.fabricmc.fabric.api.transfer.v1.storage.{Storage, TransferVariant}
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant
import net.fabricmc.fabric.api.transfer.v1.transaction.{Transaction, TransactionContext}
import net.minecraft.block.{AbstractFurnaceBlock, BlockState}
import net.minecraft.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.entity.decoration.ItemFrameEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.{Entity, ItemEntity}
import net.minecraft.fluid.{Fluid, Fluids}
import net.minecraft.item.{Item, Items}
import net.minecraft.nbt.{NbtCompound, NbtElement, NbtList, NbtLong}
import net.minecraft.registry.{RegistryKey, RegistryKeys}
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.{HoverEvent, MutableText, Text}
import net.minecraft.util.Identifier
import net.minecraft.util.math.{BlockPos, Box}
import net.minecraft.world.{BlockView, TeleportTarget, World}
import org.eu.net.pool.phlib.{*, given}
import org.slf4j.{Logger, LoggerFactory}
import ram.talia.hexal.api.casting.iota.MoteIota
import ram.talia.hexal.api.mediafieditems.MediafiedItemManager
import ram.talia.moreiotas.api.casting.iota.{ItemStackIota, ItemTypeIota}

import java.util.UUID
import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq.unsafeWrapArray
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Using, boundary}

trait InventoryView(val viewType: InventoryView.Type[?]) extends InventoryView.Handler:
  def isTruthy = true
  def serialize: NbtCompound = NbtCompound().tap(_.putString("id", InventoryView.registry.getId(viewType).toString))
object InventoryView extends Registrar[InventoryView.Type[?]]("inventory"):
  trait Type[+T <: InventoryView]:
    def deserialize(data: NbtCompound)(using ServerWorld): Option[T]
  trait Handler:
    def apply(idx: Int)(using CastingEnvironment): Option[SlotReference] = None
    def contents(using TransactionContext, CastingEnvironment): Set[VariantIota[?]] = Set()
    def tryExtract(variant: TransferVariant[?], amount: Long)(using TransactionContext, CastingEnvironment): Long = 0
    def tryInsert(variant: TransferVariant[?], amount: Long)(using TransactionContext, CastingEnvironment): Long = 0
    def capacity(variant: TransferVariant[?])(using TransactionContext, CastingEnvironment): Long =
      Using(summon[TransactionContext].openNested()): tx =>
        given TransactionContext = tx
        tryExtract(variant, Long.MaxValue)
      match
        case Success(n) => n
        case Failure(ex) =>
          given_Logger.error("capacity", ex)
          0L
    def entities(using TransactionContext): Set[Entity] = Set()
    @throws[Mishap]
    def teleportEntity(ent: Entity)(using TransactionContext, CastingEnvironment): Boolean = false
  object Events:
    val forEntity: Event[Entity => ServerWorld ?=> Seq[Handler]] = EventFactory.createArrayBacked(classOf, _ => Seq(), fns => e => unsafeWrapArray(fns).flatMap(_(e)))
    val forBlock: Event[(BlockPos, BlockState) => ServerWorld ?=> Seq[Handler]] = EventFactory.createArrayBacked(classOf, (_, _) => Seq(), fns => (pos, state) => unsafeWrapArray(fns).flatMap(_(pos, state)))
    // 'implementation restriction' my ass
    val forIota: Event[CastingEnvironment ?=> PartialFunction[Iota, InventoryView]] = EventFactory.createArrayBacked(classOf, PartialFunction.empty, new java.util.function.Function[Array[CastingEnvironment ?=> PartialFunction[Iota, InventoryView]], CastingEnvironment ?=> PartialFunction[Iota, InventoryView]]:
      def apply(fns: Array[CastingEnvironment ?=> PartialFunction[Iota, InventoryView]]): CastingEnvironment ?=> PartialFunction[Iota, InventoryView] = (((_: CastingEnvironment) ?=> PartialFunction.empty[Iota, InventoryView]) /: fns) { _ orElse _ }
    )
  abstract class OfMerged(viewType: InventoryView.Type[?], views: => Seq[Handler]) extends InventoryView(viewType):
    def getViews = views
    override def apply(idx: Int)(using CastingEnvironment): Option[SlotReference] = views.collectFirst(Function.unlift(_(idx)))
    override def contents(using TransactionContext, CastingEnvironment) = views.flatMap(_.contents).toSet
    override def tryExtract(variant: TransferVariant[?], amount: Long)(using TransactionContext, CastingEnvironment): Long = LazyList.from(views).scanLeft(0L)((n, view) => view.tryExtract(variant, amount - n) + n).findFirstOrLast(_ >= amount).getOrElse(0)
    override def tryInsert(variant: TransferVariant[?], amount: Long)(using TransactionContext, CastingEnvironment): Long = LazyList.from(views).scanLeft(0L)((n, view) => view.tryInsert(variant, amount - n) + n).findFirstOrLast(_ >= amount).getOrElse(0)
    override def capacity(variant: TransferVariant[?])(using TransactionContext, CastingEnvironment) = views.map(_.capacity(variant)).sum
    override def entities(using TransactionContext) = views.flatMap(_.entities).toSet
    override def teleportEntity(ent: Entity)(using TransactionContext, CastingEnvironment): Boolean = views.iterator∃(_.teleportEntity(ent))
  class OfSum private(private[InventoryView] val views: Seq[InventoryView]) extends OfMerged(typeOfSum, views):
    override def isTruthy = views ∃(_.isTruthy)
    override def serialize =
      val c = NbtCompound()
      val list = NbtList()
      for view <- views do list.add(view.serialize)
      c.put("c", list)
      c
  object OfSum:
    def apply(views: InventoryView*) = new OfSum(
      views.flatMap:
        case v: OfSum => v.views
        case v => Iterable(v)
    )
  class OfEntity(id: UUID)(using server: MinecraftServer) extends OfMerged(typeOfEntity, server.getWorlds.collectFirst(Function.unlift(w => Option(w.getEntity(id)).map(Events.forEntity.invoker()(_)(using w)))).getOrElse(Seq())):
    override def serialize =
      val c = super.serialize
      c.putLong("m", id.getMostSignificantBits)
      c.putLong("l", id.getLeastSignificantBits)
      c
  class OfBlock(pos: BlockPos)(using world: ServerWorld) extends OfMerged(typeOfBlock, Events.forBlock.invoker()(pos, world.getBlockState(pos))):
    override def serialize =
      val c = super.serialize
      val w = world.getRegistryKey.getValue
      if w.getNamespace != "minecraft" then c.put("m", w.getNamespace)
      if w.getPath != "overworld" then c.put("w", w.getPath)
      c.putLong("p", pos.asLong)
      c
  def deserialize(data: NbtCompound)(using ServerWorld): Option[InventoryView] = for
    id <- Option(Identifier.tryParse(data.getString("id")))
    viewType <- Option(InventoryView.registry.get(id))
    view <- viewType.deserialize(data)
  yield view
  private given typeOfSum: InventoryView.Type[OfSum]:
    override def deserialize(data: NbtCompound)(using ServerWorld): Option[OfSum] =
      Some(OfSum((for
        n <- 0 until data.getInt("n")
        key = "_" + Integer.toString(n + 10, 36)
        c = data.getCompound(key)
        view <- InventoryView.deserialize(c)
      yield view)*))
  private given typeOfEntity: InventoryView.Type[OfEntity]:
    override def deserialize(data: NbtCompound)(using world: ServerWorld): Option[OfEntity] =
      given MinecraftServer = world.getServer
      Some(OfEntity(UUID(data.getLong("m"), data.getLong("l"))))
  private given typeOfBlock: InventoryView.Type[OfBlock]:
    override def deserialize(data: NbtCompound)(using ServerWorld): Option[OfBlock] =
      for
        case posLong: NbtLong <- Option(data.get("p"))
        pos = BlockPos.fromLong(posLong.longValue)
        namespace = Option(data.getString("m")).filter(!_.isBlank) getOrElse "minecraft"
        path = Option(data.getString("w")).filter(!_.isBlank) getOrElse "overworld"
        key = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(namespace, path))
        given ServerWorld <- Option(summon[ServerWorld].getServer.getWorld(key))
      yield OfBlock(pos)
  registry("sum") = typeOfSum
  registry("entity") = typeOfEntity
  registry("block") = typeOfBlock
  Events.forBlock.register: (pos, state) =>
    val storage = ItemStorage.SIDED.find(summon, pos, null): Storage[ItemVariant]
    if storage == null then Seq()
    else Seq(
      new Handler:
        override def contents(using TransactionContext, CastingEnvironment): Set[VariantIota[?]] = storage.nonEmptyIterator.map(v => VariantIota(v.getResource, RegistryKey.of(VariantIota.key, Identifier("item")))).toSet
        override def tryInsert(variant: TransferVariant[?], amount: Long)(using TransactionContext, CastingEnvironment): Long =
          variant match
            case i: ItemVariant => storage.insert(i, amount, summon)
            case _ => 0
        override def tryExtract(variant: TransferVariant[?], amount: Long)(using TransactionContext, CastingEnvironment): Long =
          variant match
            case i: ItemVariant => storage.extract(i, amount, summon)
            case _ => 0
    )
  Events.forBlock.register: (pos, state) =>
    if state.isTransparent(summon, pos) then
      Seq(new Handler:
        override def entities(using TransactionContext): Set[Entity] = summon[World].getOtherEntities(null, Box.of(pos.toCenterPos, 0.5, 0.5, 0.5), _ => true).toSet
        override def teleportEntity(ent: Entity)(using TransactionContext, CastingEnvironment): Boolean =
          var currEnt = ent
          doSnapshot((currEnt.getPos, currEnt.getWorld.asInstanceOf[ServerWorld]), snapshot => currEnt = FabricDimensions.teleport(currEnt, snapshot._2, TeleportTarget(snapshot._1, currEnt.getVelocity, currEnt.getYaw, currEnt.getPitch)))(pos.toCenterPos.subtract(0, 0.25, 0), summon)
          true
      )
    else Seq()
  trait SingleVariantHandler[O: ClassTag](variant: TransferVariant[O], key: RegistryKey[VariantIota.Reader]) extends Handler:
    def asIota = VariantIota(variant, key)
    def isPresent(using TransactionContext, CastingEnvironment): Boolean = true
    override def contents(using TransactionContext, CastingEnvironment): Set[VariantIota[?]] = if isPresent then Set(asIota) else Set()
    override def tryExtract(variant: TransferVariant[?], amount: Long)(using TransactionContext, CastingEnvironment): Long = if variant == this.variant then trySubtract(amount) else 0L
    override def tryInsert(variant: TransferVariant[?], amount: Long)(using TransactionContext, CastingEnvironment): Long = if variant == this.variant then tryAdd(amount) else 0L
    override def capacity(variant: TransferVariant[?])(using TransactionContext, CastingEnvironment): Long = if variant == this.variant then cap else 0L
    def trySubtract(amount: Long)(using TransactionContext, CastingEnvironment): Long
    def tryAdd(amount: Long)(using TransactionContext, CastingEnvironment): Long
    def cap(using TransactionContext, CastingEnvironment): Long

  Events.forBlock.register: (pos, state) =>
    summon[ServerWorld].getBlockEntity(pos) match
      case e: BlockEntityAbstractImpetus => Seq(new SingleVariantHandler(SingletonVariant.media, RegistryKey.of(VariantIota.key, "media")) {
        private def mediaCapacity = 9000000000000000000L - e.getMedia
        override def trySubtract(amount: Long)(using TransactionContext, CastingEnvironment): Long =
          val toExtract = amount min e.getMedia
          doSnapshot(e.getMedia, e.setMedia)(e.getMedia - toExtract)
          toExtract
        override def tryAdd(amount: Long)(using TransactionContext, CastingEnvironment): Long =
          val toInsert = amount min mediaCapacity
          doSnapshot(e.getMedia, e.setMedia)(e.getMedia + toInsert)
          toInsert
        override def cap(using TransactionContext, CastingEnvironment): Long = mediaCapacity
      })
      case _ => Seq()
  Events.forBlock.register: (pos, state) =>
    summon[ServerWorld].getBlockEntity(pos) match
      case e: AbstractFurnaceBlockEntity => Seq(
        new SingleVariantHandler(SingletonVariant.heat, RegistryKey.of(VariantIota.key, "heat")):
          override def trySubtract(amount: Long)(using TransactionContext, CastingEnvironment): Long =
            val action = (amount min e.burnTime).toInt
            doSnapshot(e.burnTime, e.burnTime = _)(e.burnTime - action)
            doSnapshot(summon[ServerWorld].getBlockState(pos), summon[ServerWorld].setBlockState(pos, _, 3))(summon[ServerWorld].getBlockState(pos).`with`(AbstractFurnaceBlock.LIT, true))
            action
          override def tryAdd(amount: Long)(using TransactionContext, CastingEnvironment): Long =
            val action = (amount min cap).toInt
            doSnapshot(e.burnTime, e.burnTime = _)(e.burnTime + action)
            doSnapshot(e.fuelTime, e.fuelTime = _)(e.fuelTime max e.burnTime)
            action
          override def cap(using TransactionContext, CastingEnvironment): Long = (5*60*20) - e.burnTime
      )
      case _ => Seq()
given Conversion[AbstractFurnaceBlockEntity, AbstractFurnaceBlockEntityAccess] = _.asInstanceOf // by mixin
trait AbstractFurnaceBlockEntityAccess:
  def burnTime: Int
  def burnTime_=(burnTime: Int): Unit
  def fuelTime: Int
  def fuelTime_=(fuelTime: Int): Unit

private [hexic] val conceptScale = mutable.Map[ClassTag[? <: TransferVariant[?]], Double]().withDefaultValue(1.0)
def setConceptScale[T <: TransferVariant[?]: ClassTag as ct](scale: Int) =
  if conceptScale.isDefinedAt(ct) && conceptScale(ct) != scale then
    throw IllegalStateException(s"Conflicting scales ${conceptScale(ct)} and $scale defined for class $ct")
  else
    conceptScale(ct) = scale


def doSnapshot[T](current: => T, apply: T => Unit)(toApply: T)(using tx: TransactionContext): Unit =
  object partip extends SnapshotParticipant[T]:
    override def createSnapshot(): T = current
    override def readSnapshot(snapshot: T): Unit = apply(snapshot)
  partip.updateSnapshots(tx)
  partip.readSnapshot(toApply)

object BoxedView extends IotaType[BoxedView.Instance]:
  InventoryView
  case class Instance(view: InventoryView) extends Iota(BoxedView, view):
    export view.{isTruthy, serialize}
    override def toleratesOther(that: Iota): Boolean = that match
      case that: BoxedView.Instance => view == that.view
      case _ => false
  override def deserialize(tag: NbtElement, world: ServerWorld): Instance =
    given ServerWorld = world;
    (for
      case c: NbtCompound <- Some(tag)
      view <- InventoryView.deserialize(c)
    yield Instance(view)).orNull
  override def display(tag: NbtElement): Text = "[View]".styled(_.withColor(color))
  override def color: Int = 0xa59e7c
  given this.type = this

trait SlotReference:
  def item(using CastingEnvironment): Item
  def nbt(using CastingEnvironment): Option[NbtCompound]
  def count(using CastingEnvironment): Long
  @throws[Mishap]
  def nbt_=(using Transaction, CastingEnvironment)(nbt: Option[NbtCompound]): Unit
  @throws[Mishap]
  def count_=(using Transaction, CastingEnvironment)(count: Long): Unit
object SlotReference extends Registrar[SlotReference.Type[?]]("slot"):
  class Type[T <: SlotReference: Codec]

object id:
  def unapply(x: Identifier) = (x.getNamespace, x.getPath)

def initViews() =
  hexXplat.getIotaTypeRegistry("transfer_type") = VariantIota
  hexXplat.getIotaTypeRegistry("inventory_view") = BoxedView
  Events.registryLookup.register:
    case (r, id("hexic" | "hexxychests", "variant")) => VariantIota
    case (r, id("hexic" | "hexxychests", "reference")) => BoxedView
  Patterns.register("findview", e"addaadewewedaaddqwawqddaadewewedaaddqwawdeeweee"):
    // 2026-01-01 pool: nathan, we call this 'jank'. why would you do this?
    inline def lookup = InventoryView.Events.forIota.invoker()(using compiletime.summonInline)
    Patterns.mkConstAction(1):
      case Seq(lookup(view)) => Seq(BoxedView.Instance(view))
      case Seq(iota) => throw MishapInvalidIota.ofType(iota, 0, "hexic:view")
  InventoryView.Events.forIota.register:
    case block: Vec3Iota =>
      val pos = BlockPos.ofFloored(block.getVec3)
      summon[CastingEnvironment].assertPosInRangeForEditing(pos)
      InventoryView.OfBlock(pos)
    case entity: EntityIota =>
      given MinecraftServer = summon[CastingEnvironment].getWorld.getServer
      entity.getEntity match
        case p: PlayerEntity =>
          summon[CastingEnvironment] match
            case env: PlayerBasedCastEnv =>
              if env.getCaster == p then
                InventoryView.OfEntity(p.getUuid)
              else
                throw MishapOthersName(p)
        case e => InventoryView.OfEntity(e.getUuid)
  hexXplat.getArithmeticRegistry("view") = arith("view",
    Arithmetic.ADD -> {
      (view1: BoxedView.Instance, view2: BoxedView.Instance) => Seq(BoxedView.Instance(InventoryView.OfSum(view1.view, view2.view)))
    }
  )
  setConceptScale[FluidVariant](81000)
  setConceptScale[SingletonVariant.media.type](10000)
  setConceptScale[SingletonVariant.heat.type](20)
  Patterns.register("conceptavailable", sw"wedwqwdewwaqaa"):
    Patterns.mkConstAction(2):
      case Seq(BoxedView.Instance(target), VariantIota(typ, _)) =>
        Using.resource(Transaction.openOuter()):
          case tx@given Transaction =>
            val amt = target.tryExtract(typ, Long.MaxValue) / conceptScale(ClassTag(typ.getClass))
            tx.abort()
            Seq(DoubleIota(amt))
      case Seq(_: BoxedView.Instance, perp) => throw MishapInvalidIota(perp, 0, VariantIota.typeName)
      case Seq(perp, _) => throw MishapInvalidIota(perp, 1, BoxedView.typeName)
  Patterns.register("conceptremaining", sw"wedwqwdewadedd"):
    Patterns.mkConstAction(2):
      case Seq(BoxedView.Instance(target), VariantIota(typ, _)) =>
        Using.resource(Transaction.openOuter()):
          case tx@given Transaction =>
            val amt = target.tryInsert(typ, Long.MaxValue) / conceptScale(ClassTag(typ.getClass))
            tx.abort()
            Seq(DoubleIota(amt))
      case Seq(_: BoxedView.Instance, perp) => throw MishapInvalidIota(perp, 0, VariantIota.typeName)
      case Seq(perp, _) => throw MishapInvalidIota(perp, 1, BoxedView.typeName)
  Patterns.register("moveconcept", se"wawdwawqdewewedqwawdwaw"):
    Patterns.mkConstAction(4):
      case Seq(isIota[BoxedView.Instance, 3](BoxedView.Instance(from)), isIota[BoxedView.Instance, 2](BoxedView.Instance(into)), typ: VariantIota[?], isIota[DoubleIota, 0](count)) =>
        val key = ClassTag(typ.data.getClass)
        val scale = conceptScale(key)
        @tailrec def negotiate(limit: Long): Seq[Iota] =
          val compromise = Using.resource(Transaction.openOuter()):
            case tx@given TransactionContext =>
              val extracted = (from: InventoryView).tryExtract((typ: VariantIota[?]).data, limit)
              val inserted = (into: InventoryView).tryInsert((typ: VariantIota[?]).data, extracted)
              if extracted == inserted then
                tx.commit()
                return Seq(DoubleIota(inserted / scale))
              else
                inserted
          negotiate(compromise)
        val initialLimit = Using.resource(Transaction.openOuter()):
          case tx@given TransactionContext =>
            val value = (into: InventoryView).tryInsert(typ.data, (scale * count.getDouble).toLong)
            tx.abort()
            value
        negotiate(initialLimit)

  Patterns.register("moveentity", se"edeeewawdweaaddaqwqwqaddaaewdwawewdqd"):
    Patterns.mkConstAction(3):
      case Seq(isIota[BoxedView.Instance, 2](from), isIota[BoxedView.Instance, 1](into), isIota[DoubleIota, 0](count)) =>
        Using.resource(Transaction.openOuter()):
          case tx@given TransactionContext =>
            val count = from.view.entities.count(into.view.teleportEntity)
            if count > 0 then tx.commit()
            Seq(DoubleIota(count))
  Patterns.register("thinkaboutit", e"awqawewaqw"):
    Patterns.mkConstAction(1):
      case Seq(iota) =>
        def dieOfBadType() = throw MishapInvalidIota.of(iota, 0, "hexic:not_eldritch_horror")
        iota match
          case BoxedView.Instance(view) =>
            Using.resource(Transaction.openOuter()):
              case given TransactionContext =>
                Seq(ListIota(view.contents.toSeq))
          case i: ItemStackIota => Seq(if i.getItemStack.getItem == Items.AIR then NullIota() else VariantIota(ItemVariant.of(i.getItemStack), RegistryKey.of(VariantIota.key, Identifier("item"))))
          case i: ItemTypeIota => Seq(if i.getItem == Items.AIR then NullIota() else VariantIota(ItemVariant.of(i.getItem), RegistryKey.of(VariantIota.key, Identifier("item"))))
          case v: Vec3Iota =>
            val pos = BlockPos.ofFloored(v.getVec3)
            summon[CastingEnvironment].assertPosInRange(pos)
            val state = summon[BlockView].getBlockState(pos)
            if state.isAir then
              Seq(NullIota())
            else
              state.getFluidState.getFluid match
                case Fluids.EMPTY =>
                  state.getBlock.asItem match
                    case null | Items.AIR =>
                      state.getBlock.getPickStack(summon, pos, state) match
                      case null | Items.AIR =>
                        Seq(NullIota())
                      case item => Seq(VariantIota(ItemVariant.of(item), RegistryKey.of(VariantIota.key, Identifier("item"))))
                    case item => Seq(VariantIota(ItemVariant.of(item), RegistryKey.of(VariantIota.key, Identifier("item"))))
                case fluid => Seq(VariantIota(FluidVariant.of(fluid), RegistryKey.of(VariantIota.key, Identifier("fluid"))))
          case e: EntityIota if !e.getEntity.isRemoved =>
            e.getEntity match
              case s: ItemEntity => Seq(if s.getStack.getItem == Items.AIR then NullIota() else VariantIota(ItemVariant.of(s.getStack), RegistryKey.of(VariantIota.key, Identifier("item"))))
              case s: ItemFrameEntity => Seq(if s.getHeldItemStack.getItem == Items.AIR then NullIota() else VariantIota(ItemVariant.of(s.getHeldItemStack), RegistryKey.of(VariantIota.key, Identifier("item"))))
              case _ => dieOfBadType()
          case m: MoteIota if MediafiedItemManager.contains(m.getItemIndex) =>
            Seq(
              if m.getItem == Items.AIR then
                NullIota()
              else
                VariantIota(ItemVariant.of(m.getItem, m.getTag), RegistryKey.of(VariantIota.key, Identifier("item"))))
          case _ => dieOfBadType()
//noinspection UnstableApiUsage
case class VariantIota[T: ClassTag](data: TransferVariant[T], key: RegistryKey[VariantIota.Reader]) extends Iota(VariantIota, data):
  override def isTruthy: Boolean = true
  override def toleratesOther(that: Iota): Boolean =
    that match
      case v: VariantIota[T] => key == v.key && data == v.data
      case _ => false
  override def serialize: NbtElement =
    data.toNbt tap(_.putString("type", key.getValue.toString))
//noinspection UnstableApiUsage
object VariantIota extends IotaType[VariantIota[?]], Registrar[VariantIota.Reader]("transfer_variants"):
  given IotaType[VariantIota[?]] = this
  type Reader = NbtCompound => Option[VariantIota.TaggedVariant]
  trait TaggedVariant:
    type T: ClassTag
    def variant: TransferVariant[T]
    def display: Text
  def color: Int = 0x720a0a
  private[hexic] def parseVariant(c: NbtCompound): Option[(TaggedVariant, RegistryKey[Reader])] =
    for
      i <- Option(Identifier.tryParse(c.getString("type")))
      entry <- Option.fromNullable(registry.get(i))
      parsed <- entry(c)
    yield (parsed, RegistryKey.of(VariantIota, i))
  end parseVariant
  def deserialize(using NbtElement, ServerWorld): VariantIota[?] | Null =
    summon[NbtElement] match
      case c: NbtCompound =>
        parseVariant(c) match
          case Some((t, k)) =>
            import t.given
            VariantIota(t.variant, k)
          case None => null
      case _ => null
  end deserialize
  override def display(e: NbtElement): Text = parseVariant(e.downcast).fold(NullIota.DISPLAY)(_._1.display)
  end display
  registry(Identifier("item")) = c =>
    val s = ItemVariant.fromNbt(c)
    Option.unless(s.isBlank):
      new TaggedVariant:
        type T = Item
        def variant: TransferVariant[Item] = s
        def display: Text = t"⌠${ItemInlineData(s.toStack).asText(true).copy().styled(InlineAPI.INSTANCE.withSizeModifier(_, 1.5))}⌡"
          .styled(_.withColor(0x7c7145).withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_ITEM, HoverEvent.ItemStackContent(s.toStack))))
  registry(Identifier("fluid")) = c =>
    val s = FluidVariant.fromNbt(c)
    Option.unless(s.isBlank):
      new TaggedVariant:
        type T = Fluid
        def variant: TransferVariant[Fluid] = s
        def display: MutableText =
          val bs = s.getFluid.getDefaultState.getBlockState
          t"(${bs.getBlock.getName.styled(_.withColor(bs.getMapColor(null, null).color))})".styled(_.withColor(0x3c5e34))
  registry("media") = c =>
    Some(new TaggedVariant:
      type T = SingletonVariant
      def variant = SingletonVariant.media
      def display: Text = Text.literal("Media").styled(_.withColor(0x74b3f2)))
  registry("heat") = c =>
    Some(new TaggedVariant:
      type T = SingletonVariant
      def variant = SingletonVariant.heat
      def display: Text = Text.literal("Heat").styled(_.withColor(0xe08355)))

//noinspection UnstableApiUsage
class SingletonVariant extends TransferVariant[SingletonVariant]:
  def getNbt = NbtCompound()
  def getObject: this.type = this
  def isBlank = false
  def toNbt = NbtCompound()
  def toPacket(buf: net.minecraft.network.PacketByteBuf): Unit = ()
object SingletonVariant:
  // 2026-01-01 pool: implementation restriction: these must be proper subclasses since classtags are relevant
  object media extends SingletonVariant()
  object heat extends SingletonVariant()
  object energy extends SingletonVariant()