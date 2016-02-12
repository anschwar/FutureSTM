package concurrent

import scala.concurrent.ExecutionContext.Implicits.{global => executor}
import scala.concurrent.stm._

/**
  * Interface for the usage of futures in a more functional way. It delegates the action the the concrete implementation
  * which is within the FunctionalFuture. Those functions can only be accessed via this interface and not directly.
  */

package object future {

  final case class Future[A]() {
    val result: Ref[Result[A]] = Ref(Result.empty)

    override def equals(other: Any): Boolean =
      other match {
        case other: Future[A] => get(other) == get(this)
        case _ => false
      }

    override def toString: String = result.single.get.toString
  }

  object Future {
    def apply[A](func: => A): Future[A] = {
      val future = new Future[A]
      forkIO {
        atomic { implicit tnx => future.result() = Result(func) }
      }
      future
    }
  }

  final class Promise[A] {
    val future: Future[A] = new Future[A]
  }

  object Promise {
    def apply[A]() = new Promise[A]
  }

  /**
    * blocks and waits until a result is calculated. This will then be returned
    */
  def get[A](future: Future[A]): Option[A] =
    atomic { implicit txn =>
      future.result() match {
        case Empty => retry
        case Fail => return None
        case Success(x) => return Some(x)
      }
    }

  /**
    * returns immediately a failed future
    */
  def fail[A](): Future[A] = {
    val future = new Future[A]
    atomic { implicit tnx => future.result() = Fail }
    future
  }


  /**
    * execute callback on success else do nothing. This happens in the background therefore this method is non-blocking
    */
  def onSuccess[A](future: Future[A], callback: (A) => Unit) = {
    forkIO {
      // waiting for the result
      get(future) match {
        case None => ()
        case Some(x) => callback(x)
      }
    }
  }

  /**
    * executes a callback if the future fails
    */
  def onFailure[A](future: Future[A], callback: () => Unit) = {
    forkIO {
      // waiting for the result
      get(future) match {
        case None => callback()
        case _ =>
      }
    }
  }

  /**
    * The difference is that we wait until we obtain some (successful) result. Makes sense in a setting where we overwrite some failure, e.g. using some form of retry.
    */
  def waitForSuccess[A](future: Future[A], callback: (A) => Unit) = {
    forkIO {
      atomic { implicit txn =>
        future.result() match {
          case Success(x) => callback(x)
          case _ => retry
        }
      }
    }
  }

  /**
    * Combines the current future with a second one. The calculation is again done in the background. First
    * the results of the given futures will be retrieved. After that both results will be combined and a new future is yield
    */
  def combine[A, B, C](first: Future[A], second: Future[B], function: (A, B) => C): Future[C] = {
    Future {
      val tuple = (get(first), get(second))
      tuple match {
        // no syntactic suger, the tuple must be tested so instead of Some(v1, v2)
        case (Some(v1), Some(v2)) => function(v1, v2)
        case _ => null.asInstanceOf[C]
      }
    }
  }

  /**
    * Future result passed to some function which *then* yields a new future.
    */
  def followedBy[A, B](future: Future[A], function: (A) => B): Future[B] = {
    Future {
      get(future) match {
        case Some(x) => function(x)
        // this has to be done because Scala has types like Int that can't be null
        // this values will return 0 instead of null
        case None => null.asInstanceOf[B]
      }
    }
  }

  /**
    * Returns a new future which either carries the result of the first future or the state of the second one.
    * The first future will always be choosen first.
    */
  def orAlt[A](future: Future[A], other: Future[A]): Future[A] = {
    Future {
      get(future) match {
        case Some(x) => x
        case None => get(other) match {
          case Some(x) => x
          case None => null.asInstanceOf[A]
        }
      }
    }
  }

  /**
    * returns a new future which either succeed if the guard condition is satisfied or fails if not.
    */
  def when[A](future: Future[A], condition: (A) => Boolean): Future[A] = {
    Future {
      get(future) match {
        case Some(x) if condition(x) => x
        case _ => null.asInstanceOf[A]
      }
    }
  }

  /**
    * pick the first available result (either success or failure)
    */
  def first[A](futures: Traversable[Future[A]]): Future[A] = {
    val promise = Promise[A]()
    futures.foreach(tryCompleteWith(promise, _))
    promise.future
  }

  /**
    * Picks the first successful computation
    */
  def firstSuccessful[A](futures: Traversable[Future[A]]): Future[A] = {
    val promise = Promise[A]()
    futures.foreach(trySuccessfulCompleteWith(promise, _))
    promise.future
  }

  /* ============================= Functions Promise ============================== */

  /**
    * Tries to fulfill the promise
    */
  def trySuccess[A](promise: Promise[A], result: A): Boolean = {
    atomic { implicit txn =>
      promise.future.result() match {
        case Empty => promise.future.result() = Success(result)
          true
        case _ => false
      }
    }
  }

  /**
    * Tries to success two promises at the same time
    */
  def multiTrySuccess[A, B](first: (Promise[A], A), second: (Promise[B], B)): Boolean = {
    atomic { implicit txn =>
      val res1 = first._1.future.result()
      val res2 = second._1.future.result()
      (res1, res2) match {
        case (Empty, Empty) =>
          first._1.future.result() = Success(first._2)
          second._1.future.result() = Success(second._2)
          true
        case _ => false
      }
    }
  }

  /**
    * Tries to success two promises at the same time.
    */
  def multiTrySuccess[A](promises: Traversable[(Promise[A], A)]): Boolean = {
    atomic { implicit txn =>
      val allEmpty = promises.forall(promise => promise._1.future.result() == Empty)
      allEmpty match {
        case false => false
        case _ =>
          // in case something went wrong this might still return false but the problem here is that we cannot rollback if we return false here
          promises.forall(promise => trySuccess(promise._1, promise._2))
      }
    }
  }

  /**
    * Writes the given result directly to the future.
    * ask why the haskell implementation used to read the value first. It never touches it again
    */
  def forceSuccess[A](promise: Promise[A], value: A) = {
    atomic { implicit txn =>
      promise.future.result() = Success(value)
    }
  }

  /**
    * tries to fail the promise
    * shouldn't this return a boolean? or doesn't this even matter
    */
  def tryFail[A](promise: Promise[A]) = {
    atomic { implicit txn =>
      promise.future.result() match {
        case Empty => promise.future.result() = Fail
        case _ =>
      }
    }
  }

  /**
    * Tries to complete the promise with a given future. The result of the future will be used to complete the promise.
    * If the future failed the promise will also fail
    */
  def tryCompleteWith[A](promise: Promise[A], future: Future[A]) = {
    forkIO {
      get(future) match {
        case Some(v) => trySuccess(promise, v)
        case None => tryFail(promise)
      }
    }
  }

  /**
    * Tries to complete the promise with a given future. The result of the future will be used to complete the promise.
    * Only completes the promise if the future succeeds
    */
  def trySuccessfulCompleteWith[A](promise: Promise[A], future: Future[A]) = {
    forkIO {
      get(future) match {
        case Some(v) => trySuccess(promise, v)
        case None =>
      }
    }
  }

  private def forkIO(body: => Unit) = {
    val runnable = new Runnable {
      override def run(): Unit = body
    }
    executor.prepare().execute(runnable)
  }

}
