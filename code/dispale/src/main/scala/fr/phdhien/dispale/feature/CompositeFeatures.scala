package fr.phdhien.dispale.feature

import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.Itemset

final case class CompositeFeatures(features: Seq[Features]) extends FeatureMapGenerator {
  override def toFeatureMap(dataset: Dataset[Set[Int]]): FeatureMap =
    new CompositeFeatureMap(features map {
      case generator: FeatureMapGenerator => generator.toFeatureMap(dataset)
      case featureMap: FeatureMap => featureMap
    })

  override def toString: String =
    features.view.map(_.toString).mkString("Composite(", ",", ")")
}

final class CompositeFeatureMap(val features: Seq[FeatureMap]) extends FeatureMap {
  override val featureCount: Int = features.view.map(_.featureCount).sum
  override lazy val featureLabels: IndexedSeq[String] = features.flatMap(_.featureLabels).toIndexedSeq

  override protected[feature] def storeFeaturesIn(itemset: Itemset, array: Array[Double], startIndex: Int): Unit =
    features.foldLeft(0) { case (startIndexDelta, featureMap) =>
      featureMap.storeFeaturesIn(itemset, array, startIndex + startIndexDelta)
      startIndexDelta + featureMap.featureCount
    }

  override def toString: String =
    features.view.map(_.toString).mkString("Composite(", ",", ")")
}
