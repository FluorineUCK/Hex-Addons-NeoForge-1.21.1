package org.eu.net.pool
package phlib

import java.util.NoSuchElementException
import scala.util.{Failure, Success, Try, boundary}

class Scope[T](initial: T):
  private object local extends InheritableThreadLocal[Option[T]]:
    override def initialValue: Option[T] = None
  def enter[R](newValue: T)(body: => R): R =
    val oldValue = local.get
    try
      local.set(Some(newValue))
      body
    finally
      local.set(oldValue)
  def value: T = local.get.getOrElse(initial)
  def valueIfChanged: Option[T] = local.get
  def isChanged: Boolean = local.get.isDefined
object Scope:
  given [T] => Conversion[Scope[T], T] = _.value

class Portal[T]:
  private val scope = Scope[Try[T] => Nothing]:
    case Success(value) => sys.exit(0)
    case Failure(exc) => { exc.printStackTrace(); sys.exit(1) }
  def enter[R](body: => R): Either[T, R] =
    def wrap: Either[Try[T], R] = scope.enter(x => return Left(x))(Right(body))
    wrap.left.map(_.get)
  def isEntered: Boolean = scope.isChanged
  def exit(value: T): Nothing = scope(Success(value))
  def abort(error: Throwable): Nothing = scope(Failure(error))
  def tryExit(value: T): Unit = for jump <- scope.valueIfChanged do jump(Success(value))
  def tryAbort(error: Throwable): Unit = for jump <- scope.valueIfChanged do jump(Failure(error))