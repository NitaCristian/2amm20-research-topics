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

import scala.sys.process._
import scala.annotation.tailrec
import scala.util.Random
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConverters._
import java.util.Set

import ca.pfv.spmf.algorithms.frequentpatterns.tko._
import ca.pfv.spmf.algorithms.frequentpatterns.tkuce._
import java.io._
import scala.io.Source

// LetSIP Original ++ LogisticWeight as weight function
// Une fonction d'apprentissage est alors apprise à partir
// de ce LogisticWeight à chaque itération

object LetSIP {
  def apply(dataset: Dataset[scala.collection.immutable.Set[Int]],
            datasetPath: String,
            directory:String,
            run_id: Int,
            minsup: Int,
            jmax: Double,
            oracle: String,
            params: Parameters,
            listFeatures: Array[Features],
            user: Ranker = FrequencyRanker,
            seed: Long = System.nanoTime().hashCode()): LetSIP = {
    new LetSIP(dataset, datasetPath, directory, run_id, minsup, jmax, oracle, params, listFeatures, user, seed)
  }
}

class LetSIP(
            val dataset: Dataset[scala.collection.immutable.Set[Int]],
            val datasetPath: String,
            val directory:String,
            val run_id: Int,
            val minsup: Int,
            val jmax: Double,
            val oracle: String,
            val params: Parameters,
            val listFeatures: Array[Features],
            val user: Ranker = FrequencyRanker,
            val seed: Long = System.nanoTime().hashCode()) extends LearningLogging {
  println("USER:"+user)        
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
  var all_w = ArrayVector.ones(d)
  //println("SIZE ALL_W: "+all_w.length)
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
  
  def get_featureMap(dataset: Dataset[scala.collection.immutable.Set[Int]], params: Parameters) = {
    params.featureMap match {
      case generator: FeatureMapGenerator => generator.toFeatureMap(dataset)
      case map: FeatureMap => map
    }
  }
  
  //############################################################################################################
  //############################################################################################################
  
  def get_problem(oracle: String, dataset: Dataset[scala.collection.immutable.Set[Int]], datasetPath: String,
                                k: Int = 5, minsup: Int = 10, jmax: Double = 0.05) = {
                                
    oracle.toLowerCase match {
      case "eflexics" => EclatProblem(dataset, minsup)
      case _ => EclatProblem(dataset, minsup)
    }
  }
  
  //############################################################################################################
  //############################################################################################################
  
  def get_list_from_oracle(oracle: String,weight: WeightFunction[Itemset],overlap:Int) : Iterator[Itemset]= {
  
    //Selects the oracle
    oracle.toLowerCase match {
      case "eflexics" => sample(weight)
      case "tko"=> {
    val input : String = datasetPath
    val weights: Array[Double]=all_w.array
    var listTopK = new ListBuffer[Itemset]()
    var i=0
    //println("NOMBRE TRANSACTIONS:"+dataset.size)
    
    //case features=Items
    if (d==dataset.attributes.size) {
    	val algoTKO=new AlgoTKO_Dispale_I() 
    	//extracts a top k patterns
    	val topK = algoTKO.runAlgorithm(input,k,weights)
    	
    	//for each sampled pattern, make a new object Itemset 
    	for(i<-1 to k){
    	   val oneTopK=itemsToSet(topK.get(k-i))
    	   val itsCover=coverToSet(dataset,algoTKO.getCovers().get(k-i))
	   val TopItemset: Itemset =Itemset(oneTopK,dataset,itsCover)
	   
	   //checking if the itemset is not already in the query retention
	   var check:Boolean = true
	   val j=0
	   for (j<-1 to overlap){
	      check=check & (TopItemset.items != queries(currentIteration)(j-1).items)
	   }
	   if (check){
	      storeWeightInPlace(TopItemset,weight(TopItemset))
	      //println(TopItemset)
	      listTopK+=TopItemset
	   }
    	} 
    }
    
    //case features=Items+Transactions
    else {
    	val algoTKO=new AlgoTKO_Dispale_IT() 
    	//extracts a top k patterns
    	val topK=algoTKO.runAlgorithm(input,k,weights,dataset.attributes.size)
    	
    	//for each sampled pattern, make a new object Itemset 
    	for(i<-1 to k){
    	   val oneTopK=itemsToSet(topK.get(k-i))
    	   val itsCover=coverToSet(dataset,algoTKO.getCovers().get(k-i))
	   val TopItemset: Itemset =Itemset(oneTopK,dataset,itsCover)
	   
	   //checking if the itemset is not already in the query retention
	   var check:Boolean = true
	   val j=0
	   for (j<-1 to overlap){
	      check=check & (TopItemset.items != queries(currentIteration)(j-1).items)
	   }
	   if (check){
	      storeWeightInPlace(TopItemset,weight(TopItemset))
	      //println(TopItemset)
	      listTopK+=TopItemset
	   }
	}
    }
    
    //returning the list of candidates as an iterator
    val it=listTopK.iterator
    it
    }
    
    
       case "tkuce"=> {
    val input : String = datasetPath
    val weights: Array[Double]=all_w.array
    var listTopK = new ListBuffer[Itemset]()
    var i=0
    //println("NOMBRE TRANSACTIONS:"+dataset.size)
    
    //case features=Items
    if (d==dataset.attributes.size) {
	val algoTKUCE=new AlgoTKUCEP_Dispale_I() 
	val topK = algoTKUCE.runAlgorithm(input,k,weights)
	
	//for each sampled pattern, make a new object Itemset 
	for(i<-1 to k){
	   val oneTopK=itemsToSet(topK.get(i-1))
	   val itsCover=coverToSet(dataset,algoTKUCE.getCovers().get(i-1))
	   val TopItemset: Itemset =Itemset(oneTopK,dataset,itsCover)
	   
	   //checking if the itemset is not already in the query retention
	   var check:Boolean = true
	   val j=0
	   for (j<-1 to overlap){
	      check=check & (TopItemset.items != queries(currentIteration)(j-1).items)
	   }
	   if (check){
	      storeWeightInPlace(TopItemset,weight(TopItemset))
	      //println(TopItemset)
	      listTopK+=TopItemset
	   }
	}
    }
    else {
    	val algoTKUCE=new AlgoTKUCEP_Dispale_IT() 
    	val topK = algoTKUCE.runAlgorithm(input,k,weights,dataset.attributes.size)
    	
    	//for each sampled pattern, make a new object Itemset 
	for(i<-1 to k){
	   val oneTopK=itemsToSet(topK.get(i-1))
	   val itsCover=coverToSet(dataset,algoTKUCE.getCovers().get(i-1))
	   val TopItemset: Itemset =Itemset(oneTopK,dataset,itsCover)
	   
	   //checking if the itemset is not already in the query retention
	   var check:Boolean = true
	   val j=0
	   for (j<-1 to overlap){
	      check=check & (TopItemset.items != queries(currentIteration)(j-1).items)
	   }
	   if (check){
	      storeWeightInPlace(TopItemset,weight(TopItemset))
	      //println(TopItemset)
	      listTopK+=TopItemset
	   }
	}
    }
    //returning the list of candidates as an iterator
    val it=listTopK.iterator
    it
    }
    
    
       case "huisampler"=> {
    val input : String = datasetPath
    val weights: Array[Double]=all_w.array
    var listTopK = new ListBuffer[Itemset]()
    var i=0
    
    //M: maximum length of a sampled pattern
    val M=500
    //println("NOMBRE TRANSACTIONS:"+dataset.size)
    // writting input parameters in a txt file
    var param=d+"\n"+input+"\n"+k+"\n"+M+"\n"
    var j=0
    for (j<-1 to all_w.length){
        param=param+all_w(j-1)+" "
    }
    println(d,all_w.length)
    val file=new File(directory+"/parameters-" + run_id.toString +".txt")
    val bw=new BufferedWriter(new FileWriter(file))
    bw.write(param+"\n")
    bw.close()
    val path=new java.io.File("code/dispale/src/main/python/haisampler-src-main/HAISampler.py").getCanonicalPath
    
    //System call for launching HAISampler
    val cmd = "python3 "+path+ " -dir "+directory + " -run " + run_id.toString
    cmd.!!
    val filename=directory+"/topK-" + run_id.toString +".txt"
    
    //reading sampled patterns
    for (line <- Source.fromFile(filename).getLines){
        val itemsetAndCover=line.split(":")
        val itemsetString=itemsetAndCover(0).split(" ")
        val coverString=itemsetAndCover(1).split(" ")
        //make a new object Itemset 
        val oneTopK=arrayToSet(itemsetString)
        val itsCover=arrayToMask(dataset,coverString)
        val TopItemset: Itemset =Itemset(oneTopK,dataset,itsCover)
        
        //checking if the itemset is not already in the query retention
        var check:Boolean = true
        val j=0
        for (j<-1 to overlap){
           check=check & (TopItemset.items != queries(currentIteration)(j-1).items)
        }
        if (check){
           storeWeightInPlace(TopItemset,weight(TopItemset))
           //println(TopItemset)
           listTopK+=TopItemset
        }
     }
     //returning the list of candidates as an iterator
     val it=listTopK.iterator
     it
     }   
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
  // ********************** FUNCTIONS USED FOR CONSTRUCTING AN OBJECT ITEMSET **********************************
  
  def arrayToSet(items:Array[String]):scala.collection.immutable.Set[Int]={
       var solution=scala.collection.mutable.Set[Int]()
       items.foreach{i => solution.add(i.toInt)}
       solution.toSet
       }
  
  def arrayToMask(dataset:Dataset[scala.collection.immutable.Set[Int]],cover:Array[String]):Option[Mask]={
        val builder=new MaskBuilder(datasetSize=dataset.size)
        cover.foreach{i => builder.set(i.toInt)}
        Some(builder.build())
        }
        

  def itemsToSet(myset:java.util.Set[Integer]):scala.collection.immutable.Set[Int]={
  	val sol=myset
	var solution=scala.collection.mutable.Set[Int]()
	var i=0
	val it=sol.iterator()
	while(it.hasNext()){
	val Next=it.next()
	solution.add(Next)}
	solution.toSet
	}

  def coverToSet(dataset:Dataset[scala.collection.immutable.Set[Int]], mycover:java.util.Set[Integer]):Option[Mask]={
  	val builder=new MaskBuilder(datasetSize=dataset.size)
  	val it=mycover.iterator();
  	while(it.hasNext()){builder.set(it.next())}
  	Some(builder.build())
  	}
	
  val GetWeight : Itemset => Double={_.getMetadata[Double]("wg4ps.weight").get}
  
  def storeWeightInPlace(itemset:Itemset,w:Double):Itemset ={
  	itemset.storeMetadata("wg4ps.weight",w)
  	itemset
  	}
  
  def sampleQuery(weight: WeightFunction[Itemset]): Unit = {
    //number of patterns retained from previous iteration
    val overlap = if (currentIteration > 0) params.queryOverlap else 0
    if (currentIteration > 0) {
      //copy retained patterns to new query
      queries(currentIteration - 1).copyToArray(queries(currentIteration), 0, overlap)
      
      //queries(currentIteration).view(0, overlap).foreach { p => wg4ps.storeWeightInPlace(p, weight(p)) }
      } 
    queries(currentIteration).view(0, overlap).foreach { p => storeWeightInPlace(p, weight(p)) }
    
    //extract missing patterns with the oracle
    get_list_from_oracle(oracle,weight,overlap).take(params.querySize - overlap).zipWithIndex
      .foreach { case (itemset, j) => queries(currentIteration)(overlap + j) = itemset}
    /*sample(weight).take(params.querySize - overlap).zipWithIndex
      .foreach { case (itemset, j) => queries(currentIteration)(overlap + j) = itemset}*/
    scala.util.Sorting.quickSort(queries(currentIteration))(ByWeight)
    //println("WILL KEEP: "+ user.describe(queries(currentIteration)(0)))
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
          //println("ETAPE1")
          //var i=0
          //for(i<-1 to all_w.length){
          //	println(i-1+": "+all_w(i-1))
          //	}
          var msg = s"Sampling $k itemsets to query"
          logger.debug(s" ${msg.replace("ing", "ed")}")
          
          run_sampling()
          // *************** RANK PATTERNS BY DESCENDING ORDER OF THEIR WEIGHT **************
          //println("ETAPE2")
          msg = s"Ranking $k itemsets"
          logger.debug(s" ${msg.replace("ing", "ed")}")
          
          user.rank(currentQuery)
          
          // *************** PAIRS OF PATTERNS TO BE USED FOR THE LEARNING ******************
          //println("ETAPE3")
          msg = s"Generating ${params.pairsPerQuery} example pairs from $k itemsets"
          logger.debug(s" ${msg.replace("ing", "ed")}")
          // *************** UPDATE FEATURES ELEMENTS' WEIGHT *******************************
          //println("ETAPE4")
          msg = s"Updating $d weights"
          logger.debug(s" ${msg.replace("ing", "ed")}")
          // ********************************************************************************
          //println("ETAPE5")
          val iter_results = run_learning()
          
          // *************** PRINT LOGS *****************************************************
          //println("ETAPE6")
          logAnaData(currentIteration, currentQuery, learnedWeight, user)
          logIterationOutcome(currentIteration, currentQuery, learnedWeight, user)
          // *************** NEXT ITERATIONS ************************************************
          //println("ETAPE7")
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

