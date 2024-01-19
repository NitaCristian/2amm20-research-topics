/*
 * This file is part of the DiSPaLe project (https://gitlab.com/phdhien/dispale)
 *
 * Copyright (c) 2022, Normandie Université, France
 *
 * Licensed under the MIT license.
 *
 * See LICENSE file in the project root for full license information.
 */
package fr.phdhien.dispale;

import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import fr.phdhien.dispale.tools.DataSet;
import fr.phdhien.dispale.tools.TItemSet;

/**
 * @author Arnold Hien
 */
public class BestICVSubset {
	
	public int currentPosition;
	public int iter, nbElem;
	public int[] all_ranks;
	public DataSet dataset;
	public BitSet[] all_itemsets;
	public BitSet[] allTransactions;
	public BitSet bestICVSubsetBitSet;
	public HashSet<Integer> bestICVSubset;
	
	public BestICVSubset(int nbElem, String datasetPath) {
		this.iter = 0;
		this.nbElem = nbElem;
		this.bestICVSubsetBitSet = new BitSet();
		this.bestICVSubset = new HashSet<Integer>();
		
		this.all_ranks = new int[nbElem];
		this.all_itemsets = new BitSet[nbElem];
		this.allTransactions = new BitSet[nbElem];
		for(int i=0; i<nbElem; i++){
			this.all_itemsets[i] = new BitSet();
			this.allTransactions[i] = new BitSet();
		
		}
		
		try {
			dataset = new DataSet(datasetPath);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(5);
		}
	}
	
	public void setItemsets(Set<Integer> pattern) {
		for(Integer item : pattern) {
	//public void setItemsets(scala.collection.immutable.Set<Integer> pattern) {
	//	for(Integer item : pattern) {
			all_itemsets[iter].set(item);
		}
		all_ranks[iter] = iter;
		iter++;
	}
	
	public double avg(int[] ranks) {
		int avg = 0;
		for(int i=0; i<ranks.length; i++) {
			avg += ranks[i];
		}
		if(ranks.length != 0)
			avg /= ranks.length;
		
		return avg;
	}
	
	//*
	public double interclassVariance(int[] ranks, Set<Integer> coveredRanks, 
			Set<Integer> uncoveredRanks) {
		int[] a = coveredRanks.stream().mapToInt(Integer::intValue).toArray();
		int[] b = uncoveredRanks.stream().mapToInt(Integer::intValue).toArray();
		
		return coveredRanks.size()*(Math.pow(avg(ranks)-avg(a), 2)) + 
				uncoveredRanks.size()*(Math.pow(avg(ranks)-avg(b), 2));
	}
	//*/
	
	public void enumerateItemsets() {
		double highestICV = 0;
		BitSet top1Pattern = new BitSet();
		Set<BitSet> subItemsets = new HashSet<BitSet>();
		HashMap<BitSet, BitSet> tID = new HashMap<BitSet, BitSet>();
		
		Set<Integer> all_items = new HashSet<Integer>();
		//System.out.println("\n~~~~~~~~~");
		for(BitSet pattern : all_itemsets) {
			//System.out.println("" + pattern);
			
			for(int it=pattern.nextSetBit(0); it!=-1; it=pattern.nextSetBit(it+1)) {
				all_items.add(it);
			}
		}
		//System.out.println("~~~~~~~~~");
		//System.out.println(" - " + all_items + " - ");
		//System.out.println("~~~~~~~~~\n");
		
		// ###############################################
		
		// cover mean the set of patterns in which a sub-itemset appears in
		// get all singleton covers
		for(Integer item : all_items) {
			BitSet it = new BitSet();
			it.set(item);
			
			BitSet currentPattern = new BitSet();
			Set<Integer> coveredRank = new HashSet<Integer>();
			Set<Integer> unCoveredRank = new HashSet<Integer>();
			
			for(int i=0; i<all_itemsets.length; i++) {
				if(all_itemsets[i].get(item)) {
					currentPattern.set(i);
					coveredRank.add(all_ranks[i]);
				}
				else
					unCoveredRank.add(all_ranks[i]);
			}
			tID.put(it, currentPattern);
			
			double icv = interclassVariance(all_ranks, coveredRank, unCoveredRank);
			subItemsets.add(it);
			if(icv > highestICV) {
				highestICV = icv;
				top1Pattern = (BitSet) it.clone();
			}
		}
		
		/*
		System.out.println("~~~~~~~~~");
		System.out.println("top item : " + top1Pattern);
		System.out.println("~~~~~~~~~\n");
		
		// generate all subitemsets having a cover of at least 1
		System.out.println("~~~~~~~~~");
		System.out.println("|all_items| = " + all_items.size());
		System.out.println("|subItemsets| = " + subItemsets.size());
		System.out.println("~~~~~~~~~\n");
		*/
		
		int iter_i = 0;
		while(!subItemsets.isEmpty()) {
			Set<BitSet> nextSubItemsets = new HashSet<BitSet>();
			//System.out.println("i = " + iter_i);
			for(BitSet sub : subItemsets) {
				for(Integer item : all_items) {
					if(!sub.get(item)) {
						Set<Integer> coveredRank = new HashSet<Integer>();
						Set<Integer> unCoveredRank = new HashSet<Integer>();
						
						BitSet new_subItemset = (BitSet) sub.clone();
						new_subItemset.set(item);
						
						BitSet it = new BitSet(), cov = tID.get(sub);
						it.set(item);
						//cov.intersects(tID.get(it));
						cov.and(tID.get(it));
					
						// check if the subitemset doesn't exist in the subItemsets list
						// and if it exist in at least one of the itemset in the list (|cov|>0)
						
						if(!subItemsets.contains(new_subItemset) && (cov.cardinality()>0)){
							tID.put(new_subItemset, cov); // save it cover
							//subItemsets.add(new_subItemset); // save the new sub-itemset
							nextSubItemsets.add(new_subItemset); // save the new sub-itemset
							
							//System.out.println("new sub-itemset = " + new_subItemset);
							
							for(Integer r : all_ranks) {
								if(tID.get(new_subItemset).get(r))
									coveredRank.add(r);
								else
									unCoveredRank.add(r);
							}
						
							double icv = interclassVariance(all_ranks, coveredRank, unCoveredRank);
							if(icv > highestICV) {
								highestICV = icv;
								top1Pattern = (BitSet) new_subItemset.clone();
								//System.out.println("new top = " + top1Pattern);
							}
							else if((icv == highestICV) && 
									(new_subItemset.cardinality() > top1Pattern.cardinality())) {
								highestICV = icv;
								top1Pattern = (BitSet) new_subItemset.clone();
								//System.out.println("new top = " + top1Pattern);
							}
							
						}
						
					}
				}
				//System.out.println("|subItemsets| = " + subItemsets.size());
				//System.out.println("|nextSubItemsets| = " + nextSubItemsets.size());
			}
			subItemsets.clear();
			subItemsets.addAll(nextSubItemsets);
		}
		
		//System.out.println("\n~~~~~~~~~");
		//System.out.println("|all_items| = " + all_items.size());
		//System.out.println("|subItemsets| = " + subItemsets.size());
		//System.out.println("~~~~~~~~~\n");
		
		bestICVSubset.clear();
		bestICVSubsetBitSet.clear();
		for (int i=top1Pattern.nextSetBit(0); i!=-1; i=top1Pattern.nextSetBit(i+1)) {
			bestICVSubset.add(i);
			bestICVSubsetBitSet.set(i);
		}
		
		//BitSet cov_tempo = dataset.covers.getCoverPOP(
		//			new TItemSet(bestICVSubsetBitSet)).getListTransactions();
		//System.out.println("~~~~~~~~~");
		//System.out.println(" - " + bestICVSubset + " - " + cov_tempo + " - ");
		//System.out.println("~~~~~~~~~\n");
		
		getAllTransactions();
	}
	
	public void getAllTransactions() {
		allTransactions = new BitSet[nbElem];
		for(int i=0; i<nbElem; i++) {
			allTransactions[i] = dataset.covers.getCoverPOP(
					new TItemSet(all_itemsets[i])).getListTransactions();
		}
		
	}
	
	public Set<Integer> getBestSolutionItems(){
		Set<Integer> sol = new HashSet<Integer>();
		for (int item=bestICVSubsetBitSet.nextSetBit(0); item!=-1; 
				item=bestICVSubsetBitSet.nextSetBit(item+1)) {
			sol.add(item);
		}
		return sol;
	}
	
	public Set<Integer> getBestSolutionCover() {
		HashSet<Integer> currentCover = new HashSet<Integer>();
		
		//BitSet cov = (BitSet) allTransactions[currentPosition].clone();
		BitSet cov = dataset.covers.getCoverPOP(
					new TItemSet(bestICVSubsetBitSet)).getListTransactions();
		
		for (int tr=cov.nextSetBit(0); tr!=-1; tr=cov.nextSetBit(tr+1))
			currentCover.add(tr);
			//currentCover.add(tr+1);
		
		currentPosition++;
		return (Set<Integer>) currentCover.clone();
	}
	
	public Set<Integer> getBestICVSubset() {
		return bestICVSubset;
	}
	
	public void reset() {
		iter = 0;
		currentPosition = 0;
		bestICVSubset.clear();
		for(int i=0; i<nbElem; i++) {
			all_ranks[i] = -1;
			all_itemsets[i].clear();
			allTransactions[i].clear();
		}
	}
	
}
