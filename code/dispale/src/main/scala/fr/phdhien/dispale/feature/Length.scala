package fr.phdhien.dispale.feature

import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.Itemset

object Length extends FeatureMapGenerator {
  override def toFeatureMap(dataset: Dataset[Set[Int]]): FeatureMap =
    LengthFeature(dataset.attributes.size.toDouble)

  override def toString: String = "Length"
}

final case class LengthFeature(override val scalingConstant: Double) extends UnscaledMeasureFeature {
  override def measureName: String = "Length"
  override def unscaledValueFor(itemset: Itemset): Double = itemset.description.length
}
