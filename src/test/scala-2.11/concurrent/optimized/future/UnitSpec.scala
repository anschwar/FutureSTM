package concurrent.optimized.future

import org.scalatest._

import scala.concurrent.ExecutionContext

/**
  * Base class for unit testing
  */
abstract class UnitSpec extends FlatSpec with Matchers with OptionValues with Inside with Inspectors {

  implicit val context: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  // result constants
  val IntResult = 14
  val LongResult = Long.MaxValue
  val FloatResult = 0.99f
  val StringResult = "Suppe"

  def delayedCalculation[T](returnValue: T, timeToSleep: Long): T = {
    sleep(timeToSleep)
    returnValue
  }

  def sleep(duration: Long) {
    Thread.sleep(duration)
  }

}