package fr.phdhien.dispale

import fr.phdhien.dispale.feature.{FeatureMap, FeatureMapGenerator}
import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.Itemset
import be.kuleuven.pmlib.labels.{Model, QualityMeasure}
import be.kuleuven.scd._
import be.kuleuven.weightgen.{Uniform, WeightFunction}

import scala.util.Random

trait Ranker {
  def rank(query: Array[Itemset]): Unit
  
  def rank(query: Array[Itemset], featureMap: FeatureMap): Unit

  def describe(itemset: Itemset): String = itemset.toString()
}

trait QualityMeasureRanker[M <: Model] extends Ranker {
  protected val measure: QualityMeasure[M]
  protected val dataset: Dataset[Set[Int]] with M

  protected lazy final val datasetModel: measure.S = measure.computeState(dataset)

  protected final def score(itemset: Itemset) =
    measure.score(itemset, dataset, Some(datasetModel))

  protected final val byMeasure = Ordering.by(score).reverse

  override final def rank(query: Array[Itemset]): Unit =
    scala.util.Sorting.quickSort(query)(byMeasure)

  override final def rank(query: Array[Itemset], featureMap: FeatureMap): Unit =
    scala.util.Sorting.quickSort(query)(byMeasure)

  override final def describe(itemset: Itemset): String =
    //score(itemset).formatted("%.6f")
    f"${score(itemset)}"
}

object FrequencyRanker extends Ranker {
  private val bySize = Ordering.by { itemset: Itemset => itemset.size }.reverse

  override def rank(query: Array[Itemset]): Unit =
    scala.util.Sorting.quickSort(query)(bySize)

  override def rank(query: Array[Itemset], featureMap: FeatureMap): Unit =
    scala.util.Sorting.quickSort(query)(bySize)

  override def describe(itemset: Itemset): String = itemset.size.toString

  override def toString: String = "FrequencyRanker"
}

final class SurprisingnessRanker(dataset: Dataset[Set[Int]]) extends Ranker {
  private val invDatasetSize = 1.0 / dataset.size
  private val itemFrequencies = dataset.records.foldLeft(Array.ofDim[Double](dataset.attributes.size)) { case (f, t) =>
    t.values.foreach { i => f(i) += invDatasetSize }
    f
  }

  def surprisingness(itemset: Itemset): Double =
    (itemset.size * invDatasetSize - itemset.items.view.map(itemFrequencies).product).max(0)

  private val bySurprisingness = Ordering.by(surprisingness).reverse

  override def rank(query: Array[Itemset]): Unit =
    scala.util.Sorting.quickSort(query)(bySurprisingness)

  override def rank(query: Array[Itemset], featureMap: FeatureMap): Unit =
    scala.util.Sorting.quickSort(query)(bySurprisingness)

  override def describe(itemset: Itemset): String =
    //surprisingness(itemset).formatted("%.6f")
    f"${surprisingness(itemset)}"

  override def toString: String = "Suprisingness"
}

object SurprisingnessRanker {
  def apply(dataset: Dataset[Set[Int]]): SurprisingnessRanker = {
    new SurprisingnessRanker(dataset)
  }
}


