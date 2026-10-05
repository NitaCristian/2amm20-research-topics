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
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
	
	// multiple discriminant sub-patterns
	// nbSubPatterns: maximum number of sub-patterns to select (m)
	// selection: "top" (the m highest ICV) or "complementary" (greedy, penalising redundant sub-patterns)
	// redundancyWeight: how strongly redundancy is penalised by the "complementary" selection (0 = same as "top")
	public int nbSubPatterns = 1;
	public String selection = "complementary";
	public double redundancyWeight = 1.0;
	public List<Candidate> candidates = new ArrayList<Candidate>();
	public List<Candidate> selected = new ArrayList<Candidate>();
	
	// a sub-itemset, the query patterns containing it (cover) and its ICV
	public static class Candidate {
		public final BitSet items;
		public final BitSet cover;
		public final double icv;
		
		public Candidate(BitSet items, BitSet cover, double icv) {
			this.items = items;
			this.cover = cover;
			this.icv = icv;
		}
	}
	
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
	
	public void setSelection(int nbSubPatterns, String selection, double redundancyWeight) {
		this.nbSubPatterns = Math.max(1, nbSubPatterns);
		this.selection = selection.toLowerCase();
		this.redundancyWeight = redundancyWeight;
	}
	
	public double avg(int[] ranks) {
		double avg = 0;
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
			if(icv > 0)
				candidates.add(new Candidate((BitSet) it.clone(), (BitSet) currentPattern.clone(), icv));
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
		
		//int iter_i = 0;
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
						
						// the same sub-itemset can be reached from several parents: evaluate it only once
						if(nextSubItemsets.contains(new_subItemset))
							continue;
						
						// clone: `and' must not modify the stored cover of `sub'
						BitSet it = new BitSet(), cov = (BitSet) tID.get(sub).clone();
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
							if(icv > 0)
								candidates.add(new Candidate((BitSet) new_subItemset.clone(), (BitSet) cov.clone(), icv));
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
		
		selectSubPatterns();
		
		//BitSet cov_tempo = dataset.covers.getCoverPOP(
		//			new TItemSet(bestICVSubsetBitSet)).getListTransactions();
		//System.out.println("~~~~~~~~~");
		//System.out.println(" - " + bestICVSubset + " - " + cov_tempo + " - ");
		//System.out.println("~~~~~~~~~\n");
		
		getAllTransactions();
	}
	
	// order used to break ICV ties: higher ICV first, then longer sub-pattern first
	// (List.sort is stable, so remaining ties keep the enumeration order, as the original top-1 search did)
	private static int compareCandidates(Candidate a, Candidate b) {
		int c = Double.compare(b.icv, a.icv);
		if(c != 0)
			return c;
		return Integer.compare(b.items.cardinality(), a.items.cardinality());
	}
	
	private static double jaccard(BitSet a, BitSet b) {
		BitSet inter = (BitSet) a.clone();
		inter.and(b);
		BitSet union = (BitSet) a.clone();
		union.or(b);
		return union.isEmpty() ? 0.0 : (double) inter.cardinality() / union.cardinality();
	}
	
	// redundancy of a candidate w.r.t. an already selected sub-pattern:
	// - same query patterns covered (it captures the same part of the ranking), or
	// - same items (e.g. (A,B) vs (A,B,C))
	public static double redundancy(Candidate c, Candidate s) {
		return Math.max(jaccard(c.cover, s.cover), jaccard(c.items, s.items));
	}
	
	// select up to `nbSubPatterns' sub-patterns among the candidates
	// - "top": the highest ICV ones
	// - "complementary": greedy, each step takes the candidate maximising
	//       ICV * (1 - redundancyWeight * max redundancy with the already selected ones)
	// the first selected sub-pattern is always the highest ICV one (same as the original DiSPaLe)
	public void selectSubPatterns() {
		selected.clear();
		List<Candidate> sorted = new ArrayList<Candidate>(candidates);
		sorted.sort(BestICVSubset::compareCandidates);
		
		if(sorted.isEmpty()) {
			// no discriminating sub-pattern: keep the original behaviour (the empty sub-pattern)
			selected.add(new Candidate(new BitSet(), new BitSet(), 0.0));
			return;
		}
		
		if(selection.equals("top")) {
			for(int i=0; i<sorted.size() && selected.size()<nbSubPatterns; i++)
				selected.add(sorted.get(i));
			return;
		}
		
		selected.add(sorted.get(0));
		while(selected.size() < nbSubPatterns) {
			Candidate best = null;
			double bestScore = 0;
			for(Candidate c : sorted) {
				if(selected.contains(c))
					continue;
				double maxRed = 0;
				for(Candidate s : selected)
					maxRed = Math.max(maxRed, redundancy(c, s));
				double score = c.icv * (1 - redundancyWeight * maxRed);
				if(score > bestScore) {
					bestScore = score;
					best = c;
				}
			}
			if(best == null)
				break; // every remaining candidate is fully redundant
			selected.add(best);
		}
	}
	
	public int getNbSelected() {
		return selected.size();
	}
	
	public double getSolutionICV(int j) {
		return selected.get(j).icv;
	}
	
	public Set<Integer> getSolutionItems(int j){
		Set<Integer> sol = new HashSet<Integer>();
		BitSet items = selected.get(j).items;
		for (int item=items.nextSetBit(0); item!=-1; item=items.nextSetBit(item+1)) {
			sol.add(item);
		}
		return sol;
	}
	
	// transactions of the dataset containing the j-th selected sub-pattern
	public Set<Integer> getSolutionCover(int j) {
		HashSet<Integer> currentCover = new HashSet<Integer>();
		BitSet cov = dataset.covers.getCoverPOP(
					new TItemSet(selected.get(j).items)).getListTransactions();
		for (int tr=cov.nextSetBit(0); tr!=-1; tr=cov.nextSetBit(tr+1))
			currentCover.add(tr);
		return currentCover;
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
	
	@SuppressWarnings("unchecked")
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
		candidates.clear();
		selected.clear();
		for(int i=0; i<nbElem; i++) {
			all_ranks[i] = -1;
			all_itemsets[i].clear();
			allTransactions[i].clear();
		}
	}
	
}
