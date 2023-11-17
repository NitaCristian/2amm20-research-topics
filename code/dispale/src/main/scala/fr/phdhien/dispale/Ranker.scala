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

//final class GaussianRanker(dataset: Dataset[Set[Int]],seed:Long) extends Ranker {
final class GaussianRanker(
                          dataset: Dataset[Set[Int]], 
                          seed:Long = System.nanoTime().hashCode(),
                          weightsFile: String = null) extends Ranker {
  
  
  
  
  private val weights={
    //
    var w:Array[Double]=new Array[Double](dataset.attributes.size)
    //
    //if(weightsFile.isEmpty){
    if(weightsFile == null){
      //
      // generate gaussian weights 
      //
      val rnd = new Random(seed)
      for(i<-1 to dataset.attributes.size){
        w(i-1) = rnd.nextGaussian()
        println(w(i-1))
      }
      //
    }
    else{
      //
      // read gaussian weights from file 
      //
      var i = 0
      val loop = new Breaks;
      val bufferedSource = scala.io.Source.fromFile(weightsFile)
      for (line <- bufferedSource.getLines()) {
        val weight_str = line.replaceAll(" ", "").replaceAll("\n", "")
        w(i) = weight_str.toDouble
        i = i+1
        if (i == dataset.attributes.size)
          loop.break;
      }
      bufferedSource.close()
    }
    //
    w
  }
  
  def utility(itemset:Itemset):Double={
      var v:Double=0
      var i=0
      for (i<-1 to dataset.attributes.size){
          if (itemset.items(i-1)){
              v=v+weights(i-1)}
          }
      v=v*itemset.size
      v
  }
  
  private val byUtility=Ordering.by(utility).reverse
  
  override def rank(query: Array[Itemset]): Unit =
    scala.util.Sorting.quickSort(query)(byUtility)

  override def rank(query: Array[Itemset], featureMap: FeatureMap): Unit =
    scala.util.Sorting.quickSort(query)(byUtility)

  override def describe(itemset: Itemset): String ={
    //surprisingness(itemset).formatted("%.6f")
    //println(s"${itemset.items} ${surprisingness(itemset)}")
    f"${utility(itemset)}"
    }

  override def toString: String = "Gaussian"
}
      

object GaussianRanker {
//  def apply(dataset:Dataset[Set[Int]],seed:Long): GaussianRanker = {
//    new GaussianRanker(dataset,seed)
  def apply(dataset:Dataset[Set[Int]], seed:Long = System.nanoTime().hashCode(), weightsFile: String = null): GaussianRanker = {
    new GaussianRanker(dataset, seed, weightsFile)
  }
}


final class SurprisingnessRanker(dataset: Dataset[Set[Int]]) extends Ranker {
  private val invDatasetSize = 1.0 / dataset.size
  private val itemFrequencies = dataset.records.foldLeft(Array.ofDim[Double](dataset.attributes.size)) { case (f, t) =>
    t.values.foreach { i => f(i) += invDatasetSize }
    f
  }

  def surprisingness(itemset: Itemset): Double ={
     val v= (itemset.size * invDatasetSize - itemset.items.view.map(itemFrequencies).product).max(0)
     //println(s"${itemset.items} $v")
     v
  }

  private val bySurprisingness = Ordering.by(surprisingness).reverse

  override def rank(query: Array[Itemset]): Unit =
    scala.util.Sorting.quickSort(query)(bySurprisingness)

  override def rank(query: Array[Itemset], featureMap: FeatureMap): Unit =
    scala.util.Sorting.quickSort(query)(bySurprisingness)

  override def describe(itemset: Itemset): String ={
    //surprisingness(itemset).formatted("%.6f")
    //println(s"${itemset.items} ${surprisingness(itemset)}")
    f"${surprisingness(itemset)}"
    }

  override def toString: String = "Surprisingness"
}

object SurprisingnessRanker {
  def apply(dataset: Dataset[Set[Int]]): SurprisingnessRanker = {
    new SurprisingnessRanker(dataset)
  }
}



