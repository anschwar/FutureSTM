package concurrent.benchmark

import java.util.concurrent.{CountDownLatch, ExecutorService, Executors}

import concurrent.future._
import org.scalameter.api._
import org.scalameter.{Aggregator, Bench, Gen}

import scala.concurrent.duration.Duration
import scala.concurrent.forkjoin.ForkJoinPool
import scala.concurrent.{Await, Future => NativeFuture}

object BlockingGetBenchmark extends Bench.OfflineReport {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def aggregator = Aggregator.median

  var pool: ExecutorService = null

  val threads = Gen.range("threads")(10, 60, 5)
  val iterations = Gen.range("get")(100, 1000, 100)

  performance of "Blocking get" config(
    exec.independentSamples -> 1,
    exec.benchRuns -> 1000
    ) in {
    measure method "own sequential" in {
      using(iterations) in { g =>
        val f = Future {
          Thread.sleep(1)
          42
        }
        for (i <- 1 to g) {
          get(f)
        }
      }
    }
    measure method "native sequential" in {
      using(iterations) in { g =>
        val f = NativeFuture {
          Thread.sleep(1)
          42
        }
        for (i <- 1 to g) {
          Await.result(f, Duration.Inf)
        }
      }
    }
    measure method "own concurrent with Threads" in {
      using(threads) in { threads =>
        val f = Future {
          Thread.sleep(1)
          42
        }
        val latch = new CountDownLatch(threads)
        for (i <- 1 to threads) {
          val t = new Thread() {
            override def run(): Unit = {
              get(f)
              latch.countDown()
            }
          }
          t.start()
        }
        latch.await()
      }
    }
    measure method "native concurrent with Threads" in {
      using(threads) in { sz =>
        val f = NativeFuture {
          Thread.sleep(1)
          42
        }
        val latch = new CountDownLatch(sz)
        for (i <- 1 to sz) {
          val t = new Thread() {
            override def run(): Unit = {
              Await.result(f, Duration.Inf)
              latch.countDown()
            }
          }
          t.start()
        }
        latch.await()
      }
    }
    measure method "own concurrent with ForkJoinPool" in {
      using(threads) beforeTests {
        pool = ForkJoinPool.commonPool()
      } in { threads =>
        val f = Future {
          Thread.sleep(1)
          42
        }
        val latch = new CountDownLatch(threads)
        for (i <- 1 to threads) {
          val runnable = new Runnable {
            override def run(): Unit = {
              get(f)
              latch.countDown()
            }
          }
          pool.execute(runnable)
        }
        latch.await()
      }
    }
    measure method "native concurrent with ForkJoinPool" in {
      using(threads) beforeTests {
        pool = ForkJoinPool.commonPool()
      } in { sz =>
        val f = NativeFuture {
          Thread.sleep(1)
          42
        }
        val latch = new CountDownLatch(sz)
        for (i <- 1 to sz) {
          val runnable = new Runnable {
            override def run(): Unit = {
              Await.result(f, Duration.Inf)
              latch.countDown()
            }
          }
          pool.execute(runnable)
        }
        latch.await()
      }
    }
    measure method "own concurrent with Threadpool" in {
      using(threads) beforeTests {
        pool = Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors())
      } afterTests {
        pool.shutdown()
      } in { threads =>
        val f = Future {
          Thread.sleep(1)
          42
        }
        val latch = new CountDownLatch(threads)
        for (i <- 1 to threads) {
          val runnable = new Runnable {
            override def run(): Unit = {
              get(f)
              latch.countDown()
            }
          }
          pool.execute(runnable)
        }
        latch.await()
      }
    }
    measure method "native concurrent with Threadpool" in {
      using(threads) beforeTests {
        pool = Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors())
      } afterTests {
        pool.shutdown()
      } in { sz =>
        val f = NativeFuture {
          Thread.sleep(1)
          42
        }
        val latch = new CountDownLatch(sz)
        for (i <- 1 to sz) {
          val runnable = new Runnable {
            override def run(): Unit = {
              Await.result(f, Duration.Inf)
              latch.countDown()
            }
          }
          pool.execute(runnable)
        }
        latch.await()
      }
    }
  }
}