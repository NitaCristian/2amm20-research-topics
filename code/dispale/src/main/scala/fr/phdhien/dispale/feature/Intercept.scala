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

import be.kuleuven.pmlib.itemsets.Itemset

/* 
 * @author Arnold Hien
 */
object Intercept extends FeatureMap {
    override val featureCount: Int = 1
    override val featureLabels = IndexedSeq(this.toString)

    override protected[feature] def storeFeaturesIn(
                                                        itemset: Itemset, 
                                                        array: Array[Double], 
                                                        startIndex: Int
                                                    ): Unit =
        array(startIndex) = 1

    override def toString: String = "Intercept"
}
