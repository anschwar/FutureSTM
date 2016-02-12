package concurrent.optimized

import java.util.concurrent.ConcurrentLinkedQueue

import concurrent.{Empty, Fail, Result, Success}

import scala.concurrent.ExecutionContext.Implicits.{global => executor}
import scala.concurrent.stm._

/**
  * Interface for the usage of futures in a more functional way. It delegates the action the the concrete implementation
  * which is within the FunctionalFuture. Those functions can only be accessed via this interface and not directly.
  */
package object future {

  /* ================================ Data structures & companion objects ================================ */

  final case class Future[A]() {
    val callbacks = new ConcurrentLinkedQueue[Runnable]()
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
      execute(createRunnable(complete(future, func)))
      future
    }
  }

  final class Promise[A] {
    val future: Future[A] = new Future[A]
  }

  object Promise {
    def apply[A]() = new Promise[A]
  }

  /* ================================ Helper functions ================================ */

  private def afterCompletion[A](future: Future[A]): Unit = {
    while (!future.callbacks.isEmpty) {
      val task = future.callbacks.poll()
      if (task != null) execute(task)
    }
  }

  private def addOrExecuteCallback[A](future: Future[A], runnable: Runnable) = {
    // add the runnable to the queue before checking what to do because STM could execute a retry which would
    // cause that the runnable will be added 2 times
    future.callbacks.add(runnable)
    atomic { implicit txn =>
      future.result() match {
        case Empty => // do nothing here
        case _ => afterCompletion(future)
      }
    }
  }

  private def complete[A](future: Future[A], value: A) = {
    atomic { implicit tnx => future.result() = Result(value) }
    afterCompletion(future)
  }

  /* ================================ Functions that operate on the Future ================================ */
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
    complete(future, null.asInstanceOf[A])
    future
  }

  /**
    * execute callback on success else do nothing. This happens in the background therefore this method is non-blocking
    */
  def onSuccess[A](future: Future[A], callback: (A) => Unit) = {
    val cb = createRunnable {
      get(future) match {
        case None => ()
        case Some(x) => callback(x)
      }
    }
    addOrExecuteCallback(future, cb)
  }

  /**
    * executes a callback if the future fails
    */
  def onFailure[A](future: Future[A], callback: () => Unit) = {
    val cb = createRunnable {
      get(future) match {
        case None => callback()
        case _ =>
      }
    }
    addOrExecuteCallback(future, cb)
  }

  /**
    * The difference is that we wait until we obtain some (successful) result. Makes sense in a setting where we overwrite some failure, e.g. using some form of retry.
    */
  def waitForSuccess[A](future: Future[A], callback: (A) => Unit) = {
    val cb = createRunnable {
      atomic { implicit txn =>
        future.result() match {
          case Success(x) => callback(x)
          case _ => retry
        }
      }
    }
    addOrExecuteCallback(future, cb)
  }

  /**
    * Future result passed to some function which *then* yields a new future.
    */
  def followedBy[A, B](future: Future[A], function: (A) => B): Future[B] = {
    val result = new Future[B]
    val callback = createRunnable {
      get(future) match {
        case Some(x) => complete(result, function(x))
        case None => complete(result, null.asInstanceOf[B])
      }
    }
    addOrExecuteCallback(future, callback)
    result
  }

  /**
    * Combines the current future with a second one. The calculation is again done in the background. First
    * the results of the given futures will be retrieved. After that both results will be combined and a new future is yield
    */
  def combine[A, B, C](first: Future[A], second: Future[B], function: (A, B) => C): Future[C] = {
    val result = new Future[C]
    val callback = createRunnable {
      (get(first), get(second)) match {
        case (Some(v1), Some(v2)) => complete(result, function(v1, v2))
        case _ => complete(result, null.asInstanceOf[C])
      }
    }
    addOrExecuteCallback(first, callback)
    result
  }

  /**
    * Returns a new future which either carries the result of the first future or the state of the second one.
    * The first future will always be choosen first.
    */
  def orAlt[A](future: Future[A], other: Future[A]): Future[A] = {
    val result = new Future[A]
    val callback = createRunnable {
      get(future) match {
        case Some(x) => complete(result, x)
        case None => get(other) match {
          case Some(x) => complete(result, x)
          case None => complete(result, null.asInstanceOf[A])
        }
      }
    }
    addOrExecuteCallback(future, callback)
    result
  }

  /**
    * returns a new future which either succeed if the guard condition is satisfied or fails if not.
    */
  def when[A](future: Future[A], condition: (A) => Boolean): Future[A] = {
    val result = new Future[A]
    val callback = createRunnable {
      get(future) match {
        case Some(x) if condition(x) => complete(result, x)
        case _ => complete(result, null.asInstanceOf[A])
      }
    }
    addOrExecuteCallback(future, callback)
    result
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


  /* ================================ Functions that operate on the Future ================================ */

  /**
    * Tries to fulfill the promise
    */
  def trySuccess[A](promise: Promise[A], result: A): Boolean = {
    atomic { implicit txn =>
      promise.future.result() match {
        case Empty => complete(promise.future, result)
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
          complete(first._1.future, first._2)
          complete(second._1.future, second._2)
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
        case _ => promises.forall(promise => trySuccess(promise._1, promise._2))
      }
    }
  }

  /**
    * Writes the given result directly to the future.
    * ask why the haskell implementation used to read the value first. It never touches it again
    */
  def forceSuccess[A](promise: Promise[A], value: A) = {
    complete(promise.future, value)
  }

  /**
    * tries to fail the promise
    * shouldn't this return a boolean? or doesn't this even matter
    */
  def tryFail[A](promise: Promise[A]) = {
    atomic { implicit txn =>
      promise.future.result() match {
        case Empty => complete(promise.future, null.asInstanceOf[A])
        case _ =>
      }
    }
  }

  /**
    * Tries to complete the promise with a given future. The result of the future will be used to complete the promise.
    * If the future failed the promise will also fail
    */
  def tryCompleteWith[A](promise: Promise[A], future: Future[A]) = {
    val callback = createRunnable {
      get(future) match {
        case Some(v) => trySuccess(promise, v)
        case None => tryFail(promise)
      }
    }
    addOrExecuteCallback(future, callback)
    promise.future
  }

  /**
    * Tries to complete the promise with a given future. The result of the future will be used to complete the promise.
    * Only completes the promise if the future succeeds
    */
  def trySuccessfulCompleteWith[A](promise: Promise[A], future: Future[A]) = {
    val callback = createRunnable {
      get(future) match {
        case Some(v) => trySuccess(promise, v)
        case None =>
      }
    }
    addOrExecuteCallback(future, callback)
    promise.future
  }

  private def execute(runnable: Runnable) = {
    executor.prepare().execute(runnable)
  }

  private def createRunnable(body: => Unit): Runnable = {
    new Runnable {
      override def run(): Unit = body
    }
  }

}
