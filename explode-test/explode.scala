import scala.quoted.{Expr, Quotes, Type}

private var smuggled: Option[Quotes#reflectModule#Term] = None

inline def smuggle[T](x: => T) = ${ smuggle_impl[T]('x) }
inline def unsmuggle[T]: T = ${ unsmuggle_impl[T] }

def smuggle_impl[T: Type](x: Expr[T])(using q: Quotes) =
  import q.reflect.*
  smuggled = Some(x.asTerm)
  '{()}
def unsmuggle_impl[T: Type](using q: Quotes): Expr[T] =
  import q.reflect.*
  smuggled.get.asInstanceOf[Term].asExprOf[T]