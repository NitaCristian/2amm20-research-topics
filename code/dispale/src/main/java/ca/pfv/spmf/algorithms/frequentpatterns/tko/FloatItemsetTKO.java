/*
 * This file is part of the DiSPaLe project (https://gitlab.com/phdhien/dispale)
 *
 * Copyright (c) 2023, Normandie Université and IMT Atlantique, France
 *
 * Licensed under the MIT license.
 *
 * See LICENSE file in the project root for full license information.
 */
package ca.pfv.spmf.algorithms.frequentpatterns.tko;

import java.util.List;
import ca.pfv.spmf.algorithms.frequentpatterns.hui_miner_float.Element;

/**
 * A generic class of the implemented algorithms (TKO, TKUCE).
 * 
 * @author Maxime Garfagni
 * @author Arnold Hien.
 */
public class FloatItemsetTKO implements Comparable<FloatItemsetTKO>{
	int[] itemset; 
	int item;
	double utility; // absolute support
	List<Element> cover;
	
	public int[] getItemset() {
		return itemset;
	}

	public int getItem() {
		return item;
	}

		
	public FloatItemsetTKO(int[] itemset, int item, double utility, List<Element> cover){
		this.itemset = itemset;
		this.item = item;
		this.utility = utility;
		this.cover=cover;
	}

	public int compareTo(FloatItemsetTKO o) {
		if(o == this){
			return 0;
		}
		double compare =  this.utility - o.utility;
		if(compare > 0){
			return 1;
		}
		if(compare < 0){
			return -1;
		}
		return 0;
	}

	public String toString() {
		StringBuffer temp = new StringBuffer();
		for(int item : itemset){
			temp.append(item + ",");
		}
		temp.append(item);
		return temp.toString();
	}
	
	public int[] getWholeItemset() {
		int len=this.itemset.length;
		int[] wholeItemset= new int[len+1];
		for(int i=0;i<len;i++){
			wholeItemset[i]=this.itemset[i];
		}
		wholeItemset[len]=this.item;
		return wholeItemset;
	}
}
