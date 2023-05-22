package fr.phdhien.dispale.feature

import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.Itemset

object Frequency extends FeatureMapGenerator {
  override def toFeatureMap(dataset: Dataset[Set[Int]]) =
    FrequencyFeature(dataset.size.toDouble)

  override def toString: String = "Frequency"
}

final case class FrequencyFeature(override val scalingConstant: Double) extends UnscaledMeasureFeature {
  override def measureName: String = "Frequency"
  override def unscaledValueFor(itemset: Itemset): Double = itemset.size.toDouble
}
