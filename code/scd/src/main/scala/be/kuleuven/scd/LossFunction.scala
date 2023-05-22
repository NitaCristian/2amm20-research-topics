package be.kuleuven.scd

import java.lang.Math.{exp, log}

trait LossFunction {
  def loss(weightExampleInnerProduct: Double, target: Double): Double

  def derivative(weightExampleInnerProduct: Double, target: Double): Double

  val beta: Double

  final def loss(example: Example, weights: Weights, d: Option[Int] = None): Double =
    loss(innerProduct(weights, example, d), example.label)

  final def totalLoss(examples: Traversable[Example], weights: Weights, d: Option[Int] = None): Double = {
    val d0 = Some(d.getOrElse(weights.length))
    examples.view.map(loss(_, weights, d0)).sum
  }

  final def regularizedLoss(examples: Traversable[Example],
                            weights: Weights,
                            lambda: Double,
                            d: Option[Int] = None,
                            m: Option[Int] = None): Double = {
    val d0 = Some(d.getOrElse(weights.length))
    totalLoss(examples, weights, d0) / m.getOrElse(examples.size) + lambda * l1Norm(weights, d0)
  }
}

object SquaredLoss extends LossFunction {
  override def loss(a: Double, y: Double): Double = {
    val l = a - y
    0.5 * l * l
  }

  override def derivative(a: Double, y: Double): Double = a - y

  override val beta: Double = 1

  override def toString = "SquaredLoss"
}

object LogisticLoss extends LossFunction {
  override def loss(a: Double, y: Double): Double = log(1 + exp(-a * y))

  override def derivative(a: Double, y: Double): Double = -y / (1 + exp(a * y))

  override val beta: Double = 0.25

  override def toString = "LogisticLoss"
}
