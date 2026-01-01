package org.eu.net.pool
package hexic

import scala.quoted.Expr
import com.chocohead.mm.api.EnumAdder
import scala.quoted.Quotes
import net.minecraft.util.DyeColor

def generateAppropriateColors_impl(e: Expr[EnumAdder])(using q: Quotes): Expr[EnumAdder] =
  // FIXME: this is wrong
  DyeColor.values.foldLeft(e)((e, c) => '{ ${ e }.addEnum(${ Expr(s"HEXIC$$PEN_WITH_COLOR_${c.asString}") }, (${Expr(c.getMapColor.color)}: Int): Integer, (${Expr(c.getSignColor)}: Int): Integer, java.lang.Boolean.TRUE) })