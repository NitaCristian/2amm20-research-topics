package be.kuleuven.scd

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.util.Random

sealed trait ScdState
case object ColdStart extends ScdState
final case class HotStart(w: Weights, z: MutableVector) extends ScdState
final case class WarmStart(w: Weights) extends ScdState
final case class NewExamplesAppended(w: Weights, z: MutableVector, mOld: Int) extends ScdState

object SCD extends ScdLogging {
  override protected val logger = Logger(LoggerFactory.getLogger("SCD"))

  def optimize(loss: LossFunction,
               examples: IndexedSeq[Example],
               m_iter: Int,
               parameters: ScdParameters,
               state: ScdState = ColdStart,
               m: Option[Int] = None,
               d: Option[Int] = None)
              (rnd: Random): (Weights, HotStart) = {
    val m0 = m.getOrElse(examples.size)
    val d0 = d.getOrElse(examples.head.length) // TODO: return if `examples` is empty

    val (w, z) = state match {
      case ColdStart =>
        // Initialize with zeroes
        (ArrayVector.zeros(d0), ArrayVector.zeros(m0))
      case HotStart(w0, z0) =>
        // Pick where the previous call left off
        (w0, z0)
      case WarmStart(w0) =>
        // Precompute the inner products vector `z`
        val z0 = ArrayVector.zeros(m0)
        (0 until m0).foreach { i => z0(i) = innerProduct(w0, examples(i), Some(d0)) }

        (w0, z0)
      case NewExamplesAppended(w0, z0, m1) =>
        // Update the inner products vector `z` for the new examples
        (m1 until m0).foreach { i => z0(i) = innerProduct(w0, examples(i), Some(d0)) }
        (w0, z0)
    }

    val lambda = parameters.lambda
    val beta = loss.beta
    //val iterations = parameters.iterations(m0, d0, beta)
    val iterations = if(parameters.features_update){ d0 } else { parameters.iterations(m0, d0, beta) }
    //
    logParameters(m0, d0, iterations, lambda, loss, state)

    //(1 to iterations).foreach { t =>
    //  val j = rnd.nextInt(d0)
    
    // lobnury -- mise à jour de tous les poids au lieu de seulement un nombre de poids égal à `iterations`
    (1 to iterations).foreach { t =>
      
      //************************************ CHOOSE THE FEATURE'ELEMENT TO BE UPDATED ************************************
      var j = 0
      if(parameters.features_update){ // Select each element one by one -*- ALL
        j = t-1
      }
      else{ // Select one element randomly and repeat "iterations" times -*- RANDOM
        j = rnd.nextInt(d0)
      }
    
      val wj = w(j)
      //logSampledFeatureIndex(t, iterations, j, wj)

      val gj = examples.view.zipWithIndex.map { case (Example(xi, yi), i) => loss.derivative(z(i), yi) * xi(j) }.sum / m0
      val eta =
        if (wj > (gj + lambda) / beta)
          -(gj + lambda) / beta
        else if (wj < (gj - lambda) / beta)
          -(gj - lambda) / beta
        else
          -wj
      w(j) += eta
      //
      // logWeightUpdate(t, iterations, gj, eta, wj, w(j), lambda, beta)
      logWeightUpdate(t, iterations, d0, gj, eta, wj, w(j), lambda, beta)
      //
      logStatistics(t, iterations, loss, lambda, examples, w, m0, d0)
      //
      examples.view.zipWithIndex.foreach { case (Example(xi, _), i) => z(i) += eta * xi(j)}
    }
    
    // ************************************ LOGS ************************************
    var str = f"**$m_iter%d+"
    //(0 to w.length-1).foreach { t => if(t!=w.length-1) print(f"$t=${w(t)};") else print(f"$t=${w(t)}\n") }
    (0 to w.length-1).foreach { t => if(t!=w.length-1) str += f"${w(t)};" else str += f"${w(t)}" }
    println(f"$str")

    (w, HotStart(w, z))
  }

  override def toString = "SCD"
}
/*
trait ScdLogging {
  protected val logger: Logger
  protected final val eps: Double = 0.0000001

  protected final def logParameters(m: Int, d: Int,
                                    iterations: Int,
                                    lambda: Double,
                                    loss: LossFunction,
                                    state: ScdState): Unit = {
    logger.info(s"$m examples, $d features")
    logger.info(f"$loss, beta = ${loss.beta}%.4f")
    logger.info(f"$iterations%d iterations, lambda = $lambda%4.1e")
    logger.debug(s"Initial state: $state")
  }

  protected final def logSampledFeatureIndex(t: Int, iterations: Int, j: Int, wj: Double): Unit =
    logger.debug(f"Iteration $t/$iterations: sampled feature index $j, current weight = $wj%.5f")

  protected final def logWeightUpdate(t: Int, iterations: Int,
                                      gj: Double, eta: Double,
                                      oldWeight: Double, newWeight: Double,
                                      lambda: Double, beta: Double): Unit = {
    logger.trace(f"Iteration $t/$iterations: comparing $oldWeight%.5f with [${(gj - lambda) / beta}%.5f; ${(gj + lambda) / beta}%.5f]")
    logger.debug(f"Iteration $t/$iterations: loss derivative = $gj%.5f; update = $eta%.5f, new weight = $newWeight%.5f")
  }

  protected final def logStatistics(t: Int, iterations: Int,
                                    loss: LossFunction,
                                    lambda: Double,
                                    examples: IndexedSeq[Example],
                                    weights: Weights,
                                    m: Int, d: Int): Unit = {
    if (t < iterations) {
      logger.trace(f"Iteration $t/$iterations: total loss = ${loss.totalLoss(examples, weights, Some(d))}%.3f")
      logger.trace(f"\tregularized loss = ${loss.regularizedLoss(examples, weights, lambda, Some(d), Some(m))}%.6f")
      logger.trace(f"\t${(0 until weights.length).count(weights(_).abs > eps)} non-zero weights, L1-norm = ${l1Norm(weights, Some(d))}%.3f")
    } else {
      logger.info(f"Iteration $t/$iterations: total loss = ${loss.totalLoss(examples, weights, Some(d))}%.3f")
      logger.info(f"\tregularized loss = ${loss.regularizedLoss(examples, weights, lambda, Some(d), Some(m))}%.6f")
      logger.info(f"\t${(0 until weights.length).count(weights(_).abs > eps)} non-zero weights, L1-norm = ${l1Norm(weights, Some(d))}%.3f")
    }
  }
}
*/
