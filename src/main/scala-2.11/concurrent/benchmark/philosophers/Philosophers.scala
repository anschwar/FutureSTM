package concurrent.benchmark.philosophers

import java.util.concurrent.CountDownLatch

import concurrent.future._

object Philosophers {

  private[philosophers] final case class Philosopher(id: Int)

  def philosopher(id: Int, left: Promise[Int], right: Promise[Int]): Future[Philosopher] = {
    Future {
      multiTrySuccess((left, id) ::(right, id) :: Nil) match {
        case false => null
        case _ => Philosopher(id)
      }
    }
  }

  /**
    * Problem callbacks dont block. There is really nothing here that blocks
    */
  def philosopherRace(philos: Int): Unit = {
    // equal amount of rounds set to 100
    for (a <- 0 until 100) {
      // same amount of forks as philos
      val latch = new CountDownLatch(philos)
      val forks = (for (i <- 1 to philos) yield Promise[Int]()).toList
      val philosophers = for (i <- 0 until philos) yield philosopher(i + 1, forks(i), forks((i + 1) % forks.size))
      for (philosopher <- philosophers) {
        // either one or the other will be executed because they exclude each other
        onSuccess(philosopher, (philosopher: Philosopher) => latch.countDown())
        onFailure(philosopher, () => latch.countDown())
      }
      // wait for all until we start a new round
      latch.await()
    }
  }

  def main(args: Array[String]) {
    philosopherRace(5)
  }
}
