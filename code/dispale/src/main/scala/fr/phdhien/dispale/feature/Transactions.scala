package fr.phdhien.dispale.feature

import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.Itemset

object Transactions extends FeatureMapGenerator {
  override def toFeatureMap(dataset: Dataset[Set[Int]]) = new TransactionFeatures(dataset)

  override def toString = "Transactions"
}

final class TransactionFeatures(dataset: Dataset[Set[Int]]) extends FeatureMap {
  override val featureCount: Int = dataset.size
  override val featureLabels: IndexedSeq[String] = (0 until featureCount).map(ti => s"T$ti")

  override protected[feature] def storeFeaturesIn(itemset: Itemset, array: Array[Double], startIndex: Int) =
    (0 until featureCount).foreach { t => array(startIndex + t) = if (itemset.mask.isCovering(t)) 1 else 0 }

  override def toString: String = s"Transactions($featureCount)"
}
