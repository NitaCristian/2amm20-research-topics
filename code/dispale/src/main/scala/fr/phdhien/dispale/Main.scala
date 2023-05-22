package fr.phdhien.dispale

import fr.phdhien.dispale.feature.{Features, Items, Patterns, Transactions, Frequency, Length, Intercept}
import be.kuleuven.pmlib.itemsets.{CP4IMData, CP4IMParameters}
import be.kuleuven.scd.{FixedIterations, ScdParameters}

object Main extends App {
  
    
  //****************************************************************************************************************
  // ************************************************ LAUNCH RUNNING ***********************************************
  //****************************************************************************************************************
  
  /**/
  val launcher: MethodSelector = MethodSelector()
  launcher.init(args)
  launcher.simulation()
  
  //----------------------------------------------------------------------------------------------------------------
  //----------------------------------------------------------------------------------------------------------------
  
  println("\n###############################################")
  println("##################### END #####################")
  println("###############################################\n")
  
}

