package org.eu.net.pool.hexic

import com.chocohead.mm.api.ClassTinkerers
import com.google.common.base.Charsets

import java.lang.instrument.Instrumentation
import net.bytebuddy.agent.ByteBuddyAgent
import net.minecraft.Bootstrap
import net.minecraft.item.Item
import org.objectweb.asm.tree.{ClassNode, FieldInsnNode, InsnList, InsnNode, InvokeDynamicInsnNode, LdcInsnNode, MethodInsnNode, MethodNode, MultiANewArrayInsnNode, TypeInsnNode}

import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain
import org.objectweb.asm.{ClassReader, ClassWriter, Handle, Opcodes, Type}

import java.nio.file.{Files, Path}
import scala.annotation.{showAsInfix, tailrec}
import scala.collection.mutable
import scala.jdk.CollectionConverters.given
import scala.quoted
import scala.quoted.{Expr, Quotes}
import scala.util.NotGiven
import scala.util.chaining.given

given instrumentation: Instrumentation = ByteBuddyAgent.install()
class ChangesFlag private[hexic]():
  private[hexic] var value = false

def madeChanges(using flag: ChangesFlag)(): Unit = flag.value = true
def addTransformer(body: (ClassLoader, ProtectionDomain, ChangesFlag) ?=> (name: String, node: ClassNode) => Unit)(using i: Instrumentation): Unit =
  i.addTransformer:
    new ClassFileTransformer:
      override def transform(using loader: ClassLoader, name: String, existing: Class[?], domain: ProtectionDomain, buffer: Array[Byte]): Array[Byte] =
//        println("Transformer body!")
        given node: ClassNode = ClassNode()
        ClassReader(buffer).accept(node, 0)
        given flag: ChangesFlag = ChangesFlag()
        body(name, node)
        if flag.value then
          val writer = ClassWriter(3)
          node.accept(writer)
          writer.toByteArray
        else
          buffer

      override def transform(module: Module, loader: ClassLoader, name: String, existing: Class[?], domain: ProtectionDomain, buffer: Array[Byte]): Array[Byte] =
        transform(using loader, name, existing, domain, buffer)

case class Peekable[T](underlying: Iterator[T]) extends Iterator[T]:
  private var peeked: Option[Option[T]] = None
  override def next(): T = peeked match
    case Some(Some(value)) =>
      peeked = None
      value
    case _ =>
      peeked = None
      underlying.next()
  override def hasNext: Boolean = peeked.exists(_.isDefined) || underlying.hasNext
  def peek: Option[T] = peeked match
    case Some(value) => value
    case None =>
      val x = underlying.nextOption()
      peeked = Some(x)
      x
  def peekWhile(cond: T => Boolean): Iterator[T] = new Iterator[T]:
    override def hasNext: Boolean = peek.exists(cond)
    override def next(): T =
      if hasNext then
        Peekable.this.next()
      else
        throw java.util.NoSuchElementException()

@showAsInfix type :>[K, +V] = Map[K, V]

val transformedClasses: String :> TransformedClass =
  val file = Path.of("config/remapped_classes.lst")
  if Files.exists(file) then
    val lines = Peekable(Files.readAllLines(file, Charsets.UTF_8).iterator.asScala.zipWithIndex)
    val rows = for
      (line, i) <- lines
      cells = line split ' '
      if cells.length == 2
      Array(from, to) = cells
    yield
      val fields = mutable.Map[String, (String, Option[String])]()
      val methods = mutable.Map[(String, String), (String, String)]()
      lines.peekWhile(_._1.startsWith("@")).foreach: (l, i) =>
        l.split(' ') match
          case Array("@field", from, to) => fields(from) = (to, None)
          case Array("@field", from, desc, to) => fields(from) = (to, Some(desc))
          case Array("@method", from, desc, to) => methods((from, desc)) = (to, desc)
          case Array("@method", from, fromDesc, to, toDesc) => methods((from, fromDesc)) = (to, toDesc)
          case l => System.err.println(s"Illegal remapped_classes.lst line ${l.toSeq} at line $i")
      val tfClass = TransformedClass(to, fields.toMap, methods.toMap)
      println(s"Remapping '$from' to $tfClass")
      from -> tfClass
    rows.toMap
  else
    Map.empty
case class TransformedClass(newName: String, fields: String :> (String, Option[String]), methods: (String, String) :> (String, String))

extension (x: String)
  def slashes: String = x.replace('.', '/')
  def dots: String = x.replace('/', '.')

def remapDesc(desc: String) = transformedClasses.get(desc).fold(transformedClasses.foldLeft(desc):
  case (desc, (from, TransformedClass(to, _, _))) => desc.replace(s"L$from;", s"L$to;"))(_.newName)

def warCrimes(): Unit =
  try
    ClassTinkerers
      .enumBuilder("at.petrak.hexcasting.api.casting.eval.ResolvedPatternType", Integer.TYPE, Integer.TYPE, java.lang.Boolean.TYPE)
      .addEnum("HEXIC$ECHO_SHARD_ABSORBED", 0x0a5060: Integer, 0x29dfeb: Integer, java.lang.Boolean.TRUE)
      .build()
    println(s"Class remappings: $transformedClasses")
    for (k, v) <- transformedClasses do
      val node = ClassNode()
      node.visit(45, Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, k, null, "java/lang/Object", Array.empty)
      val writer = ClassWriter(3)
      node.methods.add(MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, "<clinit>", "()V", null, Array.empty).tap: m =>
        m.instructions = InsnList()
        for k <- Array(
          TypeInsnNode(Opcodes.NEW, "java/lang/AssertionError"),
          InsnNode(Opcodes.DUP),
          LdcInsnNode(s"Class ${k.dots} has been replaced"),
          MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/AssertionError", "<init>", "(Ljava/lang/String;)V"),
          InsnNode(Opcodes.ATHROW),
        ) do m.instructions.add(k)
      )
      node.accept(writer)
      ClassTinkerers.define(k.dots, writer.toByteArray)
    addTransformer: (name: String, c: ClassNode) =>
      assert(name == name.dots)
      if transformedClasses isDefinedAt name.slashes then panic(name)
      println(s"Remapping $name")
      madeChanges() // not gonna try checking
      c.methods.forEach: m =>
        m.desc = remapDesc(m.desc)
        m.instructions.forEach:
          case i: TypeInsnNode => i.desc = remapDesc(m.desc)
          case i: FieldInsnNode =>
            i.owner match
              case transformedClasses(entry) =>
                i.owner = entry.newName
                i.name match
                  case entry.fields(newName, newDesc) =>
                    i.name = newName
                    newDesc.foreach(i.desc = _)
                  case _ =>
              case _ =>
            i.desc = remapDesc(i.desc)
          case i: MethodInsnNode =>
            i.owner match
              case transformedClasses(entry) =>
                i.owner = entry.newName
                (i.name, i.desc) match
                  case entry.methods(newName, newDesc) =>
                    i.name = newName
                    i.desc = newDesc
                  case _ =>
              case _ =>
            i.desc = remapDesc(i.desc)
          case i: InvokeDynamicInsnNode =>
            i.bsm = i.bsm.pipe: b =>
              b.getOwner match
                case transformedClasses(entry) =>
                  (b.getName, b.getDesc) match
                    case entry.methods(newName, newDesc) => Handle(b.getTag, entry.newName, newName, newDesc, b.isInterface)
                    case (entry.fields(newName, newDesc), _) => Handle(b.getTag, entry.newName, newName, newDesc.getOrElse(b.getDesc), b.isInterface)
                    case _ => Handle(b.getTag, entry.newName, b.getName, b.getDesc, b.isInterface)
                case _ => b
            // we should probably transform BSM args, and name/desc
          case i: LdcInsnNode =>
            i.cst match
              case t: Type => i.cst = Type.getType(remapDesc(t.getDescriptor))
              case _ =>
          case i: MultiANewArrayInsnNode =>
            i.desc = remapDesc(i.desc)
      c.fields.forEach: f =>
        f.desc = remapDesc(f.desc)
      c.visibleAnnotations.forEach: a =>
        a.desc = remapDesc(a.desc)
        for i <- 0 until a.values.size by 2 do
          a.values.get(i+1) match
            case t: Type => a.values.set(i+1, Type.getType(remapDesc(t.getDescriptor)))
            case _ => Object()
      c.invisibleAnnotations.forEach: a =>
        a.desc = remapDesc(a.desc)
        for i <- 0 until a.values.size by 2 do
          a.values.get(i+1) match
            case t: Type => a.values.set(i+1, Type.getType(remapDesc(t.getDescriptor)))
            case _ => Object()
  catch case e: Throwable =>
    e.printStackTrace()
    panic(s"$e")

@tailrec
def panic(reason: String): Nothing =
  Bootstrap.SYSOUT.println(s"thread '${Thread.currentThread.getName}' panicked at '$reason'")
  Bootstrap.SYSOUT.flush()
  Runtime.getRuntime.halt(101)
  panic(reason)