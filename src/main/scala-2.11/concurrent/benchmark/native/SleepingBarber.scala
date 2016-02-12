package concurrent.benchmark.native

import java.util.concurrent.CountDownLatch

import scala.concurrent.{Future, Promise}


object SleepingBarber {

  import scala.concurrent.ExecutionContext.Implicits.global

  def remove[A](index: Int, list: List[A]): List[A] = {
    list.filterNot(i => i == index)
  }

  def serve(customersToServe: Int): Unit = {
    var customers = (for (customer <- 1 to customersToServe) yield customer).toList
    while (customers.nonEmpty) {
      val latch = new CountDownLatch(1)
      val barber = Promise[Int]()
      val done = Promise[Int]()
      barber.future.onSuccess { case customer =>
        if (done.trySuccess(customer)) {
          customers = remove(customer, customers)
          // using a latch here because the Await object could possibly create deadlocks which also occured here
          // see http://blog.jessitron.com/2014/01/fun-with-pool-induced-deadlock.html
          latch.countDown()
        }
      }
      for (customer <- customers) {
        Future {
          Thread.sleep(1)
          barber.trySuccess(customer)
        }
      }
      latch.await()
    }
  }
}

