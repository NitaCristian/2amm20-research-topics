package fr.phdhien.dispale

import be.kuleuven.flexics._
//import be.kuleuven.flexics.eflexics.EclatProblem
import be.kuleuven.flexics.GetWeight
import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.Itemset
import be.kuleuven.pmlib.patterns.{Mask, MaskBuilder}
import be.kuleuven.scd._
import be.kuleuven.weightgen._
import be.kuleuven.weightgen.{Uniform, WeightFunction, WeightGenConfiguration, WeightMcConfiguration}
import be.kuleuven.wg4ps.specialised.EclatProblem

import com.typesafe.scalalogging.Logger

import fr.phdhien.dispale.feature.{Features, FeatureMap, FeatureMapGenerator, Patterns}

import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.util.Random

// LetSIP Original ++ LogisticWeight as weight function
// Une fonction d'apprentissage est alors apprise à partir
// de ce LogisticWeight à chaque itération

object LetSIP {
  def apply(dataset: Dataset[Set[Int]],
            datasetPath: String,
            minsup: Int,
            jmax: Double,
            oracle: String,
            params: Parameters,
            listFeatures: Array[Features],
            user: Ranker = FrequencyRanker,
            seed: Long = System.nanoTime().hashCode()): LetSIP = {
    new LetSIP(dataset, datasetPath, minsup, jmax, oracle, params, listFeatures, user, seed)
  }
}

class LetSIP(
            val dataset: Dataset[Set[Int]],
            val datasetPath: String,
            val minsup: Int,
            val jmax: Double,
            val oracle: String,
            val params: Parameters,
            val listFeatures: Array[Features],
            val user: Ranker = FrequencyRanker,
            val seed: Long = System.nanoTime().hashCode()) extends LearningLogging {
  //************************************************************************************************************
  val rnd = new Random(seed)
  var currentIteration: Int = 0
  //************************************************************************************************************
  val querySize = params.querySize
  val sampler = get_sampler(oracle)
  var featureMap = get_featureMap(dataset, params)
  //************************************************************************************************************
  val loss = LogisticLoss
  val k = params.querySize
  var d = featureMap.featureCount
  var all_w = ArrayVector.zeros(d)
  var z = ArrayVector.zeros(params.iterations * params.pairsPerQuery)
  var learnedWeight: LogisticWeight = LogisticWeight(params.a, all_w, featureMap)
  var current_state: ScdState = HotStart(all_w, z)
  //************************************************************************************************************
  var currentQuery: Array[Itemset] = null
  val queries = Array.ofDim[Itemset](params.iterations, k)
  val miningProblem = get_problem(oracle, dataset, datasetPath, k, minsup, jmax)
  val trainingPairs = Array.ofDim[RankedPair](params.iterations * params.pairsPerQuery)
  //************************************************************************************************************
  val countingConf = WeightMcConfiguration.default
  val samplingConf = WeightGenConfiguration(kappa = 0.5)
  protected override val logger = Logger(LoggerFactory.getLogger("OriginalLetSIP"))
  
  //############################################################################################################
  //############################################################################################################
  
  def get_featureMap(dataset: Dataset[Set[Int]], params: Parameters) = {
    params.featureMap match {
      case generator: FeatureMapGenerator => generator.toFeatureMap(dataset)
      case map: FeatureMap => map
    }
  }
  
  //############################################################################################################
  //############################################################################################################
  
  def get_problem(oracle: String, dataset: Dataset[Set[Int]], datasetPath: String,
                                k: Int = 5, minsup: Int = 10, jmax: Double = 0.05) = {
    oracle.toLowerCase match {
      case "eflexics" => EclatProblem(dataset, minsup)
      case _ => EclatProblem(dataset, minsup)
    }
  }
  
  //############################################################################################################
  //############################################################################################################
  
  def get_sampler(oracle: String) = {
    oracle.toLowerCase match {
      case "eflexics" => EFlexicsWrapper(VanillaEFlexics)
      case _ => EFlexicsWrapper(VanillaEFlexics)
    }
  }
  
  //############################################################################################################
  // ********************** FUNCTION THAT PREPARES THE PATTERNS FOR PAIRWISING **********************************
  def rankedPairs(itemsets: Array[Itemset], k: Int): Iterator[(Itemset, Itemset)] = {
    for (i <- Iterator.range(0, k - 1);
         j <- Iterator.range(i + 1, k))
      yield (itemsets(i), itemsets(j))
  }
  
  //############################################################################################################
  //############################################################################################################
  
  def sampleQuery(weight: WeightFunction[Itemset]): Unit = {

    val overlap = if (currentIteration > 0) params.queryOverlap else 0
    if (currentIteration > 0) {
      queries(currentIteration - 1).copyToArray(queries(currentIteration), 0, overlap)
      //queries(currentIteration).view(0, overlap).foreach { p => wg4ps.storeWeightInPlace(p, weight(p)) }
      
      queries(currentIteration).view(0, overlap).foreach { p => storeWeightInPlace(p, weight(p)) }
    }

    sample(weight).take(params.querySize - overlap).zipWithIndex
      .foreach { case (itemset, j) => queries(currentIteration)(overlap + j) = itemset }
    scala.util.Sorting.quickSort(queries(currentIteration))(ByWeight)
  }
  
  //############################################################################################################
  //############################################################################################################
  
  def sample(weight: WeightFunction[Itemset]) = {
    //sampler.countThenSample(problem, weight, samplingConf, Left(countingConf), solutionWeight = Some(wg4ps.GetWeight))(rnd)
    sampler.countThenSample(miningProblem, weight, samplingConf, Left(countingConf), solutionWeight = Some(GetWeight))(rnd)
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
  
  //############################################################################################################
  //############################################################################################################
  
  def showNewExamplesToSCD(state: ScdState, z: MutableVector, iteration: Int, m: Int) = {
    val (state_w, _) = get_w_z_from_state(state, m, d)
    NewExamplesAppended(state_w, z, iteration * params.pairsPerQuery)
  }
  
  //############################################################################################################
  //############################################################################################################
  
  def updateWeights() = {
                      //state: ScdState, z: MutableVector, iteration: Int, d: Int, rnd: Random) = {
    //
    val m = (currentIteration + 1) * params.pairsPerQuery
    val trainingExamples = trainingPairs.view(0, m)
    //val m = params.pairsPerQuery
    //val trainingExamples = trainingPairs.view(currentIteration*m, (currentIteration+1)*m)

    SCD.optimize(
              loss, trainingExamples, currentIteration, params.scd.copy(lambda = params.scd.lambda * (currentIteration + 1)),
              state = if (currentIteration > 0) showNewExamplesToSCD(current_state, z, currentIteration, m) else current_state, m = Some(m), d = Some(d)
              )(rnd)
  }
  
  //############################################################################################################
  //############################################################################################################
  
  // ********************** FUNCTION USED FOR SIMULATIONS *******************************************************
  def loop(): LogisticWeight = { 

    @tailrec
    // def doLoop(iteration: Int, weight: WeightFunction[Itemset], state: HotStart): LogisticWeight = {
    def doLoop(weight: WeightFunction[Itemset]): LogisticWeight = {
      currentIteration match {
        case params.iterations =>
          val glf = weight.asInstanceOf[LogisticWeight]
          logTermination(currentIteration, glf)

          glf
        case _ =>
          // *************** SAMPLE A SET OF K PATTERNS *************************************
          var msg = s"Sampling $k itemsets to query"
          logger.debug(s" ${msg.replace("ing", "ed")}")
          
          run_sampling()
          
          // *************** RANK PATTERNS BY DESCENDING ORDER OF THEIR WEIGHT **************
          msg = s"Ranking $k itemsets"
          logger.debug(s" ${msg.replace("ing", "ed")}")
          
          user.rank(currentQuery)
          
          // *************** PAIRS OF PATTERNS TO BE USED FOR THE LEARNING ******************
          msg = s"Generating ${params.pairsPerQuery} example pairs from $k itemsets"
          logger.debug(s" ${msg.replace("ing", "ed")}")
          // *************** UPDATE FEATURES ELEMENTS' WEIGHT *******************************
          msg = s"Updating $d weights"
          logger.debug(s" ${msg.replace("ing", "ed")}")
          // ********************************************************************************
          
          val iter_results = run_learning()
          
          // *************** PRINT LOGS *****************************************************
          logAnaData(currentIteration, currentQuery, learnedWeight, user)
          logIterationOutcome(currentIteration, currentQuery, learnedWeight, user)
          // *************** NEXT ITERATIONS ************************************************
          
          //currentIteration += 1
          doLoop(learnedWeight)
          //doLoop(currentIteration + 1, learnedWeight, newState)
      }
    }

    // doLoop(0, Uniform, HotStart(all_w, z))
    doLoop(Uniform)
  }
  
  //############################################################################################################
  //############################################################################################################
  
  
  def run_sampling(): Unit = {
    if(currentIteration == 0) {
      sampleQuery(Uniform)
    }
    else {
      sampleQuery(learnedWeight)
    }
    currentQuery = queries(currentIteration)
    //currentQuery
  }
  
  //############################################################################################################
  //############################################################################################################
  
  def run_learning(): String = {
  // def run_learning(): (String, ScdState) = {
    // ********************** FORM PAIRS OF PATTERNS TO BE USED FOR THE LEARNING **************************
    rankedPairs(currentQuery, k).zipWithIndex.foreach { case ((preferred, dispreferred), j) =>
      trainingPairs(currentIteration * params.pairsPerQuery + j) = new RankedPair(preferred, dispreferred, featureMap)
    }
    
    // ****************************************************************************************************
    // ********************** UPDATE FEATURES ELEMENTS' WEIGHT WRT. THE STRATEGY CHOOSEN (features-update)
    val (_, newState) = updateWeights()
    
    // ********************** UPDATE THE LEARNED FUNCTION *************************************************
    learnedWeight = LogisticWeight(params.a, all_w, featureMap)
    
    val iter_anaDAta = getAnaData(currentIteration, currentQuery, learnedWeight)
    val iter_results = getIterationOutcome(currentIteration, currentQuery, learnedWeight)
    
    currentIteration = currentIteration+1
    
    iter_results
    // (iter_results, newState)
    // ********************** LEARNING FINISHED ***********************************************************
  }
  
  //############################################################################################################
  //############################################################################################################
  
}

