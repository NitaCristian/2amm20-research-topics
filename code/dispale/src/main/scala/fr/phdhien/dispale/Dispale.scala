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
                learner: String = "scd",
                aggregation_function: String = "lin", 
                user: Ranker = FrequencyRanker,
                seed: Long = System.nanoTime().hashCode(),
                nbSubPatterns: Int = 1,
                selection: String = "complementary",
                redundancyWeight: Double = 1.0
            ): Dispale = {
        //
        new Dispale(
                        method, dataset, datasetPath, datasetPathFimi, params, 
                        listFeatures, listFeatures_count, listFeatures_str, 
                        minsup, oracle, algo, learner, aggregation_function, user, seed,
                        nbSubPatterns, selection, redundancyWeight
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
                override val learner: String = "scd",
                val aggregation_function: String = "lin", 
                override val user: Ranker = FrequencyRanker, 
                override val seed: Long = System.nanoTime().hashCode(),
                val nbSubPatterns: Int = 1,                 // max #discriminating sub-patterns used per iteration (m)
                val selection: String = "complementary",    // how they are selected: top | complementary
                val redundancyWeight: Double = 1.0          // redundancy penalty of the complementary selection
            ) extends LetSIP(method, dataset, datasetPath, params, listFeatures, minsup, oracle, algo, learner, user, seed) {
    //
    //************************************************************************************************************
    var allFeatures: Array[Features] = listFeatures
    //************************************************************************************************************
    var best_icv_itemsets: Array[Itemset] = Array()   // discriminating sub-patterns of the current iteration
    var discriminatingPatterns = HashMap[Set[Int], Double]()  // used to save discriminating Patterns
    var discriminatingLengths = HashMap[Set[Int], Double]()  // used to save discriminating pattern length weight
    var discriminatingFrequencies = HashMap[Set[Int], Double]()  // used to save discriminating pattern frequency weight
    //************************************************************************************************************
    // used to compute best ICV itemset
    var icvObject : BestICVSubset = new BestICVSubset(params.querySize, datasetPathFimi)
    icvObject.setSelection(nbSubPatterns, selection, redundancyWeight)
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
                    //
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
                    //
                    println("\n~~~~~~~~~~~~~~~~~~~~~~\n")
                    //
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
                    val i = currentIteration * params.pairsPerQuery + j
                    trainingPairs(i) = new RankedPair(preferred, dispreferred, featureMap)
        }
        
        //
        // -------------------------------------------------------------------------
        //
        learner.toLowerCase match {
            case "scd" =>
                scd_learn()
            case "ranksvm" | "rank_svm" =>
                rankSVM_learn()
        }
        //
        // -------------------------------------------------------------------------
        //
        
        remove_discriminant_features_from_features_list()

        d = featureMap.featureCount
        
        // ********************** GET THE LEARNED FUNCTION ****************************************************
        // assign the field (not a local val): it is the weight function used to sample the next query
        learnedWeight = LogisticWeight(params.a, all_w, featureMap)
        
        // ********************** PRINT LOGS ******************************************************************
        val iter_anaDAta = getAnaData(currentIteration, currentQuery, learnedWeight)
        val iter_results = getIterationOutcome(currentIteration, currentQuery, learnedWeight)
        
        iter_results
        // (iter_results, HotStart(all_w, z))
    }

    //========================================================================================
    //================== LEARNING USING SCD (Stochastic Coordinate Descent) ==================
    //========================================================================================
    
    override def scd_learn() = {
        //
        // ****************************************************************************************************
        // ********************** UPDATE FEATURES ELEMENTS' WEIGHT WRT. THE STRATEGY CHOOSEN (features-update)
        val (_, newState) = updateWeights()
        // val (_, updated_state) = updateWeights()
    }
    
    override def updateWeights() = {

        //  UPDATE FEATURES ELEMENTS WEIGHT W.R.T. THE STRATEGY CHOOSEN (see argument 'features-update' in Main)
        val m = (currentIteration + 1) * params.pairsPerQuery
        val trainingExamples = trainingPairs.view(0, m)

        // WarmStart: recompute the inner products of all examples, since the features and
        // the weights changed (discriminating features added, weights apportioned) since the last call
        val state: ScdState = WarmStart(all_w)
        current_state = state

        SCD.optimize(
                        loss, trainingExamples, currentIteration, 
                        params.scd.copy( lambda= params.scd.lambda * (currentIteration + 1) ),
                        state=state, m=Some(m), d=Some(nb_features)
                    )(rnd)
    }

    //############################################################################################################
    //############################################################################################################
    
    def covert_to_itemset(dataset: Dataset[Set[Int]], icvObject : BestICVSubset, j: Int): Itemset = {
        Itemset(extractItems(icvObject, j), dataset, extractMask(dataset, icvObject, j))
    }

    def extractItems(icvObject : BestICVSubset, j: Int): Set[Int] = {
        val sol = icvObject.getSolutionItems(j)
        var solution = scala.collection.mutable.Set[Int]()
        val it = sol.iterator();
        while(it.hasNext()) {
            solution.add(it.next())
        }
        solution.toSet
    }

    def extractMask(dataset: Dataset[Set[Int]], icvObject : BestICVSubset, j: Int): Option[Mask] = {
        val builder = new MaskBuilder(datasetSize = dataset.size)
        val cov = icvObject.getSolutionCover(j)
        val it = cov.iterator();
        while(it.hasNext()) {
            builder.set(it.next())
        }
        Some(builder.build())
    }

    //############################################################################################################
    //############################################################################################################

    // feature types added for each discriminating sub-pattern: the pattern itself, and its frequency / length
    // when the base features contain Frequency / Length
    def nb_discriminating_feature_types: Int = {
        1 + (if(listFeatures_str.contains("F")) 1 else 0) + (if(listFeatures_str.contains("L")) 1 else 0)
    }

    // one feature group per type, each group holding one feature per sub-pattern:
    // [Patterns(S_1..S_m), DiscriminativeFrequency(S_1..S_m), DiscriminativeLength(S_1..S_m)]
    def get_discriminating_features_list(): Array[Features] = {
        val m = best_icv_itemsets.length
        var discriminating_features = Array[Features](Patterns)
        Patterns.setNbElt(m)
        Patterns.setRefPatterns(best_icv_itemsets)

        best_icv_itemsets.foreach { s => 
            if(!discriminatingPatterns.contains(s.items)) discriminatingPatterns.put(s.items, 0.0)
        }
        if(listFeatures_str.contains("F")){
            DiscriminativeFrequency.setRefPatterns(best_icv_itemsets)
            discriminating_features :+= DiscriminativeFrequency
            best_icv_itemsets.foreach { s => 
                if(!discriminatingFrequencies.contains(s.items)) discriminatingFrequencies.put(s.items, 0.0)
            }
        }
        if(listFeatures_str.contains("L")){
            DiscriminativeLength.setRefPatterns(best_icv_itemsets)
            discriminating_features :+= DiscriminativeLength
            best_icv_itemsets.foreach { s => 
                if(!discriminatingLengths.contains(s.items)) discriminatingLengths.put(s.items, 0.0)
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
        best_icv_itemsets = Array.tabulate(icvObject.getNbSelected()) { j => covert_to_itemset(dataset, icvObject, j) }
        
        best_icv_itemsets.zipWithIndex.foreach { case (s, j) => 
            println(f"\n++$currentIteration-$j-${s.items}-icv=${icvObject.getSolutionICV(j)}%.4f\n")
        }
        
        icvObject.reset()
    }
    
    //############################################################################################################
    //############################################################################################################

    def aggregation_value(new_val: Double, disc_val: Double) = {
        aggregation_function match {
            case "lin" => new_val * (1 + (params.eta*disc_val))
            case "exp" => new_val * exp(params.eta*disc_val)
            // additive: the direction does not depend on the sign of the item weight
            // (lin/exp make a negative weight more negative when the sub-pattern is liked)
            case "add" => new_val + params.eta*disc_val
        }
    }
    
    //############################################################################################################
    //############################################################################################################

    // weights of the discriminating features, in the same order as get_discriminating_features_list():
    // (pattern, frequency, length) weight of each sub-pattern, NegativeInfinity when the type is not used
    def get_discriminating_weights(weightOf: (Int, Int) => Double): Array[Array[Double]] = {
        val m = best_icv_itemsets.length
        val hasF = listFeatures_str.contains("F")
        val hasL = listFeatures_str.contains("L")
        Array.tabulate(m) { j =>
            val wP = weightOf(0, j)
            val wF = if(hasF) weightOf(1, j) else Double.NegativeInfinity
            val wL = if(hasL) weightOf(if(hasF) 2 else 1, j) else Double.NegativeInfinity
            Array(wP, wF, wL)
        }
    }

    //############################################################################################################
    //############################################################################################################

    // apportion the weights learned for the discriminating sub-patterns to the base features.
    // When several sub-patterns share an item (or transaction), their aggregations are applied one after the other
    def apportion_discriminating_weights(new_w: ArrayVector, discriminating_weights: Array[Array[Double]]): Unit = {
        best_icv_itemsets.zipWithIndex.foreach { case (s, j) =>
            val Array(wP, wF, wL) = discriminating_weights(j)
            var it = 0
            while(it < listFeatures_str.size) {
                var deb = 0
                (0 until it).foreach{ i => deb = deb+listFeatures_count(i) }
                val fin = deb + listFeatures_count(it)

                if(listFeatures_str(it) == "I"){
                    (deb until fin).foreach{ i =>
                        if(s.items(i-deb)){
                            new_w(i) = aggregation_value(new_w(i), wP)
                        }
                    }
                }
                else if(listFeatures_str(it) == "T"){
                    (deb until fin).foreach{ i =>
                        if(s.mask.isCovering(i-deb)){
                            new_w(i) = aggregation_value(new_w(i), wP)
                        }
                    }
                }
                else if(listFeatures_str(it) == "L"){
                    (deb until fin).foreach{ i =>
                        new_w(i) = aggregation_value(new_w(i), wL)
                    }
                    discriminatingLengths(s.items) = wL
                }
                else if(listFeatures_str(it) == "F"){
                    (deb until fin).foreach{ i =>
                        new_w(i) = aggregation_value(new_w(i), wF)
                    }
                    discriminatingFrequencies(s.items) = wF
                }
                it = it+1
            }
            discriminatingPatterns(s.items) = wP
        }
    }

    //############################################################################################################
    //############################################################################################################

    def add_discriminant_features_to_features_list() = {
        
        // ********************** New discriminating features *************************************************
        val discriminating_features = get_discriminating_features_list()
        val nbToAdd = best_icv_itemsets.length * nb_discriminating_feature_types
        
        // ********************** update features list : add discriminating features **************************
        allFeatures = allFeatures ++ discriminating_features
        
        // ********************** Updating feature map by adding discriminating features **********************
        update_featureMap()
        
        // ********************** Updating weights list by adding discriminating weights **********************
        // start from the weight learned the last time each sub-pattern was used (0 if never used)
        val discriminating_weights = get_discriminating_weights { (t, j) =>
            val s = best_icv_itemsets(j).items
            t match {
                case 0 => discriminatingPatterns(s)
                case _ if t == 1 && listFeatures_str.contains("F") => discriminatingFrequencies(s)
                case _ => discriminatingLengths(s)
            }
        }
        val e = all_w.length
        val new_w = ArrayVector.zeros(e + nbToAdd)
        (0 until e).foreach { i => new_w(i) = all_w(i) }
        val m = best_icv_itemsets.length
        (0 until m).foreach { j =>
            new_w(e + j) = discriminating_weights(j)(0)
            var t = 1
            if(listFeatures_str.contains("F")){ new_w(e + t*m + j) = discriminating_weights(j)(1); t += 1 }
            if(listFeatures_str.contains("L")){ new_w(e + t*m + j) = discriminating_weights(j)(2) }
        }
        all_w = new_w
        
        // ********************** Update all patterns description by adding the new elements ******************
        if(currentIteration>0){
            (0 until (currentIteration * params.pairsPerQuery)).foreach {
                i => {
                        trainingPairs(i).update_pairs_new(featureMap.featureCount, 1)
                    }
            }
        }
    }

    //############################################################################################################
    //############################################################################################################

    def remove_discriminant_features_from_features_list() = {

        // ********************** Updating feature map by removing discriminating feature *********************
        val nbToRemove = best_icv_itemsets.length * nb_discriminating_feature_types
        allFeatures = allFeatures.take(allFeatures.size - nb_discriminating_feature_types)
        update_featureMap()
        
        // ********************** get discriminating features elements to be removed **************************
        // they are stored right after the base features
        val base = featureMap.featureCount
        val m = best_icv_itemsets.length
        val discriminating_weights = get_discriminating_weights { (t, j) => all_w(base + t*m + j) }
        
        // ********************** Updating weights list by removing discriminating weights ********************
        val new_w = ArrayVector.zeros(all_w.length - nbToRemove)
        (0 until base).foreach { i => new_w(i) = all_w(i) }
        apportion_discriminating_weights(new_w, discriminating_weights)
        all_w = new_w
        
        // ********************** Update all patterns description by removing the elements ********************
        if( currentIteration > 0 ){
            (0 until (currentIteration * params.pairsPerQuery)).foreach {
                i => trainingPairs(i).update_pairs_new(featureMap.featureCount, 2)
            }
        }
    }

}




