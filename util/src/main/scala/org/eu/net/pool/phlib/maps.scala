package org.eu.net.pool
package phlib

import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.NullIota
import net.minecraft.nbt.NbtCompound
import at.petrak.hexcasting.api.casting.iota.IotaType
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtElement
import net.minecraft.text.{MutableText, Text}
import net.minecraft.util.Formatting

import java.util
import java.util.List => JavaList
import scala.collection.immutable.ListMap
import net.minecraft.server.world.ServerWorld
import at.petrak.hexcasting.api.utils.HexUtils

case class MapIota(map: Map[NbtCompound, NbtCompound] = ListMap())(using val world: ServerWorld) extends Iota(MapIota, map):
  def get(key: Iota): Option[Iota] = map.get(IotaType.serialize(key)).map(IotaType.deserialize(_, summon))
  def apply(key: Iota): Iota = get(key) getOrElse NullIota()
  def -(keys: Iota*): MapIota = MapIota(map -- (keys map IotaType.serialize))
  def --(other: MapIota): MapIota = MapIota(map -- other.map.keys)
  def +(pairs: (Iota, Iota)*): MapIota = MapIota(map ++ pairs.map(p => (IotaType.serialize(p._1), IotaType.serialize(p._2))))
  def ++(other: MapIota): MapIota = MapIota(map ++ other.map)
  def update(f: map.type => Map[NbtCompound, NbtCompound]): MapIota = MapIota(f(map))
  def head: (Iota, Iota) = (IotaType.deserialize(map.head._1, summon), IotaType.deserialize(map.head._2, summon))
  def tail: MapIota = MapIota(map.tail)
  def init: MapIota = MapIota(map.init)
  def last: (Iota, Iota) = (IotaType.deserialize(map.last._1, summon), IotaType.deserialize(map.last._2, summon))
  def headOption: Option[(Iota, Iota)] = map.headOption.map(e => (IotaType.deserialize(e._1, summon), IotaType.deserialize(e._2, summon)))
  def lastOption: Option[(Iota, Iota)] = map.lastOption.map(e => (IotaType.deserialize(e._1, summon), IotaType.deserialize(e._2, summon)))
  def &(other: MapIota): MapIota = MapIota(map.filter(_._1 pipe other.map.contains))
  def ^(other: MapIota): MapIota = new MapIota:
    (map /: other.map): (map, e) =>
      e match
        case (k@map(_), _) => map - k
        case (_, _) => map + e
  def toList: JavaList[Iota] = map.flatMap((k, v) => Seq(k: Iota, v: Iota)).toSeq
  override def isTruthy: Boolean = map.nonEmpty
  override def toleratesOther(iota: Iota): Boolean = iota match
    case m: MapIota => map == m.map
    case _ => false
  override def serialize(): NbtElement = NbtList().tap: l =>
    map.toVector.foreach(p => NbtCompound().tap(c =>
      c.put("k", p._1)
      c.put("v", p._2)) tap l.add)
  override def size = map.toSeq.map(_.size + _.size - 1).sum + 1
  override def subIotas(): java.lang.Iterable[Iota] = toList
  def asJavaMap: java.util.Map[Iota, Iota] =
    val ret = util.LinkedHashMap[Iota, Iota]()
    for k -> v <- map do
      ret(IotaType.deserialize(k, summon)) = IotaType.deserialize(v, summon)
    ret
object MapIota extends IotaType[MapIota]:
  def color: Int = 0xb0641c
  def deserialize(using data: NbtElement, world: ServerWorld): MapIota =
    val l = HexUtils.downcast(data, NbtList.TYPE)
    val o = HexUtils.downcast(_, NbtCompound.TYPE)
    new MapIota(ListMap.from(l.map(o).map(c => (o(c("k")), o(c("v"))))))
  def display(data: NbtElement): Text =
    val items = HexUtils.downcast(data, NbtList.TYPE)
    val output: MutableText = "["
    output.styled(_.withColor(color))
    if items.nonEmpty then
      def castToCompound = HexUtils.downcast(_, NbtCompound.TYPE)
      val itemPair = items.map(castToCompound).iterator
      def writePair(pair: NbtCompound) =
        output.append(try IotaType.getDisplay(pair("k") pipe castToCompound) catch case e => t"∞" formatted Formatting.RED)
        output.append(" → ")
        output.append(try IotaType.getDisplay(pair("v") pipe castToCompound) catch case e => t"∞" formatted Formatting.RED)
      writePair(itemPair.next())
      while itemPair.hasNext do
        output.append(", ")
        writePair(itemPair.next())
    else
      output.append("→")
    output.append("]")
    output
  def fromJavaMap(map: java.util.Map[Iota, Iota])(using ServerWorld) = MapIota((map: collection.Map[Iota, Iota]).map(e => IotaType.serialize(e._1) -> IotaType.serialize(e._2)).toMap)
val mapArithmetic = 
  import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic.*
  arith("map",
    ADD -> ((a: MapIota, b: MapIota) => a ++ b),
    SUB -> ((a: MapIota, b: MapIota) => a -- b),
    ABS -> ((a: MapIota) => DoubleIota(a.map.size)),
    INDEX -> ((a: MapIota, k: Iota) => a(k)),
    UNAPPEND -> ((a: MapIota) => a.lastOption.map(p => Seq(p._1, p._2)).getOrElse(Seq(NullIota(), NullIota())) prepended a.init),
    INDEX_OF -> ((a: MapIota, v: Iota) =>
      val c = IotaType.serialize(v)
      a.update(_.filter(_._2 == c))),
    REMOVE -> ((a: MapIota, k: Iota) => a - k),
    REPLACE -> ((a: MapIota, k: Iota, v: Iota) => a + (k -> v)),
    UNCONS -> ((a: MapIota) => a.headOption.map(p => Seq(p._1, p._2)).getOrElse(Seq(NullIota(), NullIota())) prepended a.tail),
    AND -> ((a: MapIota, b: MapIota) => a & b),
    OR -> ((a: MapIota, b: MapIota) => b ++ a),
    XOR -> ((a: MapIota, b: MapIota) => a ^ b),
    GREATER -> ((a: MapIota, b: MapIota) => a.map.containsAll(b.map) && a.map != b.map),
    LESS -> ((a: MapIota, b: MapIota) => b.map.containsAll(a.map) && a.map != b.map),
    GREATER_EQ -> ((a: MapIota, b: MapIota) => a.map containsAll b.map),
    LESS_EQ -> ((a: MapIota, b: MapIota) => b.map containsAll a.map),
  )
