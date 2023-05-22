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


object MethodSelector {
  def apply() = new MethodSelector()
  
}

class MethodSelector {
  //***********************************************************
  var method: String = "letsip"
  var dataset: Dataset[Set[Int]] = null
  var dataset_path: String = "zoo-1.txt"
  var fimi_dataset_path: String = "zoo-1.txt"
  var minsup: Int = 1
  var querySize: Int = 5
  var queryRetention: Int = 0
  var nb_iterations: Int = 100
  var oracle: String = "eflexics"
  var seed: Long = System.nanoTime().hashCode()
  //***********************************************************
  var jmax: Double = 0.1
  var aggregation: String = "lin"
  //***********************************************************
  var ranker: Ranker = null
  var params: Parameters = null
  var user: Ranker = FrequencyRanker
  var allFeatures:Array[Features] = null
  var allFeatures_count: Array[Int] = null
  var allFeatures_str_tab: Array[String] = null
  //***********************************************************
  var current_iteration: Int = -1
  // var run_sampling: Boolean = true
  // var run_learning: Boolean = false
  var letsip_instance: LetSIP = null
  //***********************************************************
  
  //####################################################################################################################
  //####################################################################################################################
  
  def init(args: Array[String]): Unit = {
    current_iteration = 0
    parse_params(args)
    select_method()
  }
  
  /*
  def launch(args: Array[String]): String = {
    if(current_iteration < 0) {
      init(args)
      // run_learning = true
      // run_sampling = false
      
      // val iteration_results = letsip_instance.run_sampling()
      // val patterns_sampled = letsip_instance.send_patterns_sampled()
      
      // patterns_sampled
      
      ***************************** UN-COMMENT BELOW IF YOU ONLY WANT A SIMULATION  **********************************
      simulation()
    }
    else {
      
      if(run_sampling) {
        run_learning = true
        run_sampling = false
        
        letsip_instance.run_sampling()
        letsip_instance.send_patterns_sampled()
      }
      else {
        //run_sampling = true
        //run_learning = false
        current_iteration = current_iteration+1
        
        letsip_instance.run_learning()
        letsip_instance.run_sampling()
        letsip_instance.send_patterns_sampled()
      } 
    }
    
  }
  */
  /*
  def get_user_ranking_and_learn(user_ranking: String): String = {
    letsip_instance.get_user_ranking(user_ranking)
    launch(Array())
  }
  */
  
  //####################################################################################################################
  //####################################################################################################################
  
  def select_method(): Unit = {
    letsip_instance = method match {
      case "letsip" => 
        // letsip original
        LetSIP(dataset=dataset, datasetPath=fimi_dataset_path, minsup=minsup,
                                  jmax=jmax, user=ranker, params=params, oracle=oracle,
                                  listFeatures=allFeatures, seed=seed)
      case "dispale" =>
        // letsip ++ discriminating patterns
        // --> (learning on Transactions consider the transactions inside the
        // --> the discriminating pattern cover
        Dispale(dataset=dataset, datasetPath=fimi_dataset_path, minsup=minsup,
                                  jmax=jmax, user=ranker, params=params, oracle=oracle,
                                  aggregation_function=aggregation, listFeatures=allFeatures, 
                                  listFeatures_count=allFeatures_count, listFeatures_str=allFeatures_str_tab, seed=seed)
      case "dispale-2" =>
        // letsip ++ discriminating patterns
        // --> (learning on Transactions consider the common transactions between
        // --> the discriminating pattern and all the patterns of the query's cover
        Dispale_2(dataset=dataset, datasetPath=fimi_dataset_path, minsup=minsup,
                                  jmax=jmax, user=ranker, params=params, oracle=oracle,
                                  aggregation_function=aggregation, listFeatures=allFeatures, 
                                  listFeatures_count=allFeatures_count, listFeatures_str=allFeatures_str_tab, seed=seed)
    }
    // letsip_instance
  }
  
  def select_method_simulation(method: String, dataset: Dataset[Set[Int]], datasetPath: String, minsup: Int,
                    rank_function: Ranker, params: Parameters, listFeatures: Array[Features],
                    listFeatures_str: Array[String], listFeatures_count: Array[Int], eta: Double = 0.15,
                    jmax: Double = 0.05, aggreg: String = "lin", oracle: String = "eflexics",
                    seed: Long = System.nanoTime().hashCode()): Unit = {
    letsip_instance = method match {
      case "letsip" => 
        // letsip original
        LetSIP(dataset=dataset, datasetPath=datasetPath, minsup=minsup,
                                  jmax=jmax, user=rank_function, params=params, oracle=oracle,
                                  listFeatures=listFeatures, seed=seed)
      case "dispale" =>
        // dispale (discriminating patterns)
        // --> (learning on Transactions consider the transactions inside the
        // --> the discriminating pattern cover
        Dispale(dataset=dataset, datasetPath=datasetPath, minsup=minsup,
                                  jmax=jmax, user=rank_function, params=params, oracle=oracle,
                                  aggregation_function=aggreg, listFeatures=listFeatures, 
                                  listFeatures_count=listFeatures_count, listFeatures_str=listFeatures_str, seed=seed)
      case "dispale-2" =>
        // dispale-2 (discriminating patterns)
        // --> (learning on Transactions consider the common transactions between
        // --> the discriminating pattern and all the patterns of the query's cover
        Dispale_2(dataset=dataset, datasetPath=datasetPath, minsup=minsup,
                                  jmax=jmax, user=rank_function, params=params, oracle=oracle,
                                  aggregation_function=aggreg, listFeatures=listFeatures, 
                                  listFeatures_count=listFeatures_count, listFeatures_str=listFeatures_str, seed=seed)
    }
  }
  
  //####################################################################################################################
  //####################################################################################################################
  
  def parse_params(args: Array[String]): Unit = {
    val parse_cmd = ParseArgs.get_parse(args)
    if (parse_cmd == null) {
      // There's no parameter set
    } else {
      // required arguments
      seed = parse_cmd.getOptionValue("seed").toLong
      method = parse_cmd.getOptionValue("method")
      dataset_path = parse_cmd.getOptionValue("data")
      fimi_dataset_path = parse_cmd.getOptionValue("fimi")
      minsup = parse_cmd.getOptionValue("fmin").toInt
      querySize = parse_cmd.getOptionValue("query").toInt
      nb_iterations = parse_cmd.getOptionValue("iter").toInt
      queryRetention = parse_cmd.getOptionValue("retention").toInt
      val tilt = parse_cmd.getOptionValue("tilt").toDouble
      val features_list = parse_cmd.getOptionValue("features").split("-")
      val rankFunction = parse_cmd.getOptionValue("rank")
      
      //****************************************************************************************************************
      //****************************************************************************************************************
      
      // optional arguments
      val eta = if(parse_cmd.hasOption("eta")) parse_cmd.getOptionValue("eta").toDouble else 0.15
      jmax = if(parse_cmd.hasOption("jmax")) parse_cmd.getOptionValue("jmax").toDouble else 0.05
      oracle = if(parse_cmd.hasOption("oracle")) parse_cmd.getOptionValue("oracle") else "eflexics"
      aggregation = if(parse_cmd.hasOption("aggregation")) parse_cmd.getOptionValue("aggregation").toLowerCase else "lin"
      val features_update = if(parse_cmd.hasOption("features-update")) parse_cmd.getOptionValue("features-update") else "ALL"
      
      //****************************************************************************************************************
      // *************************** NOW WE PREPARE THE VARIABLES TO BE USED BY THE PROGRAM ****************************
      //****************************************************************************************************************
      
      dataset = CP4IMData.load(path = dataset_path, CP4IMParameters(dropLabel = true))
      //****************************************************************************************************************
      ranker = rankFunction.toLowerCase match {
        //case "plranker"                => PLRanker
        case "frequencyranker"         => FrequencyRanker
        case "surprisingnessranker"    => SurprisingnessRanker(dataset)
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
        if(i>0){
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
      
      var j = 0
      var nb_ft = 0
      while(j < allFeatures_count.size) {
        nb_ft = nb_ft + allFeatures_count(j)
        j=j+1
      }
      //****************************************************************************************************************
      //****************************************************************************************************************
      val features = feature.compose(allFeatures:_*)
      val scdParams = ScdParameters(iterations = FixedIterations(nb_ft), features_update = ft_update_all)
      params = Parameters(tilt=tilt, featureMap=features, scd=scdParams, queryOverlap=queryRetention,
                            iterations=nb_iterations, querySize=querySize, eta=eta)
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

