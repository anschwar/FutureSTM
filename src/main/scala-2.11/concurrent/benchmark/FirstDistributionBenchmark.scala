package concurrent.benchmark

import org.scalameter.picklers.noPickler._
import concurrent.future
import concurrent.optimized.future._
import org.scalameter.Aggregator.Implicits._
import org.scalameter.api._
import org.scalameter.{Quantity, Bench, Gen}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future => NativeFuture}

object FirstDistributionBenchmark extends Bench.Forked[Map[String, Int]] {

  import scala.concurrent.ExecutionContext.Implicits.global

  def aggregator: Aggregator[Map[String, Int]] = Aggregator.min

  def measurer: Measurer[Map[String, Int]] = new FirstResultCharacteristics

  val concurrentTasks = Gen.range("concurrentTasks")(10, 60, 10)

  performance of "First" config(
    exec.independentSamples -> 1,
    exec.benchRuns -> 1000
    ) in {
    measure method "own first, first fastest" in {
      using(concurrentTasks) in { tasks =>
        var futures = ListBuffer.empty[future.Future[Int]]
        for (i <- 0 until tasks) {
          futures += future.Future {
            Thread.sleep(0, 10 + i)
            i
          }

        }
        future.get(future.first(futures)).get
      }
    }
    measure method "native first, first fastest" in {
      using(concurrentTasks) in { tasks =>
        var futures = ListBuffer.empty[NativeFuture[Int]]
        for (i <- 0 until tasks) {
          futures += NativeFuture {
            Thread.sleep(0, 10 + i)
            i
          }
        }
        Await.result(NativeFuture.firstCompletedOf(futures), Duration.Inf)
      }
    }
    measure method "own first, last fastest" in {
      using(concurrentTasks) in { tasks =>
        var futures = ListBuffer.empty[future.Future[Int]]
        for (i <- tasks - 1 to 0 by -1) {
          futures += future.Future {
            Thread.sleep(0, 10 + i)
            i
          }
        }
        future.get(future.first(futures)).get
      }
    }
    measure method "native first, last fastest" in {
      using(concurrentTasks) in { tasks =>
        var futures = ListBuffer.empty[NativeFuture[Int]]
        for (i <- tasks - 1 to 0 by -1) {
          futures += NativeFuture {
            Thread.sleep(0, 10 + i)
            i
          }
        }
        Await.result(NativeFuture.firstCompletedOf(futures), Duration.Inf)
      }
    }
  }
}

object FirstDistributionBusyWaitingBenchmark extends Bench.Forked[Map[String, Int]] {

  import scala.concurrent.ExecutionContext.Implicits.global

  def aggregator: Aggregator[Map[String, Int]] = Aggregator.min

  def measurer: Measurer[Map[String, Int]] = new FirstResultCharacteristics

  val concurrentTasks = Gen.range("concurrentTasks")(10, 60, 10)

  @inline
  def busyWait(micros: Long) = {
    val waitUntil = System.nanoTime() + (micros * 1000)
    while (waitUntil > System.nanoTime()) {}
  }

  performance of "First" config(
    exec.independentSamples -> 1,
    exec.benchRuns -> 1000
    ) in {
    measure method "own first, first fastest" in {
      using(concurrentTasks) in { tasks =>
        var futures = ListBuffer.empty[future.Future[Int]]
        for (i <- 0 until tasks) {
          futures += future.Future {
            busyWait(500 + i)
            i
          }

        }
        future.get(future.first(futures)).get
      }
    }
    measure method "native first, first fastest" in {
      using(concurrentTasks) in { tasks =>
        var futures = ListBuffer.empty[NativeFuture[Int]]
        for (i <- 0 until tasks) {
          futures += NativeFuture {
            busyWait(500 + i)
            i
          }
        }
        Await.result(NativeFuture.firstCompletedOf(futures), Duration.Inf)
      }
    }
    measure method "own first, last fastest" in {
      using(concurrentTasks) in { tasks =>
        var futures = ListBuffer.empty[future.Future[Int]]
        for (i <- tasks-1 to 0 by -1) {
          futures += future.Future {
            busyWait(500 + i)
            i
          }
        }
        future.get(future.first(futures)).get
      }
    }
    measure method "native first, last fastest" in {
      using(concurrentTasks) in { tasks =>
        var futures = ListBuffer.empty[NativeFuture[Int]]
        for (i <- tasks-1 to 0 by -1) {
          futures += NativeFuture {
            busyWait(500 + i)
            i
          }
        }
        Await.result(NativeFuture.firstCompletedOf(futures), Duration.Inf)
      }
    }
  }
}

object FirstDistributionBusyWaitingWithOptimizedFutureBenchmark extends Bench.Forked[Map[String, Int]] {

  import scala.concurrent.ExecutionContext.Implicits.global

  def aggregator: Aggregator[Map[String, Int]] = Aggregator.min

  def measurer: Measurer[Map[String, Int]] = new FirstResultCharacteristics

  val concurrentTasks = Gen.range("concurrentTasks")(10, 60, 10)

  @inline
  def busyWait(micros: Long) = {
    val waitUntil = System.nanoTime() + (micros * 1000)
    while (waitUntil > System.nanoTime()) {}
  }

  performance of "First" config(
    exec.independentSamples -> 1,
    exec.benchRuns -> 1000
    ) in {
    measure method "own first, first fastest" in {
      using(concurrentTasks) in { tasks =>
        var futures = ListBuffer.empty[future.Future[Int]]
        for (i <- 0 until tasks) {
          futures += future.Future {
            busyWait(500 + i)
            i
          }
        }
        future.get(future.first(futures)).get
      }
    }
    measure method "own first, first fastest optimized" in {
      using(concurrentTasks) in { tasks =>
        var futures = ListBuffer.empty[Future[Int]]
        for (i <- 0 until tasks) {
          futures += Future {
            busyWait(500 + i)
            i
          }
        }
        get(first(futures)).get
      }
    }
    measure method "native first, first fastest" in {
      using(concurrentTasks) in { tasks =>
        var futures = ListBuffer.empty[NativeFuture[Int]]
        for (i <- 0 until tasks) {
          futures += NativeFuture {
            busyWait(500 + i)
            i
          }
        }
        Await.result(NativeFuture.firstCompletedOf(futures), Duration.Inf)
      }
    }
    measure method "own first, last fastest" in {
      using(concurrentTasks) in { tasks =>
        var futures = ListBuffer.empty[future.Future[Int]]
        for (i <- tasks-1 to 0 by -1) {
          futures += future.Future {
            busyWait(500 + i)
            i
          }
        }
        future.get(future.first(futures)).get
      }
    }
    measure method "own first, last fastest optimized" in {
      using(concurrentTasks) in { tasks =>
        var futures = ListBuffer.empty[Future[Int]]
        for (i <- tasks-1 to 0 by -1) {
          futures += Future {
            busyWait(500 + i)
            i
          }
        }
        get(first(futures)).get
      }
    }
    measure method "native first, last fastest" in {
      using(concurrentTasks) in { tasks =>
        var futures = ListBuffer.empty[NativeFuture[Int]]
        for (i <- tasks-1 to 0 by -1) {
          futures += NativeFuture {
            busyWait(500 + i)
            i
          }
        }
        Await.result(NativeFuture.firstCompletedOf(futures), Duration.Inf)
      }
    }
  }
}

class FirstResultCharacteristics extends Measurer[Map[String, Int]] {
  def name = "Measurer.FirstResultCharacteristics"

  def measure[T](context: Context, measurements: Int, setup: T => Any, tear: T => Any, regen: () => T, snippet: T => Any): Seq[Quantity[Map[String, Int]]] = {
    var iteration = 0
    val map = mutable.Map.empty[String, Int]
    var value: T = null.asInstanceOf[T]

    while (iteration < measurements) {
      value = regen()
      snippet(value) match {
        case i: Int =>
          val key = i.toString
          if (map contains key) {
            val currentValue = map get key
            map += (key -> (currentValue.get + 1))
          } else {
            map += (key -> 1)
          }
      }
      iteration += 1
    }
    List(Quantity(map.toMap, null))
  }
}