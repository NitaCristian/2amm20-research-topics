/*
 * This file is part of the DiSPaLe project (https://gitlab.com/phdhien/dispale)
 *
 * Copyright (c) 2022, Normandie Université, France
 *
 * Licensed under the MIT license.
 *
 * See LICENSE file in the project root for full license information.
 */
package fr.phdhien.dispale.mining

import scala.collection.immutable.Set

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.sys.process._
import scala.util.Random

import java.io.{File, FileWriter, BufferedWriter}

import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.Itemset
import be.kuleuven.pmlib.patterns.{Mask, MaskBuilder}
import be.kuleuven.weightgen._
import be.kuleuven.weightgen.{Uniform, WeightFunction, WeightGenConfiguration, WeightMcConfiguration}
import be.kuleuven.wg4ps.specialised.EclatProblem

import ca.pfv.spmf.algorithms.frequentpatterns.AlgoHUI
import ca.pfv.spmf.algorithms.frequentpatterns.tko.AlgoTKO
import ca.pfv.spmf.algorithms.frequentpatterns.tkuce.AlgoTKUCEP

/* 
 * @author Arnold Hien
 */

// =============================== //
// === initialize class object === //
// =============================== //
object Miner{ 

    def apply(
                dataset: Dataset[Set[Int]], 
                datasetPath: String, 
                oracle: String = "flexics", 
                algo: String = "eflexics", 
                minsup: Int = 10): Miner = {
        new Miner(dataset, datasetPath, minsup, oracle, algo)
    }

}

//############################################################################################################
//############################################### GENERIC MINER ##############################################
//############################################################################################################
class Miner(
            val dataset: Dataset[Set[Int]], 
            val datasetPath: String = null, 
            val minsup: Int = 10, 
            val oracle: String = "flexics", 
            val algo: String = "eflexics") {

    //*****************************************
    def run_pattern_mining(
                                queries_array: Array[Array[Itemset]],
                                querySize: Int = 5, 
                                queryRetention: Int = 0, 
                                currentIteration: Int = 0, 
                                nb_feats: Int = 1,
                                all_w: Array[Double] = Array.ofDim[Double](1),
                                weight_function: WeightFunction[Itemset] = Uniform
                            )(rnd: Random): Array[Itemset] = {
        null
    }

}

//############################################################################################################
//############################################## FLEXICS SAMPLER #############################################
//############################################################################################################

object FlexicsSampler_Oracle{
    def apply(
                dataset: Dataset[Set[Int]], 
                minsup: Int = 10, 
                oracle: String = "flexics",
                algo: String = "eflexics"
            ): FlexicsSampler_Oracle = {
        new FlexicsSampler_Oracle(dataset, minsup, oracle, algo)
    }
}

class FlexicsSampler_Oracle(
                                override val dataset: Dataset[Set[Int]], 
                                override val minsup: Int = 10, 
                                override val oracle: String = "flexics",
                                override val algo: String = "eflexics"
                            )  extends Miner(dataset=dataset, minsup=minsup, oracle=oracle, algo=algo) {
    
    //*****************************************
    val sampler = EFlexicsWrapper(VanillaEFlexics)
    //*****************************************
    val miningProblem = EclatProblem(dataset, minsup)
    //*****************************************
    val countingConf = WeightMcConfiguration.default
    val samplingConf = WeightGenConfiguration(kappa = 0.5)
    //*****************************************

    /*****************************/
    /*** FUNCTION TO MINE HUIs ***/
    /*****************************/
    override def run_pattern_mining(
                                        queries_array: Array[Array[Itemset]],
                                        querySize: Int, 
                                        queryRetention: Int, 
                                        currentIteration: Int, 
                                        nb_feats: Int = 1,
                                        all_w: Array[Double] = Array.ofDim[Double](1),
                                        weight_function: WeightFunction[Itemset] = Uniform
                                    )(rnd: Random): Array[Itemset] = {
        //
        
        // get patterns retained from previous iteration
        if (currentIteration > 0) {
            //copy retained patterns to new query
            queries_array(currentIteration - 1).copyToArray( queries_array(currentIteration), 0, queryRetention )
        }

        // store previous patterns weights using current weight function
        queries_array(currentIteration).view(0, queryRetention).foreach { 
            p => storeWeightInPlace(p, weight_function(p)) 
        }
        
        //extract missing patterns with the oracle
        var patterns_iterator: Iterator[Itemset] = null
        patterns_iterator = sampler.countThenSample(
                                                    miningProblem, weight_function, samplingConf, 
                                                    Left(countingConf), solutionWeight = Some(GetWeight))(rnd)
    
        patterns_iterator.take(querySize - queryRetention).zipWithIndex.foreach { 
            case (itemset, j) => queries_array(currentIteration)(queryRetention + j) = itemset
        }
    
        scala.util.Sorting.quickSort(queries_array(currentIteration))(ByWeight)
        
        // return current iteration query patterns
        queries_array(currentIteration)
    }

}

//############################################################################################################
//################################################ HUIs MINER ################################################
//############################################################################################################

object HUI_Oracle{
    def apply(
                dataset: Dataset[Set[Int]], 
                datasetPath: String, 
                minsup: Int = 10, 
                oracle: String = "huiminer",
                algo: String = "tko"
            ): HUI_Oracle = {
        new HUI_Oracle(dataset, datasetPath, minsup, oracle, algo)
    }
}

class HUI_Oracle(
                    override val dataset: Dataset[Set[Int]], 
                    override val datasetPath: String, 
                    override val minsup: Int = 10, 
                    override val oracle: String = "huiminer",
                    override val algo: String = "tko"
                )  extends Miner(dataset, datasetPath, minsup, oracle, algo) {
    
    //*****************************************
    var nb_features: Int = 1
    var all_weights: Array[Double] = Array.ofDim[Double](1)
    //*****************************************

    def setup(nb_feats: Int = 1, all_w: Array[Double] = Array.ofDim[Double](1)): Unit = {
        nb_features = nb_feats
        all_weights = all_w
    }

    /*****************************/
    /*** FUNCTION TO MINE HUIs ***/
    //****************************/
    override def run_pattern_mining(
                                queries_array: Array[Array[Itemset]],
                                querySize: Int = 5, 
                                queryRetention: Int = 0, 
                                currentIteration: Int = 0, 
                                nb_feats: Int = 1,
                                all_w: Array[Double] = Array.ofDim[Double](1),
                                weight_function: WeightFunction[Itemset] = Uniform
                            )(rnd: Random): Array[Itemset] = {
        //
        setup(nb_feats, all_w)
        //
        val method: Algo = algo.toLowerCase match{
            case "tko" | "tkuce" => 
                Algo_TKO_TKUCE("tkuce", dataset, datasetPath, querySize, currentIteration, nb_features, all_weights)
            case "haisampler" => 
                Algo_HAISampler("haisampler", dataset, datasetPath, querySize, currentIteration, nb_features, all_weights)
            case _ => 
                Algo_TKO_TKUCE("tkuce", dataset, datasetPath, querySize, currentIteration, nb_features, all_weights)
        }
        
        // get patterns retained from previous iteration
        if (currentIteration > 0) {
            //copy retained patterns to new query
            queries_array(currentIteration - 1).copyToArray( queries_array(currentIteration), 0, queryRetention )
        }

        // store previous patterns weights using current weight function
        queries_array(currentIteration).view(0, queryRetention).foreach { 
            p => storeWeightInPlace(p, weight_function(p)) 
        }
        
        //val weight_function: WeightFunction[Itemset] = if(currentIteration == 0) Uniform else learnedWeight
        
        var patterns_iterator: Iterator[Itemset] = method.mine_HUIs(queries_array, weight_function)
        
        // update current iteration itemsets
        patterns_iterator.take(querySize - queryRetention).zipWithIndex.foreach { 
            case (itemset, j) => queries_array(currentIteration)(queryRetention + j) = itemset
        }
        
        // sort current iteration itemset according to their learned weights
        scala.util.Sorting.quickSort(queries_array(currentIteration))(ByWeight)
        
        // return current iteration query patterns
        queries_array(currentIteration)
    }

}

//############################################################################################################
//########################################## GENERIC CLASS FOR MINERS ########################################
//############################################################################################################
class Algo(
            val algo_str: String = "tko",
            val dataset: Dataset[Set[Int]], 
            val datasetPath: String, 
            val querySize: Int = 5, 
            //val queryRetention: Int = 0, 
            val currentIteration: Int = 0, 
            val nb_features: Int = 5, 
            val all_weights: Array[Double] = Array.ofDim[Double](1)
        ) {
    //*****************************************
    val nbItems: Int = dataset.attributes.size
    //*****************************************
    //*****************************************
    def mine_HUIs( 
                    queries_array: Array[Array[Itemset]],
                    weight_function: WeightFunction[Itemset] = Uniform 
                ): Iterator[Itemset] = {
        null
    }


}

//############################################################################################################
//############################################ TKO and TKUCE MINER ###########################################
//############################################################################################################

object Algo_TKO_TKUCE{
    def apply(
                algo_str: String = "tko",
                dataset: Dataset[Set[Int]], 
                datasetPath: String, 
                querySize: Int = 5, 
                //val queryRetention: Int = 0, 
                currentIteration: Int = 0, 
                nb_features: Int = 5, 
                all_weights: Array[Double] = Array.ofDim[Double](1)
            ): Algo_TKO_TKUCE = {
        new Algo_TKO_TKUCE(algo_str, dataset, datasetPath, querySize, currentIteration, nb_features, all_weights)
    }
}

class Algo_TKO_TKUCE(
                        override val algo_str: String = "tko",
                        override val dataset: Dataset[Set[Int]], 
                        override val datasetPath: String, 
                        override val querySize: Int = 5, 
                        //val queryRetention: Int = 0, 
                        override val currentIteration: Int = 0, 
                        override val nb_features: Int = 5, 
                        override val all_weights: Array[Double] = Array.ofDim[Double](1)
                    ) extends Algo(algo_str, dataset, datasetPath, querySize, currentIteration, nb_features, all_weights) {
    //
    //*****************************************
    // True when features=Items+Transactions AND False when features=Items
    val transUsed: Boolean = nb_features > nbItems
    //*****************************************
    //

    override def mine_HUIs( 
                    queries_array: Array[Array[Itemset]],
                    weight_function: WeightFunction[Itemset] = Uniform 
                ): Iterator[Itemset] = {
        //
        val algo: AlgoHUI = if(algo_str.toLowerCase == "tko") new AlgoTKO() else new AlgoTKUCEP()

        // extracts top-k patterns
        val topK_patterns = algo.runAlgorithm( datasetPath, querySize, all_weights, nbItems, transUsed )
        
        // for each sampled pattern, make a new object Itemset 
        var i=0
        var listTopK = new ListBuffer[Itemset]()
        for(i<-1 to querySize){
            val id = if(algo_str.toLowerCase == "tko") querySize-i else i-1
            val oneTopK = itemsToSet( topK_patterns.get(id) )
            val itsCover = coverToSet( dataset, algo.get_one_itemset_cover(id) )
            val TopItemset: Itemset = Itemset(oneTopK, dataset, itsCover)
            
            var check:Boolean = true
            /*
            //checking if the itemset is not already in the query retention
            var j=0
            for (j<-1 to queryRetention){
                check = check & (TopItemset.items != queries_array(currentIteration)(j-1).items)
            }
            */
            if (check){
                storeWeightInPlace( TopItemset, weight_function(TopItemset) )
                listTopK += TopItemset
            }
        }
        // the list of candidates as an iterator
        val topK_patterns_iterator = listTopK.iterator

        // return an iterator over patterns mined
        topK_patterns_iterator
    }
    //

}

//############################################################################################################
//############################################# HAISampler MINER #############################################
//############################################################################################################

object Algo_HAISampler{
    def apply(
                algo_str: String = "tko",
                dataset: Dataset[Set[Int]], 
                datasetPath: String, 
                querySize: Int = 5, 
                //val queryRetention: Int = 0, 
                currentIteration: Int = 0, 
                nb_features: Int = 5, 
                all_weights: Array[Double] = Array.ofDim[Double](1)
            ): Algo_HAISampler = {
        new Algo_HAISampler(algo_str, dataset, datasetPath, querySize, currentIteration, nb_features, all_weights)
    }
}

class Algo_HAISampler(
                        override val algo_str: String = "haisampler",
                        override val dataset: Dataset[Set[Int]], 
                        override val datasetPath: String, 
                        override val querySize: Int = 5, 
                        //val queryRetention: Int = 0, 
                        override val currentIteration: Int = 0, 
                        override val nb_features: Int = 5, 
                        override val all_weights: Array[Double] = Array.ofDim[Double](1)
                    ) extends Algo(algo_str, dataset, datasetPath, querySize, currentIteration, nb_features, all_weights) {
    //*****************************************
    // HAI Sampler program path
    val path = new java.io.File("code/dispale/src/main/python/haisampler-src-main/HAISampler.py").getCanonicalPath
    //*****************************************
    val M = 500 // maximum length of a sampled pattern
    //*****************************************

    override def mine_HUIs( 
                    queries_array: Array[Array[Itemset]],
                    weight_function: WeightFunction[Itemset] = Uniform 
                ): Iterator[Itemset] = {
        //
        // writting input parameters in a txt file
        var params = nbItems + "\n" + datasetPath + "\n" + querySize + "\n" + M + "\n"
        params = params + all_weights.mkString(" ")

        val file = new File("parameters.txt")
        val bw = new BufferedWriter(new FileWriter(file))
        bw.write(params + "\n")
        bw.close()
        
        
        //System call for launching HAISampler
        val cmd = "python3 " + path
        cmd.!!
        
        //reading sampled patterns
        var listTopK = new ListBuffer[Itemset]()
        val haisampler_results = "topK.txt"
        for (line <- Source.fromFile(haisampler_results).getLines){
            val itemsetAndCover = line.split(":")
            val itemsetString = itemsetAndCover(0).split(" ")
            val coverString = itemsetAndCover(1).split(" ")
            
            //make a new object Itemset 
            val oneTopK = arrayToSet(itemsetString)
            val itsCover = arrayToMask(dataset, coverString)
            val TopItemset: Itemset = Itemset(oneTopK, dataset, itsCover)
            
            var check:Boolean = true
            /*
            //checking if the itemset is not already in the query retention
            val j=0
            for (j<-1 to queryRetention){
                check = check & (TopItemset.items != queries_array(currentIteration)(j-1).items)
            }
            */
            if (check){
                storeWeightInPlace(TopItemset, weight_function(TopItemset))
                listTopK += TopItemset
            }
        }

        // list of candidates as an iterator
        val topK_patterns_iterator = listTopK.iterator

        // return an iterator over patterns mined
        topK_patterns_iterator
    }
    //

}

