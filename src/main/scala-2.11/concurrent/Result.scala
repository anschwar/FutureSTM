package concurrent

/**
 * An Algebraic Data Type ADT for the future implementation
 */

sealed trait Result[+A] {
  def get: A

  def isEmpty: Boolean

  def isFail: Boolean
}

case object Empty extends Result[Nothing] {
  def get = throw new NoSuchElementException("Empty.get")

  override def isEmpty: Boolean = true

  override def isFail: Boolean = false
}

case object Fail extends Result[Nothing] {
  def get = throw new NoSuchElementException("Fail.get")

  override def isEmpty: Boolean = false

  override def isFail: Boolean = true
}

final case class Success[T](value: T) extends Result[T] {
  def get = value

  override def isEmpty: Boolean = false

  override def isFail: Boolean = false
}

object Result {
  def apply[A](x: A): Result[A] = if (x == null) Fail else Success(x)

  def empty[A]: Result[A] = Empty
}