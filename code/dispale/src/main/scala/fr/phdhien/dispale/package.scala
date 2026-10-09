/*
 * This file is part of the DiSPaLe project (https://gitlab.com/phdhien/dispale)
 *
 * Copyright (c) 2022, Normandie Université, France
 *
 * Licensed under the MIT license.
 *
 * See LICENSE file in the project root for full license information.
 */
package fr.phdhien

import scala.collection.immutable.Set

import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.Itemset
import be.kuleuven.scd.{Example, ScdParameters, Vector, innerProduct}
import be.kuleuven.weightgen.BoundedWeightFunction
//import be.kuleuven.cp4im.{Closed, MinLength, MinSupport}

import fr.phdhien.dispale.feature.{FeatureMap, Features}
import fr.phdhien.dispale.mining.{FlexicsSampler_Oracle, HUI_Oracle, Miner}

/* 
 * @author Arnold Hien
 */
package object dispale {

    val FeatureVectorKey = "dispale.features"

    def get_miner(
                    dataset: Dataset[Set[Int]], 
                    datasetPath: String, 
                    minsup: Int = 10, 
                    oracle:String = "sampler", 
                    algo:String = "eflexics"
                ): Miner = {
        oracle.toLowerCase() match {
            case "flexics" => FlexicsSampler_Oracle(dataset=dataset, minsup=minsup, oracle=oracle, algo=algo)
            case "hui" => HUI_Oracle(dataset=dataset, datasetPath=datasetPath, minsup=minsup, oracle=oracle, algo=algo)
            case _ => FlexicsSampler_Oracle(dataset=dataset, minsup=minsup, oracle=oracle, algo=algo)
        }
    }

    def features(itemset: Itemset, featureMap: FeatureMap): Vector = {
        itemset.getMetadata[Vector](FeatureVectorKey).getOrElse {
            val features0 = featureMap(itemset)
            features0
        }
    }

    def get_method_log_name(method: String): String = {
        method.toLowerCase() match {
            case "letsip" => "LetSIP"
            case "dispale" => "DiSPaLe"
            case "lutom" => "LUToM"
            case "lutomdisc" => "LUToMDisc"
            case _ => "InterPaM"
        }
    }

    //############################################################################################################
    //############################################################################################################

    // settings of the multi sub-pattern extension of DiSPaLe
    final case class MultiDiscParams(
                                    nbSubPatterns: Int = 1,            // m: max sub-patterns per iteration (1 = DiSPaLe)
                                    selection: String = "gain",        // top | complementary | coverage | gain | pairs
                                    redundancyWeight: Double = 1.0,    // complementary: redundancy penalty, 0..1
                                    maxOverlap: Double = 0.5,          // coverage: max |correlation| between covers
                                    minGain: Double = 0.0,             // gain / pairs: stop below this share of the total
                                    expansion: String = "separate",    // separate: features per sub-pattern | pooled
                                    transferNorm: String = "m",        // divide the transferred weights by m | none
                                    clueHistory: String = "none"       // none: earlier pairs get 0 for new clues | full
                                )

    //############################################################################################################
    //############################################################################################################

    final case class Parameters(
                                    querySize: Int = 3,
                                    queryRetention: Int = 0,
                                    iterations: Int = 10,
                                    tilt: Double = 10,
                                    eta: Double = 0.5,
                                    var featureMap: Features,
                                    scd: ScdParameters,
                                    initWeight: Double = 1.0     // initial weight of every feature
                                ) {
        //
        val pairsPerQuery: Int = querySize * (querySize - 1) / 2

        @inline def a: Double = 1.0 / tilt
        
        def getQuerySize:Int = querySize
        def getQueryRetention = queryRetention
        def getIterations:Int = iterations
        def getTilt:Double = tilt
        def getfeatureMap: Features = {
            featureMap
        }
        def getSCDParam:ScdParameters = scd
        def updateFeatureMap(feature: Features) = {
            featureMap = feature
        }
    }

    //############################################################################################################
    //############################################################################################################

    final class RankedPair(
                                val preferred: Itemset,
                                val dispreferred: Itemset,
                                featureMap: FeatureMap
                            ) extends Example {
        //
        private var diffVector = {
            val dv = Array.ofDim[Double](featureMap.featureCount)
            val (f1, f2) = (features(preferred, featureMap), features(dispreferred, featureMap))
            
            (0 until featureMap.featureCount).foreach {
                i => dv(i) = f1(i) - f2(i)
            }

            dv
        }

        @inline override val length: Int = featureMap.featureCount
        @inline override def apply(j: Int): Double = diffVector(j)
        @inline override def label: Double = 1
        
        // update itemset description by adding new
        def update_pairs(count: Int) = { 
            val dv = Array.ofDim[Double](count+1)
            (0 until count).foreach { i => dv(i) = diffVector(i) }
            dv(count) = 0
            diffVector = dv
        }
        
        def update_pairs_new(count: Int, id_update: Int) = { 
            // update itemset description by adding or removing new elements (count = new size)
            // if id_update==1 ==> add new elements (set to 0)
            // if id_update==2 ==> remove the last elements
            if (id_update == 1){
                val dv = Array.ofDim[Double](count)
                (0 until math.min(diffVector.length, count)).foreach { i => dv(i) = diffVector(i) }
                diffVector = dv
            }
            else{
                val dv = Array.ofDim[Double](count)
                (0 until count).foreach { i => dv(i) = diffVector(i) }
                diffVector = dv
            }
        }
  }


    //############################################################################################################
    //############################################################################################################

    final case class LogisticWeight(a: Double, weights: Vector, featureMap: FeatureMap) extends BoundedWeightFunction[Itemset] {
        override val tiltBound: Double = 1 / a

        // logistic weight of a given pattern
        override def weight(itemset: Itemset): Double = {
            val x = features(itemset, featureMap)
            require(x.length == weights.length, s"${weights.length} weights, but ${x.length} features")
            
            val wx = innerProduct(weights, x, Some(featureMap.featureCount))
            a + (1 - a) / (1 + Math.exp(-wx))
        }

        override def toString: String = s"LearnedLogisticWeight($a, $featureMap, ${weights.length} weights)"
    }

    //############################################################################################################
    //############################################################################################################



}

