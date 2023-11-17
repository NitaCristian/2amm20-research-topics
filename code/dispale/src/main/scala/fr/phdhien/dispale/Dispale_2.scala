package fr.phdhien.dispale

import be.kuleuven.flexics._
import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.scd._

import com.typesafe.scalalogging.Logger

import fr.phdhien.dispale.feature.{Features}

import org.slf4j.LoggerFactory

import scala.annotation.tailrec


// Ici l'apprentisage sur les transactions est différente : 
// on considère les transactions communes aux motifs contenant le discriminant

object Dispale_2 {
  def apply(
            dataset: Dataset[Set[Int]], datasetPath: String, datasetPathFimi:String,directory:String,
            run_id: Int, minsup: Int, jmax: Double, oracle: String, 
            params: Parameters, aggregation_function: String, 
            listFeatures: Array[Features], listFeatures_count: Array[Int], 
            listFeatures_str: Array[String], user: Ranker = FrequencyRanker, 
            seed: Long = System.nanoTime().hashCode()): Dispale_2 = {
    new Dispale_2(dataset, datasetPath,datasetPathFimi, directory, run_id, minsup, jmax, oracle, params, aggregation_function, 
                                    listFeatures, listFeatures_count, listFeatures_str, user, seed)
  }
}

class Dispale_2(
            override val dataset: Dataset[Set[Int]], override val datasetPath: String, override val datasetPathFimi: String, override val directory:String,
            override val run_id: Int, override val minsup: Int, override val jmax: Double, override val oracle: String, 
            override val params: Parameters, override val aggregation_function: String, 
            override val listFeatures: Array[Features], override val listFeatures_count: Array[Int], 
            override val listFeatures_str: Array[String], override val user: Ranker = FrequencyRanker, 
            override val seed: Long = System.nanoTime().hashCode()) extends 
            Dispale(dataset, datasetPath,datasetPathFimi, directory, run_id, minsup, jmax, oracle, params, aggregation_function, 
                                    listFeatures, listFeatures_count, listFeatures_str, user, seed) {
  
  protected override val logger = Logger(LoggerFactory.getLogger("Dispale-2"))
  
  override def update_weight_list(code: Int, discriminating_weights: Array[Double], nbToAddOrRemove: Int) = {
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
          
          // ********************** Look for common transactions in the covers of ***********************
          // ********************** patterns containing the discriminating pattern **********************
          var l = 0
          var cov = Array.fill(dataset.size)(true)
          while(l < dataset.size){
            var l2 = 0
            while(l2 < params.querySize){
              if (best_icv_itemset.items.subsetOf(currentQuery(l2).items)){
                cov(l) = currentQuery(l2).mask.isCovering(l) && cov(l)
              }
              l2 = l2+1
            }
            
            l = l+1
          }
          
          // ********************** NOW AGGREGATE *******************************************************
          (deb until fin).foreach{ i =>
            if(cov(i-deb)){
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
  
}

