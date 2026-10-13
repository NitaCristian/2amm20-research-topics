/*
 * This file is part of the DiSPaLe project (https://gitlab.com/phdhien/dispale)
 *
 * Copyright (c) 2022, Normandie Université, France
 *
 * Licensed under the MIT license.
 *
 * See LICENSE file in the project root for full license information.
 */
package fr.phdhien.dispale

import fr.phdhien.dispale.feature.{FeatureMap, FeatureMapGenerator}
import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.Itemset
import be.kuleuven.pmlib.labels.{Model, QualityMeasure}
import be.kuleuven.scd._
import be.kuleuven.weightgen.{Uniform, WeightFunction}

// Importing break package
import scala.util.control.Breaks
import scala.util.Random

/* 
 * @author Arnold Hien
 */
trait Ranker{
    def rank(query: Array[Itemset]): Unit
  
    def rank(query: Array[Itemset], featureMap: FeatureMap): Unit

    def describe(itemset: Itemset): String = itemset.toString()
}

//############################################################################################################
//############################################################################################################

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

//############################################################################################################
//############################################################################################################

object FrequencyRanker extends Ranker {
    private val bySize = Ordering.by { itemset: Itemset => itemset.size }.reverse

    override def rank(query: Array[Itemset]): Unit =
        scala.util.Sorting.quickSort(query)(bySize)

    override def rank(query: Array[Itemset], featureMap: FeatureMap): Unit =
        scala.util.Sorting.quickSort(query)(bySize)

    override def describe(itemset: Itemset): String = itemset.size.toString

    override def toString: String = "FrequencyRanker"
}

//############################################################################################################
//############################################################################################################

//final class GaussianRanker(dataset: Dataset[Set[Int]],seed:Long) extends Ranker {
final class GaussianRanker(
                                dataset: Dataset[Set[Int]], 
                                seed:Long = System.nanoTime().hashCode(),
                                weightsFile: String = null
                            ) extends Ranker {
  
    private val weights = {
        //
        var w:Array[Double] = new Array[Double](dataset.attributes.size)
        //
        if(weightsFile == null){
            //
            // generate gaussian weights 
            val rnd = new Random(seed)
            for(i<-1 to dataset.attributes.size){
                w(i-1) = rnd.nextGaussian()
                println(w(i-1))
            }
        } else{
            //
            // read gaussian weights from file 
            // one weight per line; lines after the last item are ignored
            // (the original `loop.break' had no enclosing `breakable' and crashed once every item had a weight)
            val bufferedSource = scala.io.Source.fromFile(weightsFile)
            bufferedSource.getLines().map(_.trim).filter(_.nonEmpty).take(dataset.attributes.size).zipWithIndex.foreach {
                case (weight_str, i) => w(i) = weight_str.toDouble
            }
            bufferedSource.close()
        }
        //
        w
    }
  
    def utility(itemset:Itemset):Double = {
        var v:Double = 0
        var i = 0
        for (i <- 1 to dataset.attributes.size){
            if (itemset.items(i-1)){
                v = v + weights(i-1)
            }
        }
        v = v*itemset.size
        v
    }
  
    private val byUtility=Ordering.by(utility).reverse
    
    override def rank(query: Array[Itemset]): Unit =
        scala.util.Sorting.quickSort(query)(byUtility)

    override def rank(query: Array[Itemset], featureMap: FeatureMap): Unit =
        scala.util.Sorting.quickSort(query)(byUtility)

    override def describe(itemset: Itemset): String = {
        //surprisingness(itemset).formatted("%.6f")
        //println(s"${itemset.items} ${surprisingness(itemset)}")
        f"${utility(itemset)}"
    }

    //override def toString: String = "Gaussian"
    override def toString: String = "GaussianRanker"
}

object GaussianRanker {
    //  def apply(dataset:Dataset[Set[Int]],seed:Long): GaussianRanker = {
    //    new GaussianRanker(dataset,seed)
    def apply(
                dataset: Dataset[Set[Int]], 
                seed: Long = System.nanoTime().hashCode(), 
                weightsFile: String = null
            ): GaussianRanker = {
        new GaussianRanker(dataset, seed, weightsFile)
    }
}

//############################################################################################################
//############################################################################################################

final class SurprisingnessRanker(
                                    dataset: Dataset[Set[Int]]
                                ) extends Ranker {
    
    private val invDatasetSize: Double = 1.0 / dataset.size

    private val itemFrequencies = dataset.records.foldLeft(Array.ofDim[Double](dataset.attributes.size)) { 
        case (f, t) =>
            t.values.foreach { i => f(i) += invDatasetSize }
            f
    }

    def surprisingness(itemset: Itemset): Double = {
        val v: Double = (itemset.size * invDatasetSize - itemset.items.view.map(itemFrequencies).product).max(0)
        //println(s"${itemset.items} $v")
        v
    }

    private val bySurprisingness = Ordering.by(surprisingness).reverse

    override def rank(
                        query: Array[Itemset]
                    ): Unit =
        scala.util.Sorting.quickSort(query)(bySurprisingness)

    override def rank(
                        query: Array[Itemset], 
                        featureMap: FeatureMap
                    ): Unit =
        scala.util.Sorting.quickSort(query)(bySurprisingness)

    override def describe(itemset: Itemset): String = {
        //surprisingness(itemset).formatted("%.6f")
        //println(s"${itemset.items} ${surprisingness(itemset)}")
        f"${surprisingness(itemset)}"
    }

    //override def toString: String = "Surprisingness"
    override def toString: String = "SurprisingnessRanker"
}

object SurprisingnessRanker {
    def apply(
                dataset: Dataset[Set[Int]]
            ): SurprisingnessRanker = {
        new SurprisingnessRanker(dataset)
    }
}

//############################################################################################################
//############################################################################################################


/*
 * Simulated user whose taste is a few separate item combinations, e.g. "likes (A,B)" and,
 * independently, "likes (C,D)":
 *     score(X) = Σ_j weight_j · [S_j ⊆ X]  +  0.01 · surprisingness(X)
 * The small surprisingness term only breaks ties between patterns with the same combinations.
 * A linear model over items cannot represent this taste exactly (it needs "A and B together").
 */
final class ComboRanker(
                            dataset: Dataset[Set[Int]],
                            val combos: Array[(Set[Int], Double)]
                        ) extends Ranker {

    private val tieBreak = SurprisingnessRanker(dataset)

    def score(itemset: Itemset): Double =
        combos.map { case (s, w) => if (s.subsetOf(itemset.items)) w else 0.0 }.sum +
            0.01 * tieBreak.surprisingness(itemset)

    private val byScore = Ordering.by(score).reverse

    override def rank(query: Array[Itemset]): Unit = scala.util.Sorting.quickSort(query)(byScore)

    override def rank(query: Array[Itemset], featureMap: FeatureMap): Unit = scala.util.Sorting.quickSort(query)(byScore)

    override def describe(itemset: Itemset): String = f"${score(itemset)}"

    override def toString: String =
        "ComboRanker(" + combos.map { case (s, w) => s.toSeq.sorted.mkString("{", ",", "}") + f":$w%.1f" }.mkString(" ") + ")"
}

object ComboRanker {
    def apply(dataset: Dataset[Set[Int]], spec: String): ComboRanker = new ComboRanker(dataset, parse(spec))

    // "29,52;40,58" (weight 1 each) or "29,52:1;40,58:1;9,40:-1" (item ids as in the .txt file)
    def parse(spec: String): Array[(Set[Int], Double)] =
        spec.split(";").map(_.trim).filter(_.nonEmpty).map { part =>
            val (items, w) = part.split(":") match {
                case Array(i, weight) => (i, weight.trim.toDouble)
                case Array(i) => (i, 1.0)
                case _ => throw new IllegalArgumentException(s"bad combination: $part")
            }
            (items.split(",").map(_.trim.toInt).toSet, w)
        }
}
