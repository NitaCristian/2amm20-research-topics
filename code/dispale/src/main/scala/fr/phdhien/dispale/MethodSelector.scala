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

import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.{CP4IMData, CP4IMParameters, Itemset}
import be.kuleuven.pmlib.patterns.{Mask, MaskBuilder}
//
import be.kuleuven.flexics._
import be.kuleuven.flexics.{GetWeight, Support}
//import be.kuleuven.flexics.eflexics.EclatProblem
import be.kuleuven.wg4ps.specialised.EclatProblem
import be.kuleuven.weightgen.{Uniform, WeightFunction, WeightGenConfiguration, WeightMcConfiguration}
//
import be.kuleuven.scd._
//import be.kuleuven.scd.{FixedIterations, IterationCalculator}

import fr.phdhien.dispale.feature._

import java.io.File
import java.io.PrintWriter

import scala.annotation.tailrec
import scala.collection.mutable.HashMap
import scala.util.Random

/* 
 * @author Arnold Hien
 */
object MethodSelector {
    def apply() = new MethodSelector()
  
}
class MethodSelector{

    //***********************************************************
    var method: String = "letsip"
    var seed: Long = System.nanoTime().hashCode()
    //***********************************************************
    var dataset: Dataset[Set[Int]] = null
    var dataset_path: String = "zoo-1.txt"
    var fimi_dataset_path: String = "zoo-1.txt"
    //***********************************************************
    var minsup: Int = 1
    var querySize: Int = 5
    var queryRetention: Int = 0
    var nb_iterations: Int = 100
    //***********************************************************
    var aggregation: String = "lin"
    //***********************************************************
    var multi: MultiDiscParams = MultiDiscParams()
    //***********************************************************
    var oracle: String = "flexics"
    var algo: String = "eflexics"
    var learner: String = "scd"
    //***********************************************************
    var ranker: Ranker = null
    var params: Parameters = null
    var user: Ranker = FrequencyRanker
    //***********************************************************
    var allFeatures:Array[Features] = null
    var allFeatures_count: Array[Int] = null
    var allFeatures_str_tab: Array[String] = null
    //***********************************************************
    var current_iteration: Int = -1
    var letsip_instance: LetSIP = null
    //***********************************************************

    //############################################################################################################
    //############################################################################################################

    def init(args: Array[String]): Unit = {
        current_iteration = 0
        parse_params(args)
        select_method()
    }

    //############################################################################################################
    //############################################################################################################
    
    def select_method(): Unit = {
        letsip_instance = method match {
        
        case "letsip" | "lutom" => 
            LetSIP(
                        method=method, dataset=dataset, datasetPath=dataset_path, //datasetPathFimi=fimi_dataset_path, 
                        params=params, minsup=minsup, oracle=oracle, algo=algo, 
                        learner=learner, listFeatures=allFeatures, 
                        user=ranker, seed=seed
                    )
        case "dispale" | "lutomDisc" =>
            Dispale(
                        method=method, dataset=dataset, datasetPath=dataset_path, datasetPathFimi=fimi_dataset_path, 
                        params=params, listFeatures=allFeatures, listFeatures_count=allFeatures_count, 
                        learner=learner, listFeatures_str=allFeatures_str_tab, minsup=minsup, 
                        oracle=oracle, algo=algo, aggregation_function=aggregation, 
                        user=ranker, seed=seed, multi=multi
                    )
        }
        // letsip_instance
    }

    //####################################################################################################################
    //####################################################################################################################
    
    def parse_params(args: Array[String]): Unit = {

        val parse_cmd = ParseArgs.get_parse(args)
        
        if (parse_cmd == null) {
            // There's no parameter set
        } else {
            // required arguments
            method = parse_cmd.getOptionValue("method")
            dataset_path = parse_cmd.getOptionValue("data")
            querySize = parse_cmd.getOptionValue("query").toInt
            nb_iterations = parse_cmd.getOptionValue("iter").toInt
            queryRetention = parse_cmd.getOptionValue("retention").toInt
            val features_list = parse_cmd.getOptionValue("features").split("-")
            val rankFunction = parse_cmd.getOptionValue("rank")
            learner = parse_cmd.getOptionValue("learn")
            oracle = if(parse_cmd.hasOption("oracle")) parse_cmd.getOptionValue("oracle") else "flexics"
            algo = if(parse_cmd.hasOption("algo")) parse_cmd.getOptionValue("algo") else "eflexics"
            
            //****************************************************************************************************************
            //****************************************************************************************************************
            
            // optional arguments
            if( parse_cmd.hasOption("seed") ){
                seed = parse_cmd.getOptionValue("seed").toLong
            }
            if(method == "letsip" || method == "dispale"){
                minsup = parse_cmd.getOptionValue("fmin").toInt
            }

            fimi_dataset_path = if(parse_cmd.hasOption("fimi")) parse_cmd.getOptionValue("fimi") else ""
            val tilt = if(parse_cmd.hasOption("tilt")) parse_cmd.getOptionValue("tilt").toDouble else 10.0
            val eta = if(parse_cmd.hasOption("eta")) parse_cmd.getOptionValue("eta").toDouble else 0.15
            aggregation = if(parse_cmd.hasOption("aggregation")) parse_cmd.getOptionValue("aggregation").toLowerCase else "lin"
            val features_update = if(parse_cmd.hasOption("features-update")) parse_cmd.getOptionValue("features-update") else "ALL"
            val weightsFile = if(parse_cmd.hasOption("weights")) parse_cmd.getOptionValue("weights") else null
            def opt(name: String, default: String) = if(parse_cmd.hasOption(name)) parse_cmd.getOptionValue(name) else default
            multi = MultiDiscParams(
                nbSubPatterns = opt("nb-disc", "1").toInt,
                selection = opt("selection", "gain").toLowerCase,
                redundancyWeight = opt("redundancy-weight", "1.0").toDouble,
                maxOverlap = opt("max-overlap", "0.5").toDouble,
                minGain = opt("min-gain", "0").toDouble,
                expansion = opt("expansion", "separate").toLowerCase,
                transferNorm = opt("transfer-norm", "m").toLowerCase,
                clueHistory = opt("clue-history", "none").toLowerCase
            )
            val combos = opt("combos", "")
            // initial weights: 0 for the sampling methods (the learned score w.x starts at 0, i.e. uniform sampling,
            // and is not saturated by the logistic function); 1 for the HUI methods, which use the weights as utilities
            val initWeight = 
                if(parse_cmd.hasOption("init-weight")) parse_cmd.getOptionValue("init-weight").toDouble
                else if(method == "letsip" || method == "dispale") 0.0 else 1.0
            
            //****************************************************************************************************************
            // *************************** NOW WE PREPARE THE VARIABLES TO BE USED BY THE PROGRAM ****************************
            //****************************************************************************************************************
            
            dataset = CP4IMData.load(path = dataset_path, CP4IMParameters(dropLabel = true))
            //****************************************************************************************************************
            ranker = rankFunction.toLowerCase match {
                case "frequencyranker"         => FrequencyRanker
                case "surprisingnessranker"    => SurprisingnessRanker(dataset)
                case "gaussianranker"	       => GaussianRanker(dataset, seed, weightsFile) //GaussianRanker(dataset,seed)
                case "comboranker"             => ComboRanker(dataset, combos)
                case _                         => FrequencyRanker // default case
            }
            //****************************************************************************************************************
            // Parse Features
            var allFeatures_str = "" // all feature types first letter
            //****************************************************************************************************************
            // number of element per feature (ex: number of items 
            // for feature Item, number of Transaction for Transactions)
            allFeatures_count = Array.fill(features_list.size)(0) 
            allFeatures = Array.fill(features_list.size)(null) // list of all features
            (0 until features_list.size).foreach{ i =>
                if(i > 0){
                    allFeatures_str = allFeatures_str + "-"
                }
                
                allFeatures_str = if(features_list(i).length() > 0) allFeatures_str + features_list(i)(0).toString.toUpperCase else ""
                allFeatures(i) = features_list(i).toLowerCase match {
                    case "items" => {
                        allFeatures_count(i) = dataset.attributes.size
                        Items
                    }
                    case "transactions" => {
                        allFeatures_count(i) = dataset.size
                        Transactions
                    }
                    case "patterns" => {
                        allFeatures_count(i) = 1
                        Patterns
                    }
                    case "frequency" => {
                        allFeatures_count(i) = 1
                        Frequency
                    }
                    case "length" => {
                        allFeatures_count(i) = 1
                        Length
                    }
                    case "intercept" => {
                        allFeatures_count(i) = 1
                        Intercept
                    }
                }
            }
            allFeatures_str_tab = allFeatures_str.split("-") // list of all features types first letter : I, T, F, L
            //****************************************************************************************************************
            // check if features elements have to be updated by choosing each randomly or by taking all of them
            val ft_update_all = features_update.toLowerCase match { 
                case "all" => true
                case "rnd" => false
                case _ => true
            }

            //****************************************************************************************************************
            // ************************************************ LAUNCH RUNNING ***********************************************
            //****************************************************************************************************************
            
            var j, nb_ft = 0
            while(j < allFeatures_count.size) {
                nb_ft = nb_ft + allFeatures_count(j)
                j = j+1
            }
            //****************************************************************************************************************
            //****************************************************************************************************************
            val features = feature.compose(allFeatures:_*)
            val scdParams = ScdParameters(iterations = FixedIterations(nb_ft), features_update = ft_update_all)
            params = Parameters(tilt=tilt, featureMap=features, scd=scdParams, queryRetention=queryRetention,
                                    iterations=nb_iterations, querySize=querySize, eta=eta, initWeight=initWeight)
            //****************************************************************************************************************
            //****************************************************************************************************************
        
        }
    }
    
    //####################################################################################################################
    //####################################################################################################################
    
    def simulation(): LogisticWeight = {
        // oracle: LeapfroggingWeightGen = VanillaEFlexics,
        
        val learnedWeight = letsip_instance.loop()
        
        learnedWeight
    }


}


