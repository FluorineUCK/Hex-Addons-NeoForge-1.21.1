package org.eu.net.pool.hexxychests
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.mishaps.Mishap
import com.mojang.serialization.Codec
import net.fabricmc.fabric.api.event.{Event, EventFactory}
import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant
import net.fabricmc.fabric.api.transfer.v1.transaction.{Transaction, TransactionContext}
import net.minecraft.block.BlockState
import net.minecraft.entity.Entity
import net.minecraft.item.Item
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import org.eu.net.pool.phlib.{*, given}
import org.slf4j.{Logger, LoggerFactory}

private[hexxychests] given Conversion[String, Identifier] = Identifier.of("hexxychests", _)
private[hexxychests] given Logger = LoggerFactory.getLogger("hexxychests")

trait InventoryView:
  def apply(idx: Int)(using CastingEnvironment): Option[SlotReference] = None
  @throws[Mishap]
  def tryWithdraw(variant: TransferVariant[?], amount: Long)(using TransactionContext, CastingEnvironment): Long = 0
  def entities(using TransactionContext): Iterable[Entity] = Iterable()
  @throws[Mishap]
  def teleportEntity(ent: Entity)(using TransactionContext, CastingEnvironment): Unit = ??? // TODO: make a mishap for this
object InventoryView extends Registrar[InventoryView.Type[?]]("inventory"):
  trait Type[T <: InventoryView]:
    def serialize(view: T): NbtCompound
    def deserialize(data: NbtCompound)(using ServerWorld): T
  object Events:
    val forEntity: Event[Entity => ServerWorld ?=> Seq[InventoryView]] = EventFactory.createArrayBacked[Entity => ServerWorld ?=> Seq[InventoryView]](classOf, _ => Seq(), fns => e => fns.flatMap(_(e)))
    val forBlock: Event[(BlockPos, BlockState) => ServerWorld ?=> Seq[InventoryView]] = EventFactory.createArrayBacked[(BlockPos, BlockState) => ServerWorld ?=> Seq[InventoryView]](classOf, (_, _) => Seq(), fns => (pos, state) => fns.flatMap(_(pos, state)))
trait SlotReference:
  def item(using CastingEnvironment): Item
  def nbt(using CastingEnvironment): Option[NbtCompound]
  def count(using CastingEnvironment): Long
  @throws[Mishap]
  def nbt_=(using Transaction, CastingEnvironment)(nbt: Option[NbtCompound]): Unit
  @throws[Mishap]
  def count_=(using Transaction, CastingEnvironment)(count: Long): Unit
object SlotReference extends Registrar[SlotReference.Type[?]]("slot"):
  class Type[T <: SlotReference : Codec]

def init() =
  println("Hello, world!")