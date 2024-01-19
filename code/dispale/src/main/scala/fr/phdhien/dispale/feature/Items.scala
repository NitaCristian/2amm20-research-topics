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
object Items extends FeatureMapGenerator {
    def toFeatureMap(dataset: Dataset[Set[Int]]) = new ItemFeatures(dataset)

    override def toString: String = "Items"
}

final class ItemFeatures(dataset: Dataset[Set[Int]]) extends FeatureMap {
    override val featureCount: Int = dataset.attributes.size
    override val featureLabels: IndexedSeq[String] = dataset.attributes.map(_.name)

    override protected[feature] def storeFeaturesIn(itemset: Itemset, array: Array[Double], startIndex: Int) =
        (0 until featureCount).foreach { f =>
            array(startIndex + f) = if (itemset.description.items.contains(f)) 1 else 0
        }

    override def toString: String = s"Items($featureCount)"
}
