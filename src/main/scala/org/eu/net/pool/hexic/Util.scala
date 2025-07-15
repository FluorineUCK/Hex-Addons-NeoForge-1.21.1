//noinspection NotImplementedCode
package org.eu.net.pool.hexic

import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic
import at.petrak.hexcasting.api.casting.arithmetic.operator.Operator
import at.petrak.hexcasting.api.casting.arithmetic.predicates.IotaMultiPredicate
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect
import at.petrak.hexcasting.api.casting.eval.vm.{CastingImage, SpellContinuation}
import at.petrak.hexcasting.api.casting.eval.{CastingEnvironment, OperationResult}
import at.petrak.hexcasting.api.casting.iota.{Iota, IotaType}
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds
import com.mojang.serialization.{Codec, DynamicOps}
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.registry.{Registries, Registry, RegistryKey, SimpleRegistry}
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier

import java.{lang, util}
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.given
import scala.quoted.*
import scala.util.chaining.given

given [T]: Conversion[RegistryKey[Registry[T]], ? <: Registry[T]] = Registries.REGISTRIES.asInstanceOf[Registry[Registry[T]]].get(_)
given Conversion[String, Identifier] = Identifier.of("hexic", _)

trait Registrar[T](val id: Identifier):
  val key: RegistryKey[Registry[T]] = RegistryKey.ofRegistry[T](id)
  lazy val registry: SimpleRegistry[T] = FabricRegistryBuilder.createSimple(key).buildAndRegister()

object Registrar:
  given [T]: Conversion[Registrar[T], RegistryKey[Registry[T]]] = _.key
  given [T]: Conversion[Registrar[T], Registry[T]] = _.registry

@tailrec
def uninline(using q: Quotes)(t: q.reflect.Term): q.reflect.Term = t match
  case q.reflect.Inlined(_, _, r) => uninline(r)
  case r => r

def unless(cond: Boolean)(body: => Unit): Unit = if (!cond) body

def eitherTypes(using q: Quotes)(t: q.reflect.TypeRepr): Seq[q.reflect.TypeRepr] =
  import q.reflect.*
  t match
    case OrType(a, b) => eitherTypes(a) ++ eitherTypes(b)
    case t => Seq(t)

// My level of sanity is slightly lower than the Hex Caster's sanity.
def arithImpl(using q: Quotes)(name: Expr[String], args: Expr[Seq[(HexPattern, AnyRef)]]): Expr[Arithmetic] =
  import q.reflect.*
  val Varargs(xs) = args: @unchecked
  val ops = xs.map { case '{ ($p: HexPattern) -> $e } => (p, e) }.toMap[Expr[HexPattern], Expr[Any]]
  '{
    new Arithmetic:
      override def arithName: String = $name
      override def opTypes: lang.Iterable[HexPattern] = ${ Expr.ofSeq(ops.keys.toSeq) }.asJava
      override def getOperator(pattern: HexPattern): Operator = ${
        Match('pattern.asTerm, ops.map(i =>
          val (p, c) = i
          uninline(using q)(c.asTerm) match
            case Lambda(a, r) =>
              a.foreach: e =>
                val ValDef(n, tyt, _) = e: @unchecked
                unless (tyt.tpe <:< TypeRepr.of[Iota]):
                  report.error("parameter type must be Iota, or a subtype thereof", tyt.pos)
              CaseDef(p.asTerm, None, '{
                new Operator(${Expr(a.size)}, xi =>
                  val i = xi.iterator
                  ${
                    a.foldLeft('{ true }) { (t, v) =>
                      val ValDef(_, ty, _) = v
                      ty.tpe.asType match
                        case '[typ] => '{ ${t} && i.hasNext && i.next.isInstanceOf[typ] }
                    }.asExprOf[Boolean]
                  }
                ):
                  override def operate(env: CastingEnvironment, img: CastingImage, cont: SpellContinuation): OperationResult =
                    val stack = img.getStack.asScala.toSeq
                    val args = stack.takeRight(${Expr(a.size)})
                    // I'm fairly certain the remainder of this method is considered a war crime
                    ${
                      Block(a.zipWithIndex.map { p =>
                        val (v@ValDef(n, ty, _), i) = p
                        ty.tpe.asType match
                          case '[t] => ValDef.copy(v)(n, ty, Some('{ stack(${Expr(i)}).asInstanceOf[t] }.asTerm))
                      }, {
                        r.tpe.asType match
                          case '[Seq[Iota]] => '{ OperationResult(img.withStack(s => s.dropRight(${Expr(a.size)}) ++ ${r.asExprOf[Seq[Iota]]}), util.ArrayList[OperatorSideEffect](), cont, HexEvalSounds.NORMAL_EXECUTE) }.asTerm
                          case _ => report.errorAndAbort(s"Operator return type ${r.tpe.show} is not statically supported", r.pos)
                      }).asExprOf[OperationResult]
                    }
              }.asTerm)
        ).toList).asExprOf[Operator]
      }
  }.tap(e => report.info(e.show, name.asTerm.pos))

trait OperationResultFactory[T]:
  def apply(self: T)(op: OperationResult): OperationResult

given [T]: Conversion[RegistryEntry[T], T] = _.value()
given [T]: Conversion[Registry[T], RegistryKey[? <: Registry[T]]] = _.getKey

given [T <: Iota]: Conversion[T, NbtCompound] = IotaType.serialize(_)
given ServerWorld => Conversion[NbtCompound, Iota | Null] = IotaType.deserializeIota(_, summon)

given [T: Codec, R: DynamicOps]: Conversion[T, R] = summon[Codec[T]].encodeStart(summon, _).getOrThrow(false, _ => {})
given [T: Codec, R: DynamicOps]: Conversion[R, T] = summon[Codec[T]].decode(summon, _).getOrThrow(false, _ => {}).getFirst
given [T: DynamicOps, U: DynamicOps]: Conversion[T, U] = summon[DynamicOps[T]].convertTo(summon, _)