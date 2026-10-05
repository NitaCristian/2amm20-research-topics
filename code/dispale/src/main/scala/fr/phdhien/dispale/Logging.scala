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

//import be.kuleuven.flexics.GetWeight
import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.Itemset
//import be.kuleuven.weightgen._
//import be.kuleuven.weightgen.WeightFunction
import be.kuleuven.wg4ps

import com.typesafe.scalalogging.Logger

import fr.phdhien.dispale.mining.EFlexicsWrapper

/* 
 * @author Arnold Hien
 */

trait LearningLogging {
  protected val logger: Logger
  protected final def logAction[T](action: => T)(message: String): T = {
    logger.debug(message)
    val actionResult = action
    logger.debug(s" ${message.replace("ing", "ed")}")
    actionResult
  }

  protected final def logTask(dataset: Dataset[Set[Int]],
                              minsup: Int,
                              client: Ranker,
                              params: Parameters,
                              sampler: EFlexicsWrapper,
                              seed: Long): Unit = {
    logger.info(s"${dataset.name}, ${dataset.size}x${dataset.attributes.size}, minsup=$minsup")
    logger.info(s"  Ranker = $client")
    logger.info(s"Features = ${params.featureMap}")
    logger.info(s"  Params = iterations=${params.iterations}, k=${params.querySize}, l=${params.queryRetention}")
    logger.info(f"     GLF = A=${params.a}%.3f")
    logger.info(f"     SCD = iterations=${params.scd.iterations}%s, lambda=${params.scd.lambda}%.2e")
    logger.info(s" Sampler = $sampler")
    logger.info(s"Rnd.seed = $seed")
  }

  protected final def newLogTask(dataset: Dataset[Set[Int]],
                              minsup: Int,
                              client: Ranker,
                              params: Parameters,
                              d: Int): Unit = {
    println(s"${dataset.name}, ${dataset.size}x${dataset.attributes.size}, minsup=$minsup")
    println(s"  Ranker = $client")
    println(s"Features = ${params.featureMap}")
    println(s"  Params = iterations=${params.iterations}, k=${params.querySize}, l=${params.queryRetention}")
    println(s"featureCount = $d")
  }

  protected final def logNewIteration(iteration: Int): Unit =
    logger.debug(s"Iteration $iteration")

  protected final def logIterationOutcome(iteration: Int, query: Array[Itemset],
                                          learnedWeight: LogisticWeight, client: Ranker): Unit ={
    val GetWeight : Itemset => Double={_.getMetadata[Double]("wg4ps.weight").get}
    query.view.zipWithIndex.foreach { case (p, i) =>
      //logger.info(f"$iteration%d;$i%d;${p.size}%d;${client.describe(p)}%s;${wg4ps.GetWeight(p)}%.6f;${learnedWeight(p)}%.6f;${p.sortedItems.mkString("+")}")
      logger.info(f"$iteration%d;$i%d;${p.size}%d;${client.describe(p)}%s;${GetWeight(p)}%.6f;${learnedWeight(p)}%.6f;${p.sortedItems.mkString("+")}")
    }
    // learned weights after this iteration, used to evaluate the learned model offline
    println(f"ITER_WEIGHTS;$iteration%d;" + (0 until learnedWeight.featureMap.featureCount).map(j => f"${learnedWeight.weights(j)}%.8f").mkString(";"))
  }
  protected final def getIterationOutcome(iteration: Int, query: Array[Itemset],
                                          learnedWeight: LogisticWeight): String = {
    val GetWeight : Itemset => Double={_.getMetadata[Double]("wg4ps.weight").get}
    var results: String = ""
    query.view.zipWithIndex.foreach { case (p, i) =>
      //logger.info(f"$iteration%d;$i%d;${p.size}%d;${GetWeight(p)}%.6f;${learnedWeight(p)}%.6f;${p.sortedItems.mkString("+")}")
      results = results + f"$iteration%d;$i%d;${p.size}%d;${GetWeight(p)}%.6f;${learnedWeight(p)}%.6f;${p.sortedItems.mkString("+")}\n"
    }
    //println("results:"+results)
    results
  }
  
  protected final def logAnaData(iteration: Int, query: Array[Itemset],
                                          learnedWeight: LogisticWeight, client: Ranker): Unit = {
    var anaData = ""
    var phi_val = Array.ofDim[Double](query.size) // quality measure
    var pct_rank = Array.ofDim[Double](query.size) // learned weight
    var patterns_size = Array.ofDim[Int](query.size) // patterns size
    var patterns_support = Array.ofDim[Int](query.size) // patterns support
    
    query.view.zipWithIndex.foreach { case (p, i) => {
        patterns_support(i) = p.size // #transactions containing the itemset
        patterns_size(i) = p.items.size
        
        anaData = f"${client.describe(p)}%s"
        phi_val(i) = anaData.replace(',', '.').toDouble
        
        //anaData = f"${learnedWeight(p)}%.6f"
        anaData = f"${learnedWeight(p)}"
        pct_rank(i) = anaData.replace(',', '.').toDouble
      }
    }
    
    anaData = f"\n##$iteration%d-"
    
    for(i <- 0 to (query.size-1)){
      if (i != (query.size-1))
        anaData += f"${patterns_support(i)};"
      else
        anaData += f"${patterns_support(i)}-"
    }
    
    for(i <- 0 to (query.size-1)){
      if (i != (query.size-1))
        anaData += f"${patterns_size(i)};"
      else
        anaData += f"${patterns_size(i)}-"
    }
    
    for(i <- 0 to (query.size-1)){
      if (i != (query.size-1))
        anaData += f"${phi_val(i)};"
      else
        anaData += f"${phi_val(i)}-"
    }
    
    var avg_rank = 0.0
    var max_rank = -1.0
    for(i <- 0 to (query.size-1)){
      avg_rank += (pct_rank(i) / query.size)
      if(pct_rank(i) > max_rank)
        max_rank = pct_rank(i)
        
      if (i != (query.size-1))
        anaData += f"${pct_rank(i)};"
      else
        anaData += f"${pct_rank(i)}"
    }
    
    var avg_regret = 1-avg_rank
    var max_regret = 1-max_rank
    println(f"$anaData\n\n")
  }
  
  protected final def getAnaData(iteration: Int, query: Array[Itemset], learnedWeight: LogisticWeight): String = {
    var anaData = ""
    var pct_rank = Array.ofDim[Double](query.size) // learned weight
    var patterns_size = Array.ofDim[Int](query.size) // patterns size
    var patterns_support = Array.ofDim[Int](query.size) // patterns support
    
    query.view.zipWithIndex.foreach { case (p, i) => {
      patterns_size(i) = p.size
      patterns_support(i) = p.items.size
      
      anaData = f"${learnedWeight(p)}"
      pct_rank(i) = anaData.replace(',', '.').toDouble
    }}
    
    anaData = f"##$iteration%d-"
    
    for(i <- 0 to (query.size-1)){
      if (i != (query.size-1))
        anaData += f"${patterns_size(i)};"
      else
        anaData += f"${patterns_size(i)}-"
    }
    
    for(i <- 0 to (query.size-1)){
      if (i != (query.size-1))
        anaData += f"${patterns_support(i)};"
      else
        anaData += f"${patterns_support(i)}-"
    }
    
    var avg_rank = 0.0
    var max_rank = -1.0
    for(i <- 0 to (query.size-1)){
      avg_rank += (pct_rank(i) / query.size)
      if(pct_rank(i) > max_rank)
        max_rank = pct_rank(i)
        
      if (i != (query.size-1))
        anaData += f"${pct_rank(i)};"
      else
        anaData += f"${pct_rank(i)}"
    }
    
    var avg_regret = 1-avg_rank
    var max_regret = 1-max_rank
    
    anaData
  }
  
  protected final def logTermination(iteration: Int, learnedWeight: LogisticWeight): Unit = {
    logger.info(s"Finished after $iteration iterations, returning $learnedWeight")
    logger.trace("\tFeature weights:")
    logger.trace(learnedWeight.featureMap.featureLabels.mkString(";"))
    logger.trace((0 until learnedWeight.featureMap.featureCount).map(j => f"${learnedWeight.weights(j)}%.6f").mkString(";"))
    // final learned weights, used to evaluate the learned model offline
    println("FINAL_WEIGHTS;" + (0 until learnedWeight.featureMap.featureCount).map(j => f"${learnedWeight.weights(j)}%.8f").mkString(";"))
    logger.info("###########################################\n")
    //logger.info("###########################################\n\n")
  }
}
