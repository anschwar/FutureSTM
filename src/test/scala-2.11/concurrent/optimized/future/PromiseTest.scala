package concurrent.optimized.future

import concurrent.Empty

class PromiseTest extends UnitSpec {

  "A FunctionalPromise" should "execute the function and return an integer value" in {
    val p = Promise[Int]()
    val bool = trySuccess(p, IntResult)
    assert(bool === true)
    val f = p.future
    assert(f.isInstanceOf[Future[Int]])
    val result = get(f)
    assert(result === Option(IntResult))
  }

  it should "try success multiple promises" in {
    val p1 = Promise[Int]()
    val p2 = Promise[String]()
    val bool = multiTrySuccess((p1, IntResult), (p2, StringResult))
    assert(bool)
    val future = p1.future
    assert(future.isInstanceOf[Future[Int]])
    val result = get(future)
    assert(result === Option(IntResult))
    val future2 = p2.future
    assert(future2.isInstanceOf[Future[String]])
    val result2 = get(future2)
    assert(result2 === Option(StringResult))
  }

  it should "try success multiple promises of same type when a list is given" in {
    val p1 = Promise[Int]()
    val p2 = Promise[Int]()
    val p3 = Promise[Int]()
    val promises = (p1, 5) ::(p2, 10) ::(p3, 20) :: Nil
    val bool = multiTrySuccess(promises)
    assert(bool)
    val future = p1.future
    assert(future.isInstanceOf[Future[Int]])
    val result = get(future)
    assert(result === Option(5))
    val future2 = p2.future
    assert(future2.isInstanceOf[Future[Int]])
    val result2 = get(future2)
    assert(result2 === Option(10))
    val future3 = p3.future
    assert(future3.isInstanceOf[Future[Int]])
    val result3 = get(future3)
    assert(result3 === Option(20))
  }

  it should "force the success of a promise if forceSuccess is called" in {
    val p = Promise[Boolean]()
    forceSuccess(p, false)
    val future = p.future
    assert(future.isInstanceOf[Future[Boolean]])
    val result = get(future)
    assert(result === Option(false))
  }

  it should "fail if tryFail is called" in {
    val p = Promise[Int]()
    tryFail(p)
    val future = p.future
    val result = get(future)
    assert(result === None)
  }

  it should "succeed tryCompleteWith if a given future succeeds" in {
    val p = Promise[String]()
    val f = Future(delayedCalculation(StringResult, 200))
    tryCompleteWith(p, f)
    val f2 = p.future
    assert(f2.isInstanceOf[Future[String]])
    val result = get(f2)
    assert(result === Some(StringResult))
  }

  it should "fail tryCompleteWith if a given future fails" in {
    val p = Promise[Int]()
    val f = concurrent.optimized.future.fail[Int]()
    tryCompleteWith(p, f)
    val f2 = p.future
    val result = get(f2)
    assert(result === None)
  }

  it should "succeed tryCompleteSuccessfulWith if a given future succeeds" in {
    val p = Promise[Float]()
    val f = Future(delayedCalculation(FloatResult, 200))
    trySuccessfulCompleteWith(p, f)
    val f2 = p.future
    assert(f2.isInstanceOf[Future[Float]])
    val result = get(f2)
    assert(result === Some(FloatResult))
  }

  it should "do nothing if trySuccessfulCompleteWith is called and the given future fails" in {
    val p = Promise[Int]()
    val f = concurrent.optimized.future.fail[Int]()
    trySuccessfulCompleteWith(p, f)
    val f2 = p.future
    assert(f2.result.single.get === Empty)
  }

  it should "execute a callback after the promise is completed" in {
    var executed = false
    val p = Promise[Int]()
    val future = p.future
    onSuccess(future, (i: Int) => executed ^= true)
    trySuccess(p, 10)
    Thread.sleep(5)
    assert(executed)
  }
}
