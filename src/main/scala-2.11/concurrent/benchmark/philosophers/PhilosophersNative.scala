package concurrent.benchmark.philosophers

import java.util.concurrent.CountDownLatch

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

object PhilosophersNative {

  /**
    * Tries to success two promises at the same time.
    */
  def multiTrySuccess[A](promises: Traversable[(Promise[A], A)]): Boolean = {
    val allEmpty = promises.forall(promise => !promise._1.isCompleted)
    allEmpty match {
      case false => false
      case _ => promises.forall(x => x._1.trySuccess(x._2))
    }
  }

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
        philosopher onComplete { case _ => latch.countDown() }
      }
      // wait for all until we start a new round
      latch.await()
    }
  }

  def main(args: Array[String]) {
    philosopherRace(5)
  }
}
