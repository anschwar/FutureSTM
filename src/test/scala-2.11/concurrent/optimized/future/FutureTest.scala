package concurrent.optimized.future

import concurrent.Result

import scala.concurrent.stm._

class FutureTest extends UnitSpec {

  "A FunctionalFuture" should "execute the function and return an integer value" in {
    val f = Future(delayedCalculation(IntResult, 200))
    val intResult = get(f)
    assert(intResult.isInstanceOf[Option[Int]])
    assert(intResult.isInstanceOf[Some[Int]])
    assert(intResult.get === IntResult)
  }

  it should "return always 'None' if fail is called" in {
    // explicit call because there is a name conflict with the assertion fail
    val f = concurrent.optimized.future.fail()
    val intResult = get(f)
    assert(intResult === None)
    sleep(100)
    assert(intResult === None)
  }

  it should "execute the callback if onSuccess is called and the calculation succeeded" in {
    var executed = false
    val f = Future(delayedCalculation(IntResult, 200))
    onSuccess(f, (input: Int) => executed ^= true)
    sleep(550)
    assert(executed === true)
  }

  it should "execute the callback if onFailure is called and the calculation failed" in {
    var executed = false
    val future = concurrent.optimized.future.fail()
    onFailure(future, () => executed ^= true)
    sleep(50)
    assert(executed === true)
  }

  it should "execute a callback if a failed future will be changed directly" in {
    var executed = false
    val future = concurrent.optimized.future.fail[String]()
    waitForSuccess(future, (input: String) => executed ^= true)
    val r1 = get(future)
    assert(executed === false)
    assert(r1 === None)
    atomic { implicit txn =>
      future.result.set(Result("hurz"))
    }
    val r2 = get(future)
    assert(r2.isInstanceOf[Some[String]])
    assert(r2 === Some("hurz"))
    assert(r2.get === "hurz")
    // wait a little bit until the callback was executed
    sleep(200)
    assert(executed === true)
  }


  it should "should execute the given function and return a new Future if followedBy is called" in {
    val f = Future(delayedCalculation(IntResult, 200))
    val followingFuture = followedBy(f, (input: Int) => input + 3)
    assert(followingFuture.isInstanceOf[Future[Int]])
    val result = get(followingFuture)
    assert(result.isInstanceOf[Some[Int]])
    assert(result.get === IntResult + 3)
  }

  it should "should correctly calculate several nested followedBys" in {
    val f = Future(delayedCalculation(IntResult, 200))
    var followingFuture: Future[Int] = followedBy(f, (input: Int) => input + 3)
    for (i <- 1 to 5) {
      followingFuture = followedBy(followingFuture, (input: Int) => input + 3)
    }
    assert(followingFuture.isInstanceOf[Future[Int]])
    val result = get(followingFuture)
    assert(result.isInstanceOf[Some[Int]])
    assert(result.get === IntResult + 18)
  }

  it should "combine two Futures and yield a new one if combine is called" in {
    val f1 = Future(IntResult)
    val f2 = Future(IntResult)
    def combineFunction = (input: Int, input2: Int) => input.toString ++ input2.toString
    val f3 = combine(f1, f2, combineFunction)
    assert(f3.isInstanceOf[Future[String]])
    val result = get(f3)
    assert(result.isInstanceOf[Some[String]])
    assert(result.get === "1414")
  }

  it should "execute the result of the current future if orAlt is called but the current future succeeds" in {
    val f1 = Future(IntResult)
    val f2 = Future(IntResult + 3)
    val alternatives = orAlt(f1, f2)
    val result = get(alternatives)
    assert(result.isInstanceOf[Some[Int]])
    assert(result.get === IntResult)
  }

  it should "execute an alternative if the current future fails and orAlt is called" in {
    val f1 = concurrent.optimized.future.fail[Int]()
    val f2 = Future(IntResult + 12)
    val alternatives = orAlt(f1, f2)
    val result = get(alternatives)
    assert(result.isInstanceOf[Some[Int]])
    assert(result.get === IntResult + 12)
  }

  it should "return 'None' if the guard criteria is not meet" in {
    val f1 = Future(IntResult)
    val guarded = when(f1, (input: Int) => input <= 3)
    val result = get(guarded)
    assert(result === None)
  }

  it should "return 'Some(x)' if the guard criteria is meet" in {
    val f1 = Future(StringResult)
    val guarded = when(f1, (input: String) => input.length > 2)
    val result = get(guarded)
    assert(result.isInstanceOf[Some[String]])
    assert(result === Some(StringResult))
    assert(result.get === StringResult)
  }

  it should "execute several callbacks if given" in {
    var succeed = false
    var failed = false
    val future = Future(delayedCalculation("42", 200))
    onSuccess(future, (x: String) => succeed ^= true)
    onFailure(future, () => failed ^= true)
    val second = followedBy(future, (x: String) => x.toInt)
    val result = get(second)
    assert(succeed)
    assert(!failed)
    assert(result.isInstanceOf[Some[Int]])
    assert(result === Some(42))
    assert(result.get === 42)
  }

  it should "should return the first completed future which is the failed one" in {
    val f1 = concurrent.optimized.future.fail[Long]()
    val f2 = Future(delayedCalculation(LongResult, 200))
    val futures = f1 :: f2 :: Nil
    val f3 = first(futures)
    assert(f3 == f1)
  }

  it should "should return the first completed future which is the one with lesser execution time" in {
    val f1 = Future(delayedCalculation(2L, 249))
    val f2 = Future(delayedCalculation(3L, 250))
    val futures = f1 :: f2 :: Nil
    val f3 = first(futures)
    assert(f3 == f1)
  }

  it should "should return the the successful completed future if firstSuccessful is called" in {
    val f1 = concurrent.optimized.future.fail[Long]()
    val f2 = Future(delayedCalculation(LongResult, 200))
    val futures = f1 :: f2 :: Nil
    val f3 = firstSuccessful(futures)
    assert(f3 == f2)
  }

  it should "should do nothing if firstSuccessful is called and no result is successful" in {
    val f1 = concurrent.optimized.future.fail[Long]()
    val f2 = concurrent.optimized.future.fail[Long]()
    val futures = f1 :: f2 :: Nil
    val f3 = firstSuccessful(futures)
    val res = f3.result
    assert(res.single.get.isEmpty)
  }
}