package fr.phdhien.dispale.feature

import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.Itemset

object Patterns extends FeatureMapGenerator {
  //var refPattern: Itemset = null
  var refPatterns: Array[Itemset] = null
  var nbElt:Int = 1
  
  //def toFeatureMap(dataset: Dataset[Set[Int]]) = new PatternFeatures(dataset, refPattern)
  //def toFeatureMap(dataset: Dataset[Set[Int]]) = new PatternFeatures(dataset, refPatterns)
  def toFeatureMap(dataset: Dataset[Set[Int]]) = new PatternFeatures(dataset, refPatterns, nbElt)
  
  //def setRefPattern(pattern: Itemset) = {
  //  refPattern = pattern
  //}
  override def setRefPatterns(patterns: Array[Itemset]) = {
    refPatterns = patterns
  }
  
  def getRefPatterns(): Array[Itemset] = {
    refPatterns
  }
  
  override def setNbElt(nb: Int) = {
    nbElt = nb
  }

  override def toString: String = "Patterns"
}

//final class PatternFeatures(dataset: Dataset[Set[Int]], pattern: Itemset) extends FeatureMap {
final class PatternFeatures(dataset: Dataset[Set[Int]], patterns: Array[Itemset], count: Int) extends FeatureMap {
  //override val featureCount: Int = 1
  //override val featureCount: Int = patterns.length
  override val featureCount: Int = count
  /*
  override val featureLabels: IndexedSeq[String] = {
    val lab = Array.fill(refPattern.items.size)("")
    var k = 0
    for (i <- 0 to dataset.attributes.size){
      if (refPattern.description.items.contains(i)){
        lab(k) = dataset.attributes(i).name
        k=k+1
      }
    }
    lab
  }
  */
  //override val featureLabels: IndexedSeq[String] = IndexedSeq("Pattern_$refPattern.items")
  override val featureLabels: IndexedSeq[String] = {
    var label = Array.fill(featureCount)("")
    (0 until featureCount).foreach { f =>
      label(f) = f"$refPatterns.items"
    }
    label
  }
  
  //val refPattern = pattern
  val refPatterns = patterns
  
  //*
  override protected[feature] def storeFeaturesIn(itemset: Itemset, array: Array[Double], startIndex: Int) = {
    (0 until featureCount).foreach { f =>
      //array(startIndex + f) = if (refPattern.items.subsetOf(itemset.items)) 1 else 0
      array(startIndex + f) = if (refPatterns(f).items.subsetOf(itemset.items)) 1 else 0
    }
  }
  //*/
  /*
  protected[feature] def storeFeaturesIn(itemsets: Array[Itemset], array: Array[Double], startIndex: Int) = {
    (0 until featureCount).foreach { f =>
      array(startIndex + f) = if (refPatterns(f).items.subsetOf(itemsets(f).items)) 1 else 0
    }
  }
  */
  
  //override def toString: String = s"Pattern($featureCount) - Ref($refPatterns)"
  override def toString: String = s"Pattern($featureCount)"
}

