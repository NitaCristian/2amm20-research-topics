/*
 * This file is part of letsip project (https://bitbucket.org/wxd/letsip/src/master/)
 *
 * Copyright (c) 2017, KU Leuven, Belgium
 */
package be.kuleuven.scd

import scala.util.Random

/* 
 * @author Vladimir Dzyuba
 */
object Main extends App {
    private val params = ScdParameters(Iterations.fixed(100), false, 0.001)
    println(params)

    private val m = 100
    private val d = 10

    private val seed = System.nanoTime().hashCode().abs
    private val rnd = new Random(seed)
    println(seed)

    private val examples = (0 until m).map { i =>
        val yi = if (rnd.nextBoolean()) 1.0 else -1.0
        val xi = Array.ofDim[Double](d)
        (0 until d).foreach { j =>
            xi(j) = if (rnd.nextBoolean()) rnd.nextDouble() * 2 - 1 else 0.0
            if (yi == 0 && j == 0)      xi(j) = -xi(j).abs //  First feature is always negative for negative examples
            else if (yi == 1 && j == 1) xi(j) =  xi(j).abs // Second feature is always positive for positive examples
        }

        new ArrayExample(xi, d, yi)
    }

    println(s"$m $d")
    (0 until d).foreach { j =>
        println(
                    examples.view
                            .zipWithIndex
                            .filter { case (Example(xi, _), _) => xi(j) != 0.0 }
                            .map { case (Example(xi, _), i) => f"$i%d ${xi(j)}%.6f" }
                            .mkString(" ")
                )
    }
    println()
    examples.foreach { case Example(_, yi) => println(yi.toInt) }
    println()

    val (w, _) = SCD.optimize(LogisticLoss, examples, 1, params, state = ColdStart, m = Some(m), d = Some(d))(rnd)
    println((0 until w.length).map(j => f"${w(j)}%.3f").mkString(" "))
}
