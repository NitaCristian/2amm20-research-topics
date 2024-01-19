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
object Frequency extends FeatureMapGenerator {
    override def toFeatureMap(dataset: Dataset[Set[Int]]) =
        FrequencyFeature(dataset.size.toDouble)

    override def toString: String = "Frequency"
}

final case class FrequencyFeature(
                                    override val scalingConstant: Double
                                ) extends UnscaledMeasureFeature {
    override def measureName: String = "Frequency"
    override def unscaledValueFor(itemset: Itemset): Double = itemset.size.toDouble
}
