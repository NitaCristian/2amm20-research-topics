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
import be.kuleuven.pmlib.labels.{Model, QualityMeasure}

/* 
 * @author Arnold Hien
 */
trait MeasureFeature extends FeatureMap {
    def measureName: String

    override final val featureCount = 1
    override final val featureLabels = IndexedSeq(measureName)

    def valueFor(itemset: Itemset): Double

    override protected[feature] final def storeFeaturesIn(itemset: Itemset, array: Array[Double], startIndex: Int): Unit =
        array(startIndex) = valueFor(itemset)
}

trait UnscaledMeasureFeature extends MeasureFeature {
    def unscaledValueFor(itemset: Itemset): Double
    def scalingConstant: Double

    @inline override final def valueFor(itemset: Itemset): Double =
        unscaledValueFor(itemset) / scalingConstant
}

trait SupervisedMeasureFeature[M <: Model] extends UnscaledMeasureFeature {
    val measure: QualityMeasure[M]
    val dataset: Dataset[Set[Int]] with M
    val measureState: measure.S

    @inline override final def measureName = measure.toString

    override def unscaledValueFor(itemset: Itemset) =
        measure.score(itemset, dataset, Some(measureState))
}

trait SupervisedMeasureFeatureGenerator[M <: Model] extends FeatureMapGenerator {
    protected val measure: QualityMeasure[M]

    protected def labelled(dataset: Dataset[Set[Int]]): Dataset[Set[Int]] with M
    protected def build(labelledDataset: Dataset[Set[Int]] with M, state: measure.S): SupervisedMeasureFeature[M]

    override final def toFeatureMap(dataset: Dataset[Set[Int]]): SupervisedMeasureFeature[M] = {
        val ds = labelled(dataset)
        val measureState = measure.computeState(ds)
        build(ds, measureState)
    }
}

