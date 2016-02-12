package concurrent.benchmark.philosophers

import java.util.concurrent.CountDownLatch

import scala.concurrent.ExecutionContext
import scala.concurrent.stm._

object PhilosophersSTM {

  private final implicit val context: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private case class Philosopher(id: Int, left: Ref[Option[Int]], right: Ref[Option[Int]], latch: CountDownLatch) extends Runnable {

    override def run() {
      atomic { implicit txn =>
        if (left().isEmpty && right().isEmpty) {
          left() = Some(id)
          right() = Some(id)
        }
      }
      latch.countDown()
    }
  }

  /**
    * Problem callbacks dont block. There is really nothing here that blocks
    */
  def philosopherRace(philos: Int): Unit = {
    // equal amount of rounds set to 100
    for (a <- 0 until 100) {
      val latch = new CountDownLatch(philos)
      val forks = (for (i <- 1 to philos) yield Ref(None: Option[Int])).toList
      val philosophers = for (i <- forks.indices) yield Philosopher(i + 1, forks(i), forks((i + 1) % forks.size), latch)
      for (philosopher <- philosophers) {
        context.prepare().execute(philosopher)
      }
      latch.await()
    }
  }
}
