import java.util.concurrent.atomic.AtomicInteger

private def bomb(): Unit =
  val answer = AtomicInteger(42)
  smuggle[Unit]:
    println(answer.get())

@main
def Explode(): Unit =
  unsmuggle[Unit] // crashes compiler - answer is out of scope