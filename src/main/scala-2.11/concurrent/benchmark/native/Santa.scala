package concurrent.benchmark.native

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

object Santa {

  private final implicit val context: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  var santaSleeps = true

  sealed trait Helper

  case object Elf extends Helper

  case object Reindeer extends Helper

  def elf(): Future[Helper] = {
    Future {
      Elf
    }
  }

  def deer(): Future[Helper] = {
    Future {
      Reindeer
    }
  }

  def collect[T <: Helper](future: Future[T]): Future[T] = {
    Await.result(future, Duration.Inf) match {
      case x if x.isInstanceOf[T] => Future(x)
      case _ => Future.failed(new RuntimeException)
    }
  }

  @tailrec
  def collectN[T <: Helper](amount: Int, future: Future[T]): Future[T] = amount match {
    case 1 => future
    case _ => collectN(amount - 1, collect(future))
  }

  def isSantaSleeping(h: Helper): Boolean = {
    santaSleeps
  }

  def collectHelper[T <: Helper](helper: Future[T], toCollect: Int): Future[T] = {
    val promise = Promise[T]
    promise.tryCompleteWith(collectN(toCollect, helper))
    promise.future
  }

  def santaLoop(rounds: Int) = {
    var elves = collectHelper(elf(), 3)
    var reindeers = collectHelper(deer(), 9)
    for (a <- 0 until rounds) {
      val group = Future.firstCompletedOf(elves :: reindeers :: Nil).filter(isSantaSleeping)
      Await.result(group, Duration.Inf) match {
        case Elf => // elves need help for r&d
          santaSleeps = false
          elves = collectHelper(elf(), 3)
          santaSleeps = true
        case Reindeer => // deers deliver the toys
          santaSleeps = false
          reindeers = collectHelper(deer(), 9)
          santaSleeps = true
        case _ => println("Error") // there might be an exception or none which leads to an exception
      }
    }
  }
}