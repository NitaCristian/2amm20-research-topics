/*
 * This file is part of letsip project (https://bitbucket.org/wxd/letsip/src/master/)
 *
 * Copyright (c) 2017, KU Leuven, Belgium
 */
package be.kuleuven.scd

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

/* 
 * @author Vladimir Dzyuba
 * @author Arnold Hien
 */
trait ScdLogging {
    protected val logger: Logger
    protected final val eps: Double = 0.0000001

    protected final def logParameters(
                                            m: Int, d: Int,
                                            iterations: Int,
                                            lambda: Double,
                                            loss: LossFunction,
                                            state: ScdState
                                        ): Unit = {
        //
        logger.info(s"$m examples, $d features")
        logger.info(f"$loss, beta = ${loss.beta}%.4f")
        logger.info(f"$iterations%d iterations, lambda = $lambda%4.1e")
        //logger.debug(s"Initial state: $state")
    }

    protected final def logSampledFeatureIndex(t: Int, iterations: Int, j: Int, wj: Double): Unit =
        logger.debug(f"Iteration $t/$iterations: sampled feature index $j, current weight = $wj%.5f")

    //protected final def logWeightUpdate(t: Int, iterations: Int,
    protected final def logWeightUpdate(
                                            t: Int, iterations: Int, d: Int,
                                            gj: Double, eta: Double,
                                            oldWeight: Double, newWeight: Double,
                                            lambda: Double, beta: Double
                                        ): Unit = {
        //
        //logger.trace(f"Iteration $t/$iterations: comparing $oldWeight%.5f with [${(gj - lambda) / beta}%.5f; ${(gj + lambda) / beta}%.5f]")
        //logger.debug(f"Iteration $t/$iterations: loss derivative = $gj%.5f; update = $eta%.5f, new weight = $newWeight%.5f")
        logger.trace(f"Iteration $t/$d: comparing $oldWeight%.5f with [${(gj - lambda) / beta}%.5f; ${(gj + lambda) / beta}%.5f]")
        //logger.debug(f"Iteration $t/$d: loss derivative = $gj%.5f; update = $eta%.5f, new weight = $newWeight%.5f")
    }

    protected final def logStatistics(
                                            t: Int, iterations: Int,
                                            loss: LossFunction,
                                            lambda: Double,
                                            examples: IndexedSeq[Example],
                                            weights: Weights,
                                            m: Int, d: Int
                                        ): Unit = {
        //
        //if (t < d) {
        if (t < iterations) {
            // logger.trace(f"Iteration $t/$iterations: total loss = ${loss.totalLoss(examples, weights, Some(d))}%.3f")
            logger.trace(f"Iteration $t/$d: total loss = ${loss.totalLoss(examples, weights, Some(d))}%.3f")
            logger.trace(f"\tregularized loss = ${loss.regularizedLoss(examples, weights, lambda, Some(d), Some(m))}%.6f")
            logger.trace(f"\t${(0 until weights.length).count(weights(_).abs > eps)} non-zero weights, L1-norm = ${l1Norm(weights, Some(d))}%.3f")
        } else {
            //logger.info(f"Iteration $t/$iterations: total loss = ${loss.totalLoss(examples, weights, Some(d))}%.3f")
            logger.info(f"Iteration $t/$d: total loss = ${loss.totalLoss(examples, weights, Some(d))}%.3f")
            logger.info(f"\tregularized loss = ${loss.regularizedLoss(examples, weights, lambda, Some(d), Some(m))}%.6f")
            logger.info(f"\t${(0 until weights.length).count(weights(_).abs > eps)} non-zero weights, L1-norm = ${l1Norm(weights, Some(d))}%.3f")
        }
    }
}


