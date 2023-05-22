package fr.phdhien.dispale.feature

import be.kuleuven.pmlib.itemsets.Itemset

object Intercept extends FeatureMap {
  override val featureCount: Int = 1
  override val featureLabels = IndexedSeq(this.toString)

  override protected[feature] def storeFeaturesIn(itemset: Itemset, array: Array[Double], startIndex: Int): Unit =
    array(startIndex) = 1

  override def toString: String = "Intercept"
}
