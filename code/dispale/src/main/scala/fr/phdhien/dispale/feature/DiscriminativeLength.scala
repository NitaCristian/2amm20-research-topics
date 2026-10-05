/*
 * This file is part of the DiSPaLe project (https://gitlab.com/phdhien/dispale)
 *
 * Copyright (c) 2022, Normandie Université, France
 *
 * Licensed under the MIT license.
 *
 * See LICENSE file in the project root for full license information.
 */
package fr.phdhien.dispale.feature

import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.Itemset

/* 
 * @author Arnold Hien
 */
object DiscriminativeLength extends FeatureMapGenerator {
    var refPatterns: Array[Itemset] = null
  
    override def toFeatureMap(dataset: Dataset[Set[Int]]): FeatureMap =
        DiscriminativeLengthFeature(dataset.attributes.size.toDouble, refPatterns)
    
    override def setRefPatterns(patterns: Array[Itemset]) = {
        refPatterns = patterns
    }
    
    def getRefPatterns(): Array[Itemset] = {
        refPatterns
    }

    override def toString: String = "DiscriminativeLength"
}

// one feature per discriminating sub-pattern
final case class DiscriminativeLengthFeature(
                                                    scalingConstant: Double, 
                                                    patterns: Array[Itemset]
                                                ) extends FeatureMap {
    //
    val refPatterns = patterns

    override val featureCount: Int = refPatterns.length

    override val featureLabels: IndexedSeq[String] = (0 until featureCount).map(f => s"DiscriminativeLength($f)")

    override protected[feature] def storeFeaturesIn(itemset: Itemset, array: Array[Double], startIndex: Int) = {
        (0 until featureCount).foreach { f =>
            array(startIndex + f) = 
                if (refPatterns(f).items.subsetOf(itemset.items)) refPatterns(f).description.length / scalingConstant else 0.0
        }
    }
}

