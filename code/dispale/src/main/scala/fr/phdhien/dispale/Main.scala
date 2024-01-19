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

import fr.phdhien.dispale.feature.{Features, Items, Patterns, Transactions, Frequency, Length, Intercept}
import be.kuleuven.pmlib.itemsets.{CP4IMData, CP4IMParameters}
import be.kuleuven.scd.{FixedIterations, ScdParameters}

/* 
 * @author Arnold Hien
 */
object Main extends App {
    
    //***************************************************************************************************************
    // *********************************************** LAUNCH RUNNING ***********************************************
    //***************************************************************************************************************
    
    val launcher: MethodSelector = MethodSelector()
    launcher.init(args)
    launcher.simulation()
    
    println("\n###############################################")
    println("##################### END #####################")
    println("###############################################\n")
    
    //----------------------------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------------------------
  
}

