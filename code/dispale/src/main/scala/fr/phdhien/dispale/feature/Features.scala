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
import be.kuleuven.scd
import be.kuleuven.scd.ArrayVector

/* 
 * @author Arnold Hien
 */

//sealed trait Features
trait Features {
    def featureCount: Int = 1
    def setNbElt(nb: Int) = {}
    def setRefPatterns(patterns: Array[Itemset]) = {}
}

trait FeatureMapGenerator extends Features {
    def toFeatureMap(dataset: Dataset[Set[Int]]): FeatureMap

    final def apply(dataset: Dataset[Set[Int]]) = toFeatureMap(dataset)

    // def setNbElt(nb: Int) = {}
    // def setRefPatterns(patterns: Array[Itemset]) = {}
}

trait FeatureMap extends Features {
    //def featureCount: Int
    def featureLabels: IndexedSeq[String] = (1 to featureCount).map(fi => s"F${fi.toString}")

    protected[feature] def storeFeaturesIn(itemset: Itemset, array: Array[Double], startIndex: Int): Unit

    final def features(itemset: Itemset): scd.Vector = {
        val array = Array.fill(featureCount)(0.0)
        storeFeaturesIn(itemset, array, 0)
        new ArrayVector(array, featureCount)
    }

    final def apply(itemset: Itemset): scd.Vector = features(itemset)
}
