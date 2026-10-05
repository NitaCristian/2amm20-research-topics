/*
 * This file is part of the DiSPaLe project (https://gitlab.com/phdhien/dispale)
 *
 * Copyright (c) 2024, Normandie Université & Université de Caen-Normandie & IMT Atlantique, France
 *
 * Licensed under the MIT license.
 *
 * See LICENSE file in the project root for full license information.
 */
package fr.phdhien.dispale

import be.kuleuven.flexics._
import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.Itemset
//import be.kuleuven.scd._
import be.kuleuven.scd.{ArrayVector, MutableVector, ScdState, SCD, ColdStart, HotStart, WarmStart, NewExamplesAppended, LogisticLoss}
import be.kuleuven.weightgen._
import be.kuleuven.weightgen.{Uniform, WeightFunction}

import com.typesafe.scalalogging.Logger

import fr.phdhien.dispale.feature.{Features, FeatureMap, FeatureMapGenerator, Patterns}
import fr.phdhien.dispale.mining.{Miner, FlexicsSampler_Oracle, HUI_Oracle}

import java.nio.file.{Files, Paths}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.collection.immutable.Set
import scala.util.Random

/* 
 * @author Arnold Hien
 */
object LetSIP {
    def apply(
                method: String,
                dataset: Dataset[Set[Int]],
                datasetPath: String,
                params: Parameters,
                listFeatures: Array[Features],
                minsup: Int = 10,
                oracle: String = "flexics", 
                algo: String = "eflexics",
                learner: String = "scd",
                user: Ranker = FrequencyRanker,
                seed: Long = System.nanoTime().hashCode()
            ): LetSIP = {
        new LetSIP(method, dataset, datasetPath, params, listFeatures, minsup, oracle, algo, learner, user, seed)
    }
}
class LetSIP(
                val method: String,
                val dataset: Dataset[Set[Int]],
                val datasetPath: String,
                val params: Parameters,
                val listFeatures: Array[Features],
                val minsup: Int = 10,
                val oracle: String = "flexics", 
                val algo: String = "eflexics",
                val learner: String = "scd",
                val user: Ranker = FrequencyRanker,
                val seed: Long = System.nanoTime().hashCode()
            ) extends LearningLogging {

    //
    println(s"USER:$user ")
    println("\n~~~~~~~~~~~~~~~~~~~~~~\n")
    //
    //************************************************************************************************************
    val rnd = new Random(seed)
    //************************************************************************************************************
    val datafile = datasetPath.split("/").takeRight(1).toList(0)
    val dataname = datafile.replace(".fimi", "")
    //************************************************************************************************************
    var featureMap = get_featureMap(dataset, params)
    val pattern_miner: Miner = get_miner(dataset, datasetPath, minsup, oracle, algo)
    //************************************************************************************************************
    var currentIteration: Int = 0
    val querySize: Int = params.querySize
    var nb_features = featureMap.featureCount
    var queryRetention = 0 // number of patterns retained from previous iteration
    //************************************************************************************************************
    val k: Int = params.querySize
    var d: Int = featureMap.featureCount
    //************************************************************************************************************
    val loss = LogisticLoss
    var all_w = new ArrayVector(Array.fill(nb_features)(params.initWeight), nb_features)
    var z = ArrayVector.zeros(params.iterations * params.pairsPerQuery)
    var learnedWeight: LogisticWeight = LogisticWeight(params.a, all_w, featureMap)
    var current_state: ScdState = HotStart(all_w, z)
    //************************************************************************************************************
    var currentQuery: Array[Itemset] = null
    var queries_array = Array.ofDim[Itemset](params.iterations, querySize)
    //************************************************************************************************************
    val trainingPairs = Array.ofDim[RankedPair](params.iterations * params.pairsPerQuery)
    //************************************************************************************************************
    protected override val logger = Logger(LoggerFactory.getLogger( get_method_log_name(method) ))
    //************************************************************************************************************

    //############################################################################################################
    //############################################################################################################

    def get_featureMap(dataset: Dataset[Set[Int]], params: Parameters) = {
        params.featureMap match {
            case generator: FeatureMapGenerator => generator.toFeatureMap(dataset)
            case map: FeatureMap => map
        }
    }

    //############################################################################################################
    //################################### FUNCTION USED TO SIMULATE ITERATIONS ###################################
    //############################################################################################################

    def loop(): LogisticWeight = { 
        @tailrec
        // def doLoop(iteration: Int, weight: WeightFunction[Itemset], state: HotStart): LogisticWeight = {
        def doLoop(weight_function: WeightFunction[Itemset]): LogisticWeight = {
            //
            queryRetention = if (currentIteration > 0) params.queryRetention else 0
            //
            currentIteration match {
                case params.iterations =>
                    val glf = weight_function.asInstanceOf[LogisticWeight]
                    logTermination(currentIteration, glf)
                    //
                    println("\n~~~~~~~~~~~~~~~~~~~~~~\n")
                    //
                    glf
                case _ =>
                    // *************** MINE A SET OF `querySize' PATTERNS *************************************
                    var msg = s"Mining $querySize itemsets to query using $oracle"
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
                    
                    // *************** PRINT LOGS *****************************************************
                    logAnaData(currentIteration, currentQuery, learnedWeight, user)
                    logIterationOutcome(currentIteration, currentQuery, learnedWeight, user)
                    
                    // *************** NEXT ITERATIONS ************************************************
                    //println("\n")
                    currentIteration += 1
                    //
                    println("\n~~~~~~~~~~~~~~~~~~~~~~\n")
                    //
                    doLoop(learnedWeight)
                    //doLoop(currentIteration + 1, learnedWeight, newState)
            }
        }

        // doLoop(0, Uniform, HotStart(all_w, z))
        doLoop(Uniform)
    }

    //############################################################################################################
    //########################################## LEARN USER PREFERENCES ##########################################
    //############################################################################################################
    
    // def run_learning(): (String, ScdState) = {
    def run_learning(): String = {
        learner.toLowerCase match {
            case "scd" =>
                scd_learn()
            case "ranksvm" | "rank_svm" =>
                rankSVM_learn()
        }
        
        // ********************** UPDATE THE LEARNED FUNCTION *************************************************
        learnedWeight = LogisticWeight(params.a, all_w, featureMap)
        val iter_anaDAta = getAnaData(currentIteration, currentQuery, learnedWeight)
        val iter_results = getIterationOutcome(currentIteration, currentQuery, learnedWeight)
        
        iter_results
        // (iter_results, newState)
        // ********************** LEARNING FINISHED *********************************************************** 
    }

    //========================================================================================
    //================================ LEARNING USING RANKSVM ================================
    //========================================================================================

    def rankSVM_learn() = {
        //
        val currentDateTime: LocalDateTime = LocalDateTime.now()
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        val formattedDate: String = currentDateTime.format(formatter)
        //
        val trainPath: String = f"results/RankSVM-DATA/train/$formattedDate/"
        val modelPath: String = f"results/RankSVM-DATA/model/$formattedDate/"
        //
        val train_dir = Paths.get(trainPath)
        val model_dir = Paths.get(modelPath)
        //
        // -------------------------------------------------------------------------
        //
        // directory.mkdir()
        // If you require it to make the entire directory path including parents,
        // use directory.mkdirs(); here instead.
        if ( !Files.exists(train_dir) ){
            Files.createDirectories(train_dir)
        }
        if ( !Files.exists(model_dir) ){
            Files.createDirectories(model_dir)
        }
        //
        // -------------------------------------------------------------------------
        //
        val all_elem = from_query_to_vector_str(trainPath, modelPath)
        val vector_str = all_elem(0)
        val abs_train_file = all_elem(1)
        val abs_model_file = all_elem(2)
        // -------------------------------------------------------------------------
        val ranksvm_learner = new RankSVMTools(d, abs_train_file, abs_model_file)
        ranksvm_learner.save_train_data(vector_str)
        
        // **************************** RANKSVM LEARNING ******************************************************
        //println("learning - launch ranksvm")
        
        ranksvm_learner.run_ranksvm_learning()
        
        //println("learning - parse ranksvm")
        
        var learned_w = ranksvm_learner.read_model_file()
        
        //-----------------------------------------------------------
        var it = 0
        while(it < d) {
            if(learned_w(it) == -1000.0) {
                learned_w(it) = all_w(it)
            }
            it += 1
        }
        //-----------------------------------------------------------
        
        //println("learning - update weights")
        
        all_w = new ArrayVector(learned_w, d)
    }
  
    def from_query_to_vector_str(trainPath: String, modelPath: String) = {
        var final_str = ""
        for(it <- Iterator.range(0, currentIteration+1)) {
            var queryIT = queries_array(it)
            final_str += "# query " + (it+1).toString + "\n"
            //
            // ***************************************************************
            //
            for(e <- Iterator.range(0, k)) {
                var f = 0
                d = featureMap.featureCount
                var features_val = Array.ofDim[String](d)
                val pattern_vector = features(queryIT(e), featureMap)
                //
                // ----------------------------------------
                while(f < d) {
                    features_val(f) = (f+1).toString + ":" + pattern_vector(f).toString
                    f += 1
                }
                // ----------------------------------------
                //
                final_str += (k-e).toString + " qid:" + (it+1).toString + " "
                final_str += features_val.mkString(" ") + "\n"
            }
            //
            // ***************************************************************
            //
        }
        val train_file = trainPath + dataname + "-it_" + currentIteration.toString + ".txt"
        val model_file = modelPath + dataname + "-it_" + currentIteration.toString + ".txt"
        //
        //final_str
        Array(final_str, train_file, model_file)
    }

    //========================================================================================
    //================== LEARNING USING SCD (Stochastic Coordinate Descent) ==================
    //========================================================================================
    
    def scd_learn() = {
        //
        // ********************** FORM PAIRS OF PATTERNS TO BE USED FOR THE LEARNING **************************
        rankedPairs(currentQuery, querySize).zipWithIndex.foreach { 
            case ((preferred, dispreferred), j) => 
                    val i = currentIteration * params.pairsPerQuery + j
                    trainingPairs(i) = new RankedPair(preferred, dispreferred, featureMap)
        }
        
        // ****************************************************************************************************
        // ********************** UPDATE FEATURES ELEMENTS' WEIGHT WRT. THE STRATEGY CHOOSEN (features-update)
        val (_, newState) = updateWeights()
    }

    //############################################################################################################
    //############################ FUNCTION THAT PREPARES THE PATTERNS FOR PAIRWISING ############################
    //############################################################################################################

    def rankedPairs(itemsets: Array[Itemset], k: Int): Iterator[(Itemset, Itemset)] = {
        for (i <- Iterator.range(0, k - 1);
            j <- Iterator.range(i + 1, k))
        yield (itemsets(i), itemsets(j))
    }

    //############################################################################################################
    //########################################## UPDATE FEATURES WEIGHTS #########################################
    //############################################################################################################

    // state: ScdState, z: MutableVector, iteration: Int, d: Int, rnd: Random) = {
    def updateWeights() = {
        //
        val m = (currentIteration + 1) * params.pairsPerQuery
        val trainingExamples = trainingPairs.view(0, m)
        //val m = params.pairsPerQuery
        //val trainingExamples = trainingPairs.view(currentIteration*m, (currentIteration+1)*m)

        val state: ScdState = 
            if(currentIteration > 0) showNewExamplesToSCD(current_state, z, currentIteration, m) else current_state

        SCD.optimize(
                        loss, trainingExamples, currentIteration, 
                        params.scd.copy( lambda = params.scd.lambda * (currentIteration + 1) ),
                        state=state, m=Some(m), d=Some(nb_features)
                    )(rnd)
    
    }
    
    //############################################################################################################
    //############################################################################################################

    def showNewExamplesToSCD(state: ScdState, z: MutableVector, iteration: Int, m: Int): ScdState = {
        val (state_w, _) = get_w_z_from_state(state, m, nb_features)
        NewExamplesAppended(state_w, z, iteration * params.pairsPerQuery)
    }

    //############################################################################################################
    //############################################################################################################
  
    def get_w_z_from_state(state: ScdState, m0: Int, d0: Int) = {
        state match {
        case ColdStart => 
            // Initialize with zeroes
            (ArrayVector.zeros(d0), ArrayVector.zeros(m0))
        case WarmStart(w0) => 
            // Precompute the inner products vector `z`
            val z0 = ArrayVector.zeros(m0)
            (w0, z0)
        case HotStart(w0, z0) => 
            // Pick where the previous call left off
            (w0, z0)
        case NewExamplesAppended(w0, z0, m1) => 
            // Update the inner products vector `z` for the new examples
            (w0, z0)
        case _ =>
            // other cases
            (ArrayVector.zeros(d0), ArrayVector.zeros(m0))
        }
    }


}



