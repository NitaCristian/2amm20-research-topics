package be.kuleuven.scd

trait IterationCalculator extends ((Int, Int, Double) => Int) {
  def iterations(examples: Int, dimension: Int, beta: Double): Int

  override final def apply(examples: Int, dimension: Int, beta: Double): Int =
    iterations(examples, dimension, beta)
}

final case class FixedIterations(iterations: Int) extends IterationCalculator {
  @inline override def iterations(m: Int, d: Int, beta: Double): Int = iterations

  override def toString(): String = s"Fixed($iterations)"
}

object Iterations {
  def toCalculator(f: (Int, Int, Double) => Int, name: String = "UndescribedIterationCalculator") =
    new IterationCalculator {
      @inline
      override def iterations(m: Int, d: Int, beta: Double) = f(m, d, beta)
      override def toString(): String = name
    }

  @inline def fixed(iterations: Int) = FixedIterations(iterations)
}
