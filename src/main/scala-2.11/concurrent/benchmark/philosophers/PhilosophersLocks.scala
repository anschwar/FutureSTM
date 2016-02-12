package concurrent.benchmark.philosophers

import java.util.concurrent.locks.{Lock, ReentrantLock}
import java.util.concurrent.{CountDownLatch, TimeUnit}

import scala.concurrent.ExecutionContext

object PhilosophersLocks {

  private final implicit val context: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private case class Philosopher(id: Int, left: Lock, right: Lock, latch: CountDownLatch) extends Runnable {

    override def run() {
      try {
        if (left.tryLock(5, TimeUnit.NANOSECONDS)) {
          if (right.tryLock(5, TimeUnit.NANOSECONDS)) {
            right.unlock()
          }
          left.unlock()
        }
        latch.countDown()
      } catch {
        case e: Exception => e.printStackTrace()
      }
    }
  }

  /**
    * Problem callbacks dont block. There is really nothing here that blocks
    */
  def philosopherRace(philos: Int): Unit = {
    // equal amount of rounds set to 100
    for (a <- 0 until 100) {
      val latch = new CountDownLatch(philos)
      val forks = (for (i <- 1 to philos) yield new ReentrantLock()).toList
      val philosophers = for (i <- forks.indices) yield Philosopher(i + 1, forks(i), forks((i + 1) % forks.size), latch)
      for (philosopher <- philosophers) {
        context.prepare().execute(philosopher)
      }
      latch.await()
    }
  }
}
