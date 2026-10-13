/*
 * This file is part of the DiSPaLe project (https://gitlab.com/phdhien/dispale)
 *
 * Licensed under the MIT license.
 *
 * See LICENSE file in the project root for full license information.
 */
package fr.phdhien.dispale

import java.io.{BufferedWriter, FileWriter}

/*
 * Structured trace of a run: one JSON object per line (JSON Lines), written to the file given with -tr.
 * Every line has "event" and "it" (iteration, -1 for the run header). Events, in order within an iteration:
 *   run                      settings of the run and the labels of the base features
 *   query                    the sampled patterns, in sampled order, with the model score w.x used to sample them
 *   ranking                  the same patterns ranked by the user, with the user's score
 *   clues                    (DiSPaLe) the selected sub-patterns: items, ICV, cover (ranks of the query patterns containing them)
 *   weights_before_learning  base weights and, for DiSPaLe, the starting weights of the clue features
 *   weights_after_learning   the same weights after SCD
 *   fold                     (DiSPaLe) for every base feature changed by the transfer: weight before, signal d, weight after
 *   weights_after_fold       (DiSPaLe) base weights once the clue features are removed
 * Nothing is written when no trace file is given.
 */
object Trace {
    private var out: BufferedWriter = null

    def open(path: String): Unit = {
        close()
        if(path != null && path.nonEmpty)
            out = new BufferedWriter(new FileWriter(path))
    }

    def enabled: Boolean = out != null

    def close(): Unit = {
        if(out != null) { out.close(); out = null }
    }

    // writes {"event": event, "it": it, <fields>}; values must already be JSON (use the helpers below)
    def event(event: String, it: Int, fields: (String, String)*): Unit = {
        if(out == null) return
        out.write(obj((("event", str(event)) +: ("it", it.toString) +: fields): _*))
        out.newLine()
        out.flush()
    }

    //==================================================================================================
    // JSON helpers
    //==================================================================================================

    def str(s: String): String = {
        val b = new StringBuilder("\"")
        s.foreach {
            case '"'  => b.append("\\\"")
            case '\\' => b.append("\\\\")
            case '\n' => b.append("\\n")
            case '\t' => b.append("\\t")
            case c if c < ' ' => b.append(f"\\u${c.toInt}%04x")
            case c => b.append(c)
        }
        b.append('"').toString
    }

    def num(d: Double): String = if(d.isNaN || d.isInfinite) "null" else d.toString

    def arr(values: Iterable[String]): String = values.mkString("[", ",", "]")

    def ints(values: Iterable[Int]): String = arr(values.map(_.toString))

    def obj(fields: (String, String)*): String = fields.map { case (k, v) => str(k) + ":" + v }.mkString("{", ",", "}")

    // weights as {"label": weight, ...}
    def weights(labels: Seq[String], w: Int => Double, n: Int): String =
        obj((0 until n).map(i => (if(i < labels.length) labels(i) else s"F$i") -> num(w(i))): _*)
}
