package concurrent.benchmark

import concurrent.benchmark.philosophers.{Philosophers, PhilosophersLocks, PhilosophersNative, PhilosophersSTM}
import org.scalameter._
import org.scalameter.api.exec

import scala.concurrent.{Future => NativeFuture}


object SantaClauseBenchmark extends Bench.OfflineReport {

  override def aggregator = Aggregator.median

  val rounds = Gen.range("rounds")(100, 500, 100)

  // Modified Santa Clause Problem
  performance of "Santa Clause" config(
    exec.independentSamples -> 1,
    exec.outliers.covMultiplier -> 2.0,
    exec.outliers.suspectPercent -> 40,
    exec.benchRuns -> 1000
    ) in {
    measure method "functional" in {
      using(rounds) in { round =>
        functional.Santa.santaLoop(round)
      }
    }
    measure method "native" in {
      using(rounds) in { round =>
        native.Santa.santaLoop(round)
      }
    }
  }
}

object SleepingBarberBenchmark extends Bench.OfflineReport {

  override def aggregator = Aggregator.median

  val rounds = Gen.range("customers")(10, 50, 10)

  // Modified Sleeping Barber Problem
  performance of "Sleeping Barber" config(
    exec.independentSamples -> 1,
    exec.outliers.covMultiplier -> 1.5,
    exec.outliers.suspectPercent -> 40
    ) in {
    measure method "functional" in {
      using(rounds) in { customer =>
        functional.SleepingBarber.serve(customer)
      }
    }
    measure method "native" in {
      using(rounds) in { customer =>
        native.SleepingBarber.serve(customer)
      }
    }
  }
}

object DinningPhilosophersBenchmark extends Bench.OfflineReport {

  override def aggregator = Aggregator.median

  val philosophers = Gen.range("philosophers")(3, 65, 2)

  // Different Versions of the Dinning Philosophers Problem
  performance of "Philosophers" config(
    exec.independentSamples -> 1,
    exec.outliers.covMultiplier -> 1.5,
    exec.outliers.suspectPercent -> 40,
    exec.benchRuns -> 100
    ) in {
    measure method "with own Promise" in {
      using(philosophers) in { philos =>
        Philosophers.philosopherRace(philos)
      }
    }
    measure method "with native Promise" in {
      using(philosophers) in { philos =>
        PhilosophersNative.philosopherRace(philos)
      }
    }
    measure method "with Locks" in {
      using(philosophers) in { philos =>
        PhilosophersLocks.philosopherRace(philos)
      }
    }
    measure method "with STM" in {
      using(philosophers) in { philos =>
        PhilosophersSTM.philosopherRace(philos)
      }
    }
  }
}