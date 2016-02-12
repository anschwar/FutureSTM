package concurrent.benchmark.functional

import concurrent.future._

object SleepingBarber {

  def remove[A](index: Int, list: List[A]): List[A] = {
    list.filterNot(i => i == index)
  }

  def serve(customersToServe: Int): Unit = {
    var customers = (for (customer <- 1 to customersToServe) yield customer).toList
    while (customers.nonEmpty) {
      val barber = Promise[Int]()
      val done = Promise[Int]()
      def cutHair(customer: Int) = {
        if (trySuccess(done, customer)) {
          customers = remove(customer, customers)
        }
      }
      onSuccess(barber.future, cutHair)
      for (customer <- customers) {
        Future {
          Thread.sleep(1)
          trySuccess(barber, customer)
        }
      }
      get(done.future)
    }
  }
}

