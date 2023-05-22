package fr.phdhien.dispale.feature

import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.Itemset

object DiscriminativeFrequency extends FeatureMapGenerator {
  var refPatterns: Array[Itemset] = null
  
  override def toFeatureMap(dataset: Dataset[Set[Int]]) =
    DiscriminativeFrequencyFeature(dataset.size.toDouble, refPatterns)
  
  override def setRefPatterns(patterns: Array[Itemset]) = {
    refPatterns = patterns
  }
  
  def getRefPatterns(): Array[Itemset] = {
    refPatterns
  }

  override def toString: String = "DiscriminativeFrequency"
}

final case class DiscriminativeFrequencyFeature(override val scalingConstant: Double, patterns: Array[Itemset]) extends UnscaledMeasureFeature {
  override def measureName: String = "DiscriminativeFrequency"
  
  val refPatterns = patterns
  
  //override def unscaledValueFor(itemset: Itemset): Double = itemset.size.toDouble
  override def unscaledValueFor(itemset: Itemset): Double = {
    if (refPatterns(0).items.subsetOf(itemset.items)) refPatterns(0).size.toDouble else 0.0
  }
}

