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

import be.kuleuven.flexics._
import be.kuleuven.flexics.GetWeight
import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.Itemset
import be.kuleuven.pmlib.patterns.{Mask, MaskBuilder}
//import be.kuleuven.scd._
import be.kuleuven.scd.{ArrayVector, MutableVector, ScdState, SCD, ColdStart, HotStart, WarmStart, NewExamplesAppended, LogisticLoss}
import be.kuleuven.weightgen._
import be.kuleuven.weightgen.{Uniform, WeightFunction, WeightGenConfiguration, WeightMcConfiguration}

import com.typesafe.scalalogging.Logger

import fr.phdhien.dispale.feature.{Features, FeatureMap, FeatureMapGenerator, Patterns, DiscriminativeFrequency, DiscriminativeLength}

import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.collection.mutable.HashMap
import scala.math.exp

/* 
 * @author Arnold Hien
 */
object Dispale {
    def apply(
                method: String,
                dataset: Dataset[Set[Int]],
                datasetPath: String,
                datasetPathFimi: String, 
                params: Parameters,
                listFeatures: Array[Features],
                listFeatures_count: Array[Int], 
                listFeatures_str: Array[String], 
                minsup: Int = 10,
                oracle: String = "flexics", 
                algo: String = "eflexics",
                aggregation_function: String = "lin", 
                user: Ranker = FrequencyRanker,
                seed: Long = System.nanoTime().hashCode()
            ): Dispale = {
        //
        new Dispale(
                        method, dataset, datasetPath, datasetPathFimi, params, 
                        listFeatures, listFeatures_count, listFeatures_str, 
                        minsup, oracle, algo, aggregation_function, user, seed
                    )
        //
    }
}

class Dispale(
                override val method: String,
                override val dataset: Dataset[Set[Int]], 
                override val datasetPath: String, 
                val datasetPathFimi: String, 
                override val params: Parameters, 
                override val listFeatures: Array[Features], 
                val listFeatures_count: Array[Int], 
                val listFeatures_str: Array[String], 
                override val minsup: Int = 10, 
                override val oracle: String = "flexics", 
                override val algo: String = "eflexics",
                val aggregation_function: String = "lin", 
                override val user: Ranker = FrequencyRanker, 
                override val seed: Long = System.nanoTime().hashCode()
            ) extends LetSIP(method, dataset, datasetPath, params, listFeatures, minsup, oracle, algo, user, seed) {
    //
    //************************************************************************************************************
    var allFeatures: Array[Features] = listFeatures
    //************************************************************************************************************
    var best_icv_itemset:Itemset = null
    var discriminatingPatterns = HashMap[Set[Int], Double]()  // used to save discriminating Patterns
    var discriminatingLengths = HashMap[Set[Int], Double]()  // used to save discriminating pattern length weight
    var discriminatingFrequencies = HashMap[Set[Int], Double]()  // used to save discriminating pattern frequency weight
    //************************************************************************************************************
    // used to compute best ICV itemset
    var icvObject : BestICVSubset = new BestICVSubset(params.querySize, datasetPathFimi)
    //************************************************************************************************************
    protected override val logger = Logger(LoggerFactory.getLogger("DiSPaLe"))  
    
    //############################################################################################################
    //############################################################################################################

    override def loop(): LogisticWeight = {
        @tailrec
        def doLoop(weight_function: WeightFunction[Itemset]): LogisticWeight = {
            currentIteration match {
                case params.iterations =>
                    val glf = weight_function.asInstanceOf[LogisticWeight]
                    logTermination(currentIteration, glf)
                    glf
                case _ =>
                    // *************** SAMPLE A SET OF `querySize' PATTERNS *************************************
                    var msg = s"Sampling $querySize itemsets to query"
                    logger.debug(s" ${msg.replace("ing", "ed")}")

                    currentQuery = pattern_miner.run_pattern_mining(
                                                                        queries_array, querySize, queryRetention, 
                                                                        currentIteration, nb_features, all_w.array, weight_function
                                                                    )(rnd)
                    
                    // *************** RANK PATTERNS BY DESCENDING ORDER OF THEIR WEIGHT **************
                    msg = s"Ranking $querySize itemsets"
                    logger.debug(s" ${msg.replace("ing", "ed")}")
                    user.rank(currentQuery)
                    
                    // *************** PAIRS OF PATTERNS TO BE USED FOR THE LEARNING ******************
                    msg = s"Generating ${params.pairsPerQuery} example pairs from $querySize itemsets"
                    logger.debug(s" ${msg.replace("ing", "ed")}")
                    
                    // *************** UPDATE FEATURES ELEMENTS' WEIGHT *******************************
                    msg = s"Updating $nb_features weights"
                    logger.debug(s" ${msg.replace("ing", "ed")}")

                    val iter_results = run_learning()
                    // val (iter_results, newState) = run_learning()
                    
                    // *************** PRINT LOGS *****************************************************
                    logAnaData(currentIteration, currentQuery, learnedWeight, user)
                    logIterationOutcome(currentIteration, currentQuery, learnedWeight, user)
                    
                    // *************** NEXT ITERATIONS ************************************************
                    currentIteration += 1
                    doLoop(learnedWeight)
                    //doLoop(currentIteration + 1, learnedWeight, newState)
            }
        }
        doLoop(Uniform)
    }

    //############################################################################################################
    //############################################################################################################

    override def run_learning(): String = {
        
        // ********************** GET SUB-PATTERN *************************************************************
        mine_discriminating_patterns()

        // ********************** UPDATE WEIGHTS **************************************************************
        add_discriminant_features_to_features_list()
        
        // ********************** FORM PAIRS OF PATTERNS TO BE USED FOR THE LEARNING **************************
        rankedPairs(currentQuery, querySize).zipWithIndex.foreach { 
            case ((preferred, dispreferred), j) =>
                trainingPairs(currentIteration * params.pairsPerQuery + j) = new RankedPair(preferred, dispreferred, featureMap)
        }
        
        // ****************************************************************************************************
        //  UPDATE FEATURES ELEMENTS WEIGHT W.R.T. THE STRATEGY CHOOSEN (see argument 'features-update' in Main)
        val state: HotStart = HotStart(all_w, z)
        current_state = state
        
        val (_, updated_state) = updateWeights()
        
        remove_discriminant_features_from_features_list()
        
        // ********************** GET THE LEARNED FUNCTION ****************************************************
        val learnedWeight = LogisticWeight(params.a, all_w, featureMap)
        
        // ********************** PRINT LOGS ******************************************************************
        val iter_anaDAta = getAnaData(currentIteration, currentQuery, learnedWeight)
        val iter_results = getIterationOutcome(currentIteration, currentQuery, learnedWeight)
        
        iter_results
        // (iter_results, HotStart(all_w, z))
    }

    //############################################################################################################
    //############################################################################################################
    
    def covert_to_itemset(dataset: Dataset[Set[Int]], icvObject : BestICVSubset): Itemset = {
        Itemset(extractItems(icvObject), dataset, extractMask(dataset, icvObject))
    }

    def extractItems(icvObject : BestICVSubset): Set[Int] = {
        val sol = icvObject.getBestSolutionItems()
        var solution = scala.collection.mutable.Set[Int]()
        var i = 0
        val it = sol.iterator();
        while(it.hasNext()) {
            solution.add(it.next())
        }
        solution.toSet
    }

    def extractMask(dataset: Dataset[Set[Int]], icvObject : BestICVSubset): Option[Mask] = {
        val builder = new MaskBuilder(datasetSize = dataset.size)
        val cov = icvObject.getBestSolutionCover()
        //*
        val it = cov.iterator();
        while(it.hasNext()) {
            builder.set(it.next())
        }
        //*/
        Some(builder.build())
    }

    //############################################################################################################
    //############################################################################################################
    
    def get_discriminating_features_list(nbToAddOrRemove: Int): Array[Features] = {
        var discriminating_features: Array[Features] = Array.fill(nbToAddOrRemove)(null)

        discriminating_features(0) = Patterns
        discriminating_features(0).setNbElt(1)
        discriminating_features(0).setRefPatterns(Array.fill(1)(best_icv_itemset))

        if(!discriminatingPatterns.exists(x => x._1 == best_icv_itemset.items)){
            discriminatingPatterns.put(best_icv_itemset.items, 0.0)
        }

        if(listFeatures_str.contains("F")){
            discriminating_features(1) = DiscriminativeFrequency
            if(!discriminatingFrequencies.exists(x => x._1 == best_icv_itemset.items)){
                discriminatingFrequencies.put(best_icv_itemset.items, 0.0)
            }
        }
        if(listFeatures_str.contains("L")){
            discriminating_features(2) = DiscriminativeLength
            if(!discriminatingLengths.exists(x => x._1 == best_icv_itemset.items)){
                discriminatingLengths.put(best_icv_itemset.items, 0.0)
            }
        }

        discriminating_features
    }

    //############################################################################################################
    //############################################################################################################

    def update_featureMap(): Unit = {
        val newCompositeFeatures = feature.compose(allFeatures:_*)
        params.updateFeatureMap(newCompositeFeatures)
        featureMap = params.featureMap match {
            case generator: FeatureMapGenerator => generator.toFeatureMap(dataset)
            case map: FeatureMap => map
        }
        nb_features = featureMap.featureCount
    }

    //############################################################################################################
    //############################################################################################################

    def mine_discriminating_patterns(): Unit = {
        var it = 0
        while(it < querySize){
            var pattern = new java.util.HashSet[Integer]()
            for (item <- currentQuery(it).items){
                pattern.add(item)
            }
            icvObject.setItemsets(pattern)
            it = it+1
        }
        icvObject.enumerateItemsets()
        best_icv_itemset = covert_to_itemset(dataset, icvObject)
        // best_icv_itemset
        println(f"\n++$currentIteration-${best_icv_itemset.items}")
        
        icvObject.reset()
    }

    //############################################################################################################
    //############################################################################################################

    override def updateWeights() = {

        val m = (currentIteration + 1) * params.pairsPerQuery
        val trainingExamples = trainingPairs.view(0, m)

        val state: ScdState = 
            if(currentIteration > 0) showNewExamplesToSCD(current_state, z, currentIteration, m) else current_state

        SCD.optimize(
                        loss, trainingExamples, currentIteration, 
                        params.scd.copy( lambda= params.scd.lambda * (currentIteration + 1) ),
                        state=state, m=Some(m), d=Some(nb_features)
                )(rnd)
    }
    
    //############################################################################################################
    //############################################################################################################

    def update_features_list(code: Int, nbToAddOrRemove: Int, featuresToBeAddedOrRemoved: Array[Features]): Unit = {
        // code is used to know whether to :
        // --> increase: add the discriminant pattern to the list (code=1)
        // or
        // --> decrease : remove the discriminant pattern to the list (code=2)
        // the number of features
        var newFeatures:Array[Features] = {
            if(code==1)
                Array.fill(allFeatures.size+nbToAddOrRemove)(null)
            else
                Array.fill(allFeatures.size-nbToAddOrRemove)(null)
        }
        val o = if(code==1) -nbToAddOrRemove else 0 // offset

        (0 until (newFeatures.size+o)).foreach{ 
            i => newFeatures(i) = allFeatures(i)
        }
        if(code==1){
            newFeatures(allFeatures.size) = featuresToBeAddedOrRemoved(0)
            var it = 1
            if(listFeatures_str.contains("F")){
                newFeatures(allFeatures.size+it) = featuresToBeAddedOrRemoved(1)
                it = it+1
            }
            if(listFeatures_str.contains("L")){
                newFeatures(allFeatures.size+it) = featuresToBeAddedOrRemoved(2)
            }
        }
        allFeatures = newFeatures
        //newFeatures
    }

    //############################################################################################################
    //############################################################################################################

    def aggregation_value(new_val: Double, disc_val: Double) = {
        if(aggregation_function == "lin") new_val * (1 + (params.eta*disc_val)) else new_val * exp(params.eta*disc_val)
    }
    
    //############################################################################################################
    //############################################################################################################

    def update_weight_list(code: Int, discriminating_weights: Array[Double], nbToAddOrRemove: Int): Unit = {
        // either increase or decrease
        val e = all_w.length
        var new_w = if(code==1) ArrayVector.zeros(e+nbToAddOrRemove) else ArrayVector.zeros(e-nbToAddOrRemove)

        val o = if(code==1) 0 else -nbToAddOrRemove // offset
        (0 until (e+o)).foreach{ i =>
            new_w(i) = all_w(i)
        }

        if(code==1){
            var it = 1
            new_w(e) = discriminating_weights(0)
            if(listFeatures_str.contains("F")){
                new_w(e+it) = discriminating_weights(1)
                it = it+1
            }
            if(listFeatures_str.contains("L")){
                new_w(e+it) = discriminating_weights(2)
            }
        }
        else{
            var it = 0

            while(it < listFeatures_str.size) {
                var deb = 0
                (0 until it).foreach{ i => deb = deb+listFeatures_count(i) }
                val fin = deb + listFeatures_count(it)

                if(listFeatures_str(it) == "I"){
                    (deb until fin).foreach{ i =>
                        if(best_icv_itemset.items(i)){
                            new_w(i) = aggregation_value(new_w(i), discriminating_weights(0))
                        }
                    }
                }
                else if(listFeatures_str(it) == "T"){
                    (deb until fin).foreach{ i =>
                        if(best_icv_itemset.mask.isCovering(i-deb)){
                            new_w(i) = aggregation_value(new_w(i), discriminating_weights(0))
                        }
                    }
                }
                else if(listFeatures_str(it) == "L"){
                    (deb until fin).foreach{ i =>
                        new_w(i) = aggregation_value(new_w(i), discriminating_weights(1))
                    }
                    discriminatingLengths(best_icv_itemset.items) = discriminating_weights(1)
                }
                else if(listFeatures_str(it) == "F"){
                    (deb until fin).foreach{ i =>
                        new_w(i) = aggregation_value(new_w(i), discriminating_weights(2))
                    }
                    discriminatingFrequencies(best_icv_itemset.items) = discriminating_weights(2)
                }
                it = it+1
            }

            discriminatingPatterns(best_icv_itemset.items) = discriminating_weights(0)
        }

        //new_w
        all_w = new_w
    }

    //############################################################################################################
    //############################################################################################################

    def add_discriminant_features_to_features_list() = {
        
        // ********************** New discriminating features *************************************************
        var nbToAddOrRemove = 1 // discriminating features elements to be added (or removed when finished)
        nbToAddOrRemove = if(listFeatures_str.contains("F")) nbToAddOrRemove+1 else nbToAddOrRemove
        nbToAddOrRemove = if(listFeatures_str.contains("L")) nbToAddOrRemove+1 else nbToAddOrRemove
        
        var discriminating_features = get_discriminating_features_list(nbToAddOrRemove)
        
        // ********************** update features list : add discriminating features **************************
        update_features_list(1, nbToAddOrRemove, discriminating_features)
        
        // ********************** Updating feature map by adding discriminating features **********************
        update_featureMap()
        
        // *********************** Variables Wi representing the discriminating weights ***********************
        // w1 : discriminating pattern weight
        val w1 = discriminatingPatterns(best_icv_itemset.items)
        // w2 : discriminating pattern frequency weight
        val w2 = if(listFeatures_str.contains("F")) discriminatingFrequencies(best_icv_itemset.items) else Double.NegativeInfinity
        // w3 : discriminating pattern length weight
        val w3 = if(listFeatures_str.contains("L")) discriminatingLengths(best_icv_itemset.items) else Double.NegativeInfinity
        // all discriminating weights array list
        val discriminating_weights = Array(w1, w2, w3)
        
        // ********************** Updating weights list by adding discriminating weights **********************
        update_weight_list(1, discriminating_weights, nbToAddOrRemove)
        
        // ********************** Update all patterns description by adding 1,2,3 new elements ****************
        if(currentIteration>0){
            (0 until (currentIteration * params.pairsPerQuery)).foreach {
                i => trainingPairs(i).update_pairs_new(featureMap.featureCount, 1)
            }
        }
    }
  
  

    //############################################################################################################
    //############################################################################################################

    def remove_discriminant_features_from_features_list() = {

        // ********************** Updating feature map by removing discriminating feature *********************
        var nbToAddOrRemove = 1 // discriminating features elements to be added (or removed when finished)
        nbToAddOrRemove = if(listFeatures_str.contains("F")) nbToAddOrRemove+1 else nbToAddOrRemove
        nbToAddOrRemove = if(listFeatures_str.contains("L")) nbToAddOrRemove+1 else nbToAddOrRemove
        
        var discriminating_features = get_discriminating_features_list(nbToAddOrRemove)
        
        update_features_list(2, nbToAddOrRemove, discriminating_features)
        
        // ********************** Updating feature map by removing discriminating features ********************
        update_featureMap()
        
        // ********************** get discriminating features elements to be removed **************************
        var it=1
        val w1 = all_w(allFeatures.size)

        val w2 = 
            if(listFeatures_str.contains("F")){
                it = it+1
                all_w(allFeatures.size+it-1)
            } else {
                Double.NegativeInfinity
            }
        
        val w3 = 
            if(listFeatures_str.contains("L")){
                it = it+1
                all_w(allFeatures.size+it-1)
            } else {
                Double.NegativeInfinity
            }
        
        val discriminating_weights = Array(w1, w2, w3)
        
        // ********************** Updating weights list by removing discriminating weights ********************
        update_weight_list(2, discriminating_weights, nbToAddOrRemove)
        
        // ********************** Update all patterns description by removing 1,2 or 3 elements ***************
        if( currentIteration > 0 ){
            (0 until (currentIteration * params.pairsPerQuery)).foreach {
                i => trainingPairs(i).update_pairs_new(featureMap.featureCount, 2)
            }
        }
    }

}




