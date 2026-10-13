/*
 * This file is part of the DiSPaLe project (https://gitlab.com/phdhien/dispale)
 *
 * Licensed under the MIT license.
 *
 * See LICENSE file in the project root for full license information.
 */
package fr.phdhien.dispale.feature

import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.Itemset

/*
 * One presence feature per kept sub-pattern (-kp keep): 1 if the pattern contains the sub-pattern, else 0.
 * Unlike the per-iteration discriminating features, these stay in the model and are never folded.
 */
final case class KeptSubPatterns(refPatterns: Array[Itemset]) extends FeatureMapGenerator {
    override def toFeatureMap(dataset: Dataset[Set[Int]]) = KeptSubPatternsMap(refPatterns)
    override def toString: String = "KeptSubPatterns"
}

final case class KeptSubPatternsMap(refPatterns: Array[Itemset]) extends FeatureMap {
    override val featureCount: Int = refPatterns.length
    override val featureLabels: IndexedSeq[String] =
        refPatterns.map(s => "Kept(" + s.items.toSeq.sorted.mkString(",") + ")").toIndexedSeq

    override protected[feature] def storeFeaturesIn(itemset: Itemset, array: Array[Double], startIndex: Int) = {
        refPatterns.indices.foreach { j =>
            array(startIndex + j) = if(refPatterns(j).items.subsetOf(itemset.items)) 1.0 else 0.0
        }
    }
}
