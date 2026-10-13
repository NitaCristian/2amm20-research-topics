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
 * Pooled discriminating features: one feature for all m selected sub-patterns together
 * (instead of one per sub-pattern).
 *   PooledPatterns  : (1/m) * #{ j : S_j ⊆ X }
 *   PooledFrequency : Σ_{j : S_j ⊆ X} support(S_j) / |D|
 *   PooledLength    : Σ_{j : S_j ⊆ X} |S_j| / |I|
 */
object PooledPatterns extends FeatureMapGenerator {
    var refPatterns: Array[Itemset] = null
    override def toFeatureMap(dataset: Dataset[Set[Int]]) =
        PooledFeature("PooledPatterns", refPatterns, _ => 1.0, refPatterns.length.max(1).toDouble)
    override def setRefPatterns(patterns: Array[Itemset]) = { refPatterns = patterns }
    override def toString: String = "PooledPatterns"
}

object PooledFrequency extends FeatureMapGenerator {
    var refPatterns: Array[Itemset] = null
    override def toFeatureMap(dataset: Dataset[Set[Int]]) =
        PooledFeature("PooledFrequency", refPatterns, s => s.size.toDouble, dataset.size.toDouble)
    override def setRefPatterns(patterns: Array[Itemset]) = { refPatterns = patterns }
    override def toString: String = "PooledFrequency"
}

object PooledLength extends FeatureMapGenerator {
    var refPatterns: Array[Itemset] = null
    override def toFeatureMap(dataset: Dataset[Set[Int]]) =
        PooledFeature("PooledLength", refPatterns, s => s.description.length.toDouble, dataset.attributes.size.toDouble)
    override def setRefPatterns(patterns: Array[Itemset]) = { refPatterns = patterns }
    override def toString: String = "PooledLength"
}

final case class PooledFeature(
                                name: String,
                                refPatterns: Array[Itemset],
                                valueOf: Itemset => Double,
                                scalingConstant: Double
                            ) extends FeatureMap {
    override val featureCount: Int = 1
    override val featureLabels: IndexedSeq[String] = IndexedSeq(name)

    override protected[feature] def storeFeaturesIn(itemset: Itemset, array: Array[Double], startIndex: Int) = {
        array(startIndex) = refPatterns.view
            .filter(s => s.items.subsetOf(itemset.items))
            .map(valueOf).sum / scalingConstant
    }
}
