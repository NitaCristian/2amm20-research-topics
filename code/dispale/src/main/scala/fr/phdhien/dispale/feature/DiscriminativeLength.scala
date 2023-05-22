package fr.phdhien.dispale.feature

import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.Itemset

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

final case class DiscriminativeLengthFeature(override val scalingConstant: Double, patterns: Array[Itemset]) extends UnscaledMeasureFeature {
  override def measureName: String = "DiscriminativeLength"
  
  val refPatterns = patterns
  
  //override def unscaledValueFor(itemset: Itemset): Double = itemset.description.length
  override def unscaledValueFor(itemset: Itemset): Double = {
    if (refPatterns(0).items.subsetOf(itemset.items)) refPatterns(0).description.length else 0.0
  }
}

