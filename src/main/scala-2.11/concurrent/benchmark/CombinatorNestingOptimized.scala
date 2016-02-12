package concurrent.benchmark

import concurrent.future
import concurrent.optimized.future._
import org.scalameter.api._
import org.scalameter.{Bench, Gen}

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future => NativeFuture}

object CombinatorNestingWithOptimizedFutureBenchmark extends Bench.OfflineReport {

  override def aggregator = Aggregator.median

  val combinations = Gen.range("combinators")(10, 100, 10)

  performance of "Combinator Nesting" config(
    exec.independentSamples -> 1,
    exec.benchRuns -> 1000
    ) in {
    measure method "own andThen" in {
      using(combinations) in { toCombine =>
        var f = future.Future(List.fill(10000)(0))
        for (i <- 1 to toCombine) {
          f = future.followedBy(f, (list: List[Int]) => list.map(_ + 1))
        }
        future.get(f)
      }
    }
    measure method "own andThen optimized" in {
      using(combinations) in { toCombine =>
        var f = Future(List.fill(10000)(0))
        for (i <- 1 to toCombine) {
          f = followedBy(f, (list: List[Int]) => list.map(_ + 1))
        }
        get(f)
      }
    }
    measure method "native andThen" in {
      using(combinations) in { toCombine =>
        var f = NativeFuture(List.fill(10000)(0))
        for (i <- 1 to toCombine) {
          f = f.transform(x => x.map(_ + 1), null)
        }
        Await.ready(f, Duration.Inf)
      }
    }
    measure method "own orAlt, first succeed" in {
      using(combinations) in { toCombine =>
        var f = future.Future(42)
        for (i <- 1 to toCombine) {
          f = future.orAlt(f, future.fail())
        }
        future.get(f)
      }
    }
    measure method "own orAlt, first succeed optimized" in {
      using(combinations) in { toCombine =>
        var f = Future(42)
        for (i <- 1 to toCombine) {
          f = orAlt(f, fail())
        }
        get(f)
      }
    }
    measure method "native fallbackTo, first succeed" in {
      using(combinations) in { toCombine =>
        var f = NativeFuture(42)
        for (i <- 1 to toCombine) {
          f = f.fallbackTo(NativeFuture.failed(new RuntimeException()))
        }
        Await.result(f, Duration.Inf)
      }
    }
    measure method "own orAlt, last succeed" in {
      using(combinations) in { toCombine =>
        var f = future.fail[Int]()
        for (i <- 1 to toCombine) {
          if (i < toCombine) f = future.orAlt(f, future.fail()) else f = future.orAlt(f, future.Future(42))
        }
        future.get(f)
      }
    }
    measure method "own orAlt, last succeed optimized" in {
      using(combinations) in { toCombine =>
        var f = fail[Int]()
        for (i <- 1 to toCombine) {
          if (i < toCombine) f = orAlt(f, fail()) else f = orAlt(f, Future(42))
        }
        get(f)
      }
    }
    measure method "native fallbackTo, last succeed" in {
      using(combinations) in { toCombine =>
        var f = NativeFuture.failed[Int](new RuntimeException())
        for (i <- 1 to toCombine) {
          if (i < toCombine) {
            f = f.fallbackTo(NativeFuture.failed(new RuntimeException()))
          } else {
            f = f.fallbackTo(NativeFuture(42))
          }
        }
        Await.result(f, Duration.Inf)
      }
    }
  }
}

object FirstBusyWaitWithOptimizedFutureBenchmark extends Bench.OfflineReport {

  override def aggregator = Aggregator.median

  val concurrentTasks = Gen.range("concurrentTasks")(10, 60, 5)

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
        future.get(future.first(futures))
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
        get(first(futures))
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
        for (i <- tasks - 1 to 0 by -1) {
          futures += future.Future {
            busyWait(500 + i)
            i
          }
        }
        future.get(future.first(futures))
      }
    }
    measure method "own first, last fastest optimized" in {
      using(concurrentTasks) in { tasks =>
        var futures = ListBuffer.empty[Future[Int]]
        for (i <- tasks - 1 to 0 by -1) {
          futures += Future {
            busyWait(500 + i)
            i
          }
        }
        get(first(futures))
      }
    }
    measure method "native first, last fastest" in {
      using(concurrentTasks) in { tasks =>
        var futures = ListBuffer.empty[NativeFuture[Int]]
        for (i <- tasks - 1 to 0 by -1) {
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