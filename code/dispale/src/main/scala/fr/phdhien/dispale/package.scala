package fr.phdhien

import be.kuleuven.pmlib.itemsets.Itemset
import be.kuleuven.scd.{Example, ScdParameters, Vector, innerProduct}
import be.kuleuven.weightgen.BoundedWeightFunction
import be.kuleuven.flexics.GetWeight
//import be.kuleuven.cp4im.{Closed, MinLength, MinSupport}

import fr.phdhien.dispale.feature.{FeatureMap, Features}

package object dispale {
  val FeatureVectorKey = "dispale.features"
  
  val ByWeight = Ordering.by(GetWeight).reverse
  val ByFreq = Ordering.by { itemset: Itemset => itemset.size }.reverse
  
  def features(itemset: Itemset, featureMap: FeatureMap): Vector =
    itemset.getMetadata[Vector](FeatureVectorKey).getOrElse {
      val features0 = featureMap(itemset)
      features0
    }

  final case class Parameters(querySize: Int = 3,
                              queryOverlap: Int = 0,
                              iterations: Int = 10,
                              tilt: Double = 10,
                              eta: Double = 0.5,
                              var featureMap: Features,
                              scd: ScdParameters) {
    val pairsPerQuery: Int = querySize * (querySize - 1) / 2

    @inline def a: Double = 1.0 / tilt
    
    def getQuerySize:Int = querySize
    def getQueryOverlap = queryOverlap
    def getIterations:Int = iterations
    def getTilt:Double = tilt
    def getfeatureMap:Features = {
      featureMap
    }
    def getSCDParam:ScdParameters = scd
    def updateFeatureMap(feature: Features) = {
      featureMap = feature
    }
  }

  final class RankedPair(val preferred: Itemset,
                         val dispreferred: Itemset,
                         featureMap: FeatureMap) extends Example {
    private var diffVector = {
      val dv = Array.ofDim[Double](featureMap.featureCount)
      val (f1, f2) = (features(preferred, featureMap), features(dispreferred, featureMap))
      (0 until featureMap.featureCount).foreach { i => dv(i) = f1(i) - f2(i) }
      dv
    }

    @inline override val length: Int = featureMap.featureCount
    @inline override def apply(j: Int): Double = diffVector(j)
    @inline override def label: Double = 1
    
    //*
    def update_pairs(count: Int) = { // update itemset description by adding new
      val dv = Array.ofDim[Double](count+1)
      (0 until count).foreach { i => dv(i) = diffVector(i) }
      dv(count) = 0
      diffVector = dv
    }
    //*/
    def update_pairs_new(count: Int, id_update: Int) = { 
      // update itemset description by adding or removing one new element
      // if id_update==1 ==> add one new element
      // if id_update==2 ==> remove one new element
      if (id_update == 1){
        val dv = Array.ofDim[Double](count)
        (0 until (count-1)).foreach { i => dv(i) = diffVector(i) }
        dv(count-1) = 0
        diffVector = dv
      }
      else{
        val dv = Array.ofDim[Double](count)
        (0 until count).foreach { i => dv(i) = diffVector(i) }
        diffVector = dv
      }
    }
  }

  final case class LogisticWeight(a: Double, weights: Vector, featureMap: FeatureMap) extends BoundedWeightFunction[Itemset] {
    override val tiltBound: Double = 1 / a

    override def weight(itemset: Itemset): Double = {
      val x = features(itemset, featureMap)
      require(x.length == weights.length, s"${weights.length} weights, but ${x.length} features")
      
      val wx = innerProduct(weights, x, Some(featureMap.featureCount))
      a + (1 - a) / (1 + Math.exp(-wx))
    }

    override def toString: String = s"LearnedLogisticWeight($a,$featureMap,${weights.length} weights)"
  }
  
  /*
  def parseConstraints(arg: String): Seq[be.kuleuven.flexics.Constraint] =
    arg.toLowerCase.split(',').sorted.reverseIterator.collect {
      case minsup if minsup.startsWith("f") => be.kuleuven.lewvits.tools.MinSupport(minsup.drop(1).toInt)
      case minlen if minlen.startsWith("l") => be.kuleuven.lewvits.tools.MinLength(minlen.drop(1).toInt)
      case "c" => be.kuleuven.lewvits.tools.Closed
    }.filter {
      case be.kuleuven.lewvits.tools.MinSupport(minsup) => minsup > 0
      case be.kuleuven.lewvits.tools.MinLength(minlen) => minlen > 0
      case _ => true
    }.toSeq
    
  */
  
  /*
  def parseClosedDivConstraints(arg: String): Array[String] = {
    //println("\n~~~ ICI - parseClosedDivConstraints - begin ~~~\n")
    
    arg.toLowerCase.split(',').sorted.reverseIterator.collect {
      case minsup if minsup.startsWith("f") => "MinSup-"+minsup.drop(1)
      case minlen if minlen.startsWith("l") => "MinLen-"+minlen.drop(1)
      case "c" => "Closed"
    }.toArray
  }
  */

}
