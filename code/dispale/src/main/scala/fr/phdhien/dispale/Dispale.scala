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

import fr.phdhien.dispale.feature.{Features, FeatureMap, FeatureMapGenerator, Patterns, DiscriminativeFrequency, DiscriminativeLength,
                                   PooledPatterns, PooledFrequency, PooledLength}

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
                multi: MultiDiscParams = MultiDiscParams()
            ): Dispale = {
        //
        new Dispale(
                        method, dataset, datasetPath, datasetPathFimi, params, 
                        listFeatures, listFeatures_count, listFeatures_str, 
                        minsup, oracle, algo, learner, aggregation_function, user, seed, multi
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
                val multi: MultiDiscParams = MultiDiscParams()   // multi sub-pattern settings (m, selection, ...)
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
    icvObject.setSelection(multi.nbSubPatterns,
        SubPatternSelectors.create(multi.selection, multi.redundancyWeight, multi.maxOverlap, multi.minGain))
    val pooled: Boolean = multi.expansion.toLowerCase == "pooled"
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

    // one feature group per type:
    // - separate: each group holds one feature per sub-pattern
    //       [Patterns(S_1..S_m), DiscriminativeFrequency(S_1..S_m), DiscriminativeLength(S_1..S_m)]
    // - pooled: each group is a single feature summing over the m sub-patterns
    //       [PooledPatterns, PooledFrequency, PooledLength]
    // the frequency / length groups are only added when the base features contain Frequency / Length
    def get_discriminating_features_list(): Array[Features] = {
        val hasF = listFeatures_str.contains("F")
        val hasL = listFeatures_str.contains("L")
        if(pooled) {
            PooledPatterns.setRefPatterns(best_icv_itemsets)
            PooledFrequency.setRefPatterns(best_icv_itemsets)
            PooledLength.setRefPatterns(best_icv_itemsets)
            Array[Features](PooledPatterns) ++ (if(hasF) Array[Features](PooledFrequency) else Array[Features]()) ++
                (if(hasL) Array[Features](PooledLength) else Array[Features]())
        } else {
            Patterns.setNbElt(best_icv_itemsets.length)
            Patterns.setRefPatterns(best_icv_itemsets)
            best_icv_itemsets.foreach { s =>
                if(!discriminatingPatterns.contains(s.items)) discriminatingPatterns.put(s.items, 0.0)
                if(hasF && !discriminatingFrequencies.contains(s.items)) discriminatingFrequencies.put(s.items, 0.0)
                if(hasL && !discriminatingLengths.contains(s.items)) discriminatingLengths.put(s.items, 0.0)
            }
            DiscriminativeFrequency.setRefPatterns(best_icv_itemsets)
            DiscriminativeLength.setRefPatterns(best_icv_itemsets)
            Array[Features](Patterns) ++ (if(hasF) Array[Features](DiscriminativeFrequency) else Array[Features]()) ++
                (if(hasL) Array[Features](DiscriminativeLength) else Array[Features]())
        }
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

    // number of features in each discriminating feature group: one per sub-pattern, or one when pooled
    def features_per_group: Int = if(pooled) 1 else best_icv_itemsets.length

    // weights of the discriminating features, in the same order as get_discriminating_features_list():
    // for each feature j of a group, its (pattern, frequency, length) weights, NegativeInfinity when the type is not used
    def get_discriminating_weights(weightOf: (Int, Int) => Double): Array[Array[Double]] = {
        val hasF = listFeatures_str.contains("F")
        val hasL = listFeatures_str.contains("L")
        Array.tabulate(features_per_group) { j =>
            val wP = weightOf(0, j)
            val wF = if(hasF) weightOf(1, j) else Double.NegativeInfinity
            val wL = if(hasL) weightOf(if(hasF) 2 else 1, j) else Double.NegativeInfinity
            Array(wP, wF, wL)
        }
    }

    //############################################################################################################
    //############################################################################################################

    // transfer the weights learned for the discriminating features to the base features, then remember them.
    // For a base item i, with T_i the selected sub-patterns that contain i (T_t: those contained in transaction t):
    // - separate: d_i = Σ_{j in T_i} w_P(j) / norm, with norm = m (transferNorm "m") or 1 ("none");
    //             frequency / length: d = Σ_j w_F(j) / norm, Σ_j w_L(j) / norm
    // - pooled:   d_i = |T_i| / m · w_P(pool); frequency / length: d = w_F(pool), w_L(pool)
    // then w_i <- aggregation(w_i, d_i): ADD w + η d, LIN w (1 + η d), EXP w e^(η d). Items with T_i empty are unchanged.
    def apportion_discriminating_weights(new_w: ArrayVector, discriminating_weights: Array[Array[Double]]): Unit = {
        val m = best_icv_itemsets.length
        val norm = if(multi.transferNorm.toLowerCase == "none") 1.0 else m.toDouble
        // contribution of sub-pattern j to a base element it covers
        def share(j: Int): Double = if(pooled) discriminating_weights(0)(0) / m else discriminating_weights(j)(0) / norm
        def total(t: Int): Double =
            if(pooled) discriminating_weights(0)(t) else discriminating_weights.map(_(t)).sum / norm

        var it = 0
        while(it < listFeatures_str.size) {
            var deb = 0
            (0 until it).foreach{ i => deb = deb+listFeatures_count(i) }
            val fin = deb + listFeatures_count(it)

            if(listFeatures_str(it) == "I"){
                (deb until fin).foreach{ i =>
                    val containing = (0 until m).filter(j => best_icv_itemsets(j).items(i-deb))
                    if(containing.nonEmpty)
                        new_w(i) = aggregation_value(new_w(i), containing.map(share).sum)
                }
            }
            else if(listFeatures_str(it) == "T"){
                (deb until fin).foreach{ i =>
                    val covering = (0 until m).filter(j => best_icv_itemsets(j).mask.isCovering(i-deb))
                    if(covering.nonEmpty)
                        new_w(i) = aggregation_value(new_w(i), covering.map(share).sum)
                }
            }
            else if(listFeatures_str(it) == "F"){
                (deb until fin).foreach{ i => new_w(i) = aggregation_value(new_w(i), total(1)) }
            }
            else if(listFeatures_str(it) == "L"){
                (deb until fin).foreach{ i => new_w(i) = aggregation_value(new_w(i), total(2)) }
            }
            it = it+1
        }

        // remember each sub-pattern's own weights, to start from them when it is selected again
        if(!pooled) {
            best_icv_itemsets.zipWithIndex.foreach { case (s, j) =>
                val Array(wP, wF, wL) = discriminating_weights(j)
                discriminatingPatterns(s.items) = wP
                if(listFeatures_str.contains("F")) discriminatingFrequencies(s.items) = wF
                if(listFeatures_str.contains("L")) discriminatingLengths(s.items) = wL
            }
        }
    }

    //############################################################################################################
    //############################################################################################################

    def add_discriminant_features_to_features_list() = {
        
        // ********************** New discriminating features *************************************************
        val discriminating_features = get_discriminating_features_list()
        val g = features_per_group
        val nbToAdd = g * nb_discriminating_feature_types
        
        // ********************** update features list : add discriminating features **************************
        allFeatures = allFeatures ++ discriminating_features
        
        // ********************** Updating feature map by adding discriminating features **********************
        update_featureMap()
        
        // ********************** Updating weights list by adding discriminating weights **********************
        // separate: start from the weight learned the last time each sub-pattern was used (0 if never used)
        // pooled: the pooled features mean something different every iteration, so they start at 0
        val discriminating_weights = get_discriminating_weights { (t, j) =>
            if(pooled) 0.0
            else {
                val s = best_icv_itemsets(j).items
                t match {
                    case 0 => discriminatingPatterns(s)
                    case _ if t == 1 && listFeatures_str.contains("F") => discriminatingFrequencies(s)
                    case _ => discriminatingLengths(s)
                }
            }
        }
        val e = all_w.length
        val new_w = ArrayVector.zeros(e + nbToAdd)
        (0 until e).foreach { i => new_w(i) = all_w(i) }
        (0 until g).foreach { j =>
            new_w(e + j) = discriminating_weights(j)(0)
            var t = 1
            if(listFeatures_str.contains("F")){ new_w(e + t*g + j) = discriminating_weights(j)(1); t += 1 }
            if(listFeatures_str.contains("L")){ new_w(e + t*g + j) = discriminating_weights(j)(2) }
        }
        all_w = new_w
        
        // ********************** Update all patterns description by adding the new elements ******************
        // clueHistory "none" (original): pairs of earlier iterations get 0 for the new features, so only the
        //   current query informs the sub-pattern weights
        // clueHistory "full": recompute the new features for the patterns of earlier iterations too, so the
        //   sub-pattern weights are learned from every pair collected so far
        if(currentIteration>0){
            val fullHistory = multi.clueHistory.toLowerCase == "full"
            (0 until (currentIteration * params.pairsPerQuery)).foreach { i =>
                if(fullHistory) {
                    val old = trainingPairs(i)
                    trainingPairs(i) = new RankedPair(old.preferred, old.dispreferred, featureMap)
                }
                else
                    trainingPairs(i).update_pairs_new(featureMap.featureCount, 1)
            }
        }
    }

    //############################################################################################################
    //############################################################################################################

    def remove_discriminant_features_from_features_list() = {

        // ********************** Updating feature map by removing discriminating feature *********************
        val g = features_per_group
        val nbToRemove = g * nb_discriminating_feature_types
        allFeatures = allFeatures.take(allFeatures.size - nb_discriminating_feature_types)
        update_featureMap()
        
        // ********************** get discriminating features elements to be removed **************************
        // they are stored right after the base features
        val base = featureMap.featureCount
        val discriminating_weights = get_discriminating_weights { (t, j) => all_w(base + t*g + j) }
        
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




