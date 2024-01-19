/*
 * This file is part of the DiSPaLe project (https://gitlab.com/phdhien/dispale)
 *
 * Copyright (c) 2022, Normandie Université, France
 *
 * Licensed under the MIT license.
 *
 * See LICENSE file in the project root for full license information.
 */
package ca.pfv.spmf.algorithms.frequentpatterns;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/**
 * A generic class of the implemented algorithms (TKO, TKUCE).
 * 
 * @author Maxime Garfagni
 * @author Arnold Hien.
 */
public class AlgoHUI {

	/**
	* Run the algorithm
	* @param input: the input file path
	* @param k: the parameter k
	* @param weighs: array containing the utilities weights to be used
	* @param nb_items: the number of items of the dataset file
	* @param transUsed: check if transactions utilities are used or not
	 * @throws IOException
	*/
	public List<Set<Integer>> runAlgorithm(String input, int k, double[] weights, int nb_items, boolean transUsed) throws IOException{
		return null;
	}
	
    /*
     * Get the cover of all itemsets
     */
	public ArrayList<Set<Integer>> get_all_itemsets_cover() {
		return null;
	}
	
    /*
     * Get the cover of one itemset
     */
	public Set<Integer> get_one_itemset_cover(int i) {
		return null;
	}

}

