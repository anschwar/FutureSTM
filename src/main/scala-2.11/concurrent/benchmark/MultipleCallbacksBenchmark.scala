package concurrent.benchmark

import java.util.concurrent.CountDownLatch

import concurrent.optimized.future._
import org.scalameter.api._
import org.scalameter.{Aggregator, Bench, Gen}

import scala.concurrent.{Future => NativeFuture}

object MultipleCallbacksBenchmark extends Bench.OfflineReport {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def aggregator = Aggregator.median

  val iterations = Gen.range("get")(10, 100, 10)

  performance of "Multiple Callbacks" config(
    exec.independentSamples -> 1,
    exec.benchRuns -> 1000
    ) in {
    measure method "own no wait" in {
      using(iterations) in { max =>
        val latch = new CountDownLatch(max)
        val f = concurrent.future.Future {
          42
        }
        for (i <- 1 to max) {
          concurrent.future.onSuccess(f, (input: Int) => latch.countDown())
        }
        latch.await()
      }
    }
    measure method "optimized own no wait" in {
      using(iterations) in { max =>
        val latch = new CountDownLatch(max)
        val f = Future {
          42
        }
        for (i <- 1 to max) {
          onSuccess(f, (input: Int) => latch.countDown())
        }
        latch.await()
      }
    }
    measure method "native no wait" in {
      using(iterations) in { max =>
        val latch = new CountDownLatch(max)
        val f = NativeFuture {
          42
        }
        for (i <- 1 to max) {
          f.onSuccess {
            case input => latch.countDown()
          }
        }
        latch.await()
      }
    }
    measure method "own wait first" in {
      using(iterations) in { max =>
        val latch = new CountDownLatch(max)
        val f = concurrent.future.Future {
          Thread.sleep(1)
          42
        }
        for (i <- 1 to max) {
          concurrent.future.onSuccess(f, (input: Int) => latch.countDown())
        }
        latch.await()
      }
    }
    measure method "optimized own wait first" in {
      using(iterations) in { max =>
        val latch = new CountDownLatch(max)
        val f = Future {
          Thread.sleep(1)
          42
        }
        for (i <- 1 to max) {
          onSuccess(f, (input: Int) => latch.countDown())
        }
        latch.await()
      }
    }
    measure method "native wait first" in {
      using(iterations) in { max =>
        val latch = new CountDownLatch(max)
        val f = NativeFuture {
          Thread.sleep(1)
          42
        }
        for (i <- 1 to max) {
          f.onSuccess {
            case input => latch.countDown()
          }
        }
        latch.await()
      }
    }
  }
}