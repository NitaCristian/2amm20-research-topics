package fr.phdhien.dispale

import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.Itemset
import be.kuleuven.pmlib.patterns.{Mask, MaskBuilder}

//import scala.collection.JavaConverters._

final class PatternsIterator(val dataset: Dataset[Set[Int]], 
                            val buildMask: Boolean = true,
                            val wrapper: ParseFlexicsSample) extends Iterator[Itemset] {
  private var nextItemset: Itemset = _
  private var w: ParseFlexicsSample = _
  
  def terminate(): Unit = wrapper.reset()
  
  override def hasNext: Boolean = {
    val hasSolution = wrapper.getNextSolution()
    if (hasSolution) {
      w = wrapper
      nextItemset = extract()
    } else {
      nextItemset = null
      w = null
    }
    hasSolution
  }

  override def next(): Itemset = nextItemset
  
  private def extract(): Itemset =
    Itemset(extractItems(), dataset, extractMask())

  private def extractItems(): Set[Int] = {
    val sol = w.getCurrentSolutionItems
    //val sol = scala.collection.JavaConverters.asScalaSet(w.getCurrentSolutionItems)
    var solution = scala.collection.mutable.Set[Int]()
    /*
    for (e <- sol){
      solution.add(e)
    }
    */
    var it = sol.iterator();
    while(it.hasNext()){
      solution.add(it.next())
    }
    solution.toSet
  }
  
  private def extractMask(): Option[Mask] =
    if (buildMask) {
      val builder = new MaskBuilder(datasetSize = dataset.size)
      var cov = w.getCurrentSolutionCover()
      /*
      for (t <- cov){
        builder.set(t)
      }
      */
      var it = cov.iterator();
      while(it.hasNext()){
        builder.set(it.next())
      }
      
      Some(builder.build())
    } else {
      None
    }
}


final class StaticFlexics private(val dataset: Dataset[Set[Int]],
                            val buildMask: Boolean = true, 
                            val wrapper: ParseFlexicsSample) {
  //def toString = s"StaticFlexics"
  
  def terminate(): Unit = {
    // wrapper.reset() // TODO : change this one
  }
  
  def get_all_patterns(): Unit = {
    wrapper.readResultsFile()
  }
  
  def get_current_training_patterns(): PatternsIterator = {
    wrapper.switch_iterator(0)
    new PatternsIterator(dataset, buildMask, wrapper)
  }
  
  def get_current_test_patterns(): PatternsIterator = {
    wrapper.switch_iterator(1)
    new PatternsIterator(dataset, buildMask, wrapper)
  }
  
  def next_fold(): Boolean = {
    wrapper.unexplored_folds()
  }
  
  def change_fold(): Unit = {
    wrapper.next_fold()
  }
}

object StaticFlexics {
  def apply(dataset: Dataset[Set[Int]],
            resultPath: String,
            nbFolds: Int,
            nb_patterns_per_fold: Int,
            buildMask: Boolean = true): StaticFlexics = {
    val wrapper = new ParseFlexicsSample(nbFolds, nb_patterns_per_fold, resultPath)
    
    new StaticFlexics(dataset, buildMask, wrapper)
  }
}
