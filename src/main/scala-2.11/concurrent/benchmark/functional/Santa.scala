package concurrent.benchmark.functional

import concurrent.future._

import scala.annotation.tailrec

object Santa {

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
    get(future) match {
      case Some(x) => Future(x)
      case None => fail()
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
    val promise = Promise[T]()
    tryCompleteWith(promise, collectN(toCollect, helper))
    promise.future
  }

  def santaLoop(rounds: Int) = {
    var elves = collectHelper(elf(), 3)
    var reindeers = collectHelper(deer(), 9)
    for (a <- 0 until rounds) {
      val group = when(first(elves :: reindeers :: Nil), isSantaSleeping)
      get(group) match {
        case Some(Elf) =>
          santaSleeps = false
          elves = collectHelper(elf(), 3)
          santaSleeps = true
        case Some(Reindeer) =>
          santaSleeps = false
          reindeers = collectHelper(deer(), 9)
          santaSleeps = true
        case _ => println("Error") // there might be an exception or none which leads to an exception
      }
    }
  }
}