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

import scala.collection.immutable.Set

import be.kuleuven.flexics.GetWeight
import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.itemsets.Itemset
import be.kuleuven.pmlib.patterns.{Mask, MaskBuilder}

/* 
 * @author Arnold Hien
 */

//############################################################################################################
//########################################## STATIC METHODS PACKAGE ##########################################
//############################################################################################################

package object mining{ 

    val GetWeight : Itemset => Double={_.getMetadata[Double]("wg4ps.weight").get}
    
    val ByWeight = Ordering.by(GetWeight).reverse
    val ByFreq = Ordering.by { itemset: Itemset => itemset.size }.reverse
    
    def storeWeightInPlace( itemset:Itemset, w:Double ):Itemset = {
        itemset.storeMetadata("wg4ps.weight", w)
        itemset
    }

    /*********************************************************/
    /*** FUNCTIONS USED FOR CONSTRUCTING AN OBJECT ITEMSET ***/
    /*********************************************************/
    
    // convert a pattern from a Array of Int object to a Set of Int object
    def arrayToSet( items: Array[String] ): Set[Int] = {
        var solution = scala.collection.mutable.Set[Int]()
        items.foreach {
            i => solution.add(i.toInt)
        }
        solution.toSet
    }

    // convert a pattern from a java Set of Integer object to a scala Set of Int object
    def itemsToSet( itemset: java.util.Set[Integer] ): Set[Int] = {
        var solution = scala.collection.mutable.Set[Int]()
        val it = itemset.iterator()
        while( it.hasNext() ) {
            val Next = it.next()
            solution.add(Next)
        }
        solution.toSet
    }
    
    // convert the cover of a pattern from an Array of String object to a MaskBuilder object
    def arrayToMask( dataset: Dataset[Set[Int]], cover: Array[String] ): Option[Mask] = {
        val builder = new MaskBuilder(datasetSize = dataset.size)
        cover.foreach{
            i => builder.set(i.toInt)
        }
        Some(builder.build())
    }
    
    // convert the cover of a pattern from a Set of Integer object to a MaskBuilder object
    def coverToSet( dataset: Dataset[Set[Int]], mycover: java.util.Set[Integer] ): Option[Mask] = {
        val builder = new MaskBuilder(datasetSize=dataset.size)
        val it = mycover.iterator();
        while(it.hasNext()){
            builder.set(it.next())
        }
        Some(builder.build())
    }

}

