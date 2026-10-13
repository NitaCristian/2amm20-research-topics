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
	// selector: the rule that picks them among the candidates (see SubPatternSelector)
	public int nbSubPatterns = 1;
	public SubPatternSelector selector = new SubPatternSelector.Top();
	public List<Candidate> candidates = new ArrayList<Candidate>();
	public List<Candidate> selected = new ArrayList<Candidate>();
	
	// switches that restore errors of the published implementation, to measure each correction on its own
	// legacyIntAverage:  mean ranks in the ICV are truncated to integers
	// legacyCoverInPlace: the cover of Y u {i} is computed by intersecting the stored cover of Y in place
	// legacyNoDedupe:    a sub-itemset reachable from several parents is evaluated once per parent
	public boolean legacyIntAverage = false;
	public boolean legacyCoverInPlace = false;
	public boolean legacyNoDedupe = false;
	
	// statistics of the last search, for the trace
	public int nbCandidatesRaw = 0;      // sub-itemsets with ICV > 0
	public int nbTiedAtMax = 0;          // distinct covers sharing the highest ICV
	public boolean top1Matches = true;   // the first selected sub-pattern is the one the original top-1 rule returns
	public int nbTop1Mismatches = 0;     // over the whole run
	public long enumerateNanos = 0, selectNanos = 0;
	
	// a sub-itemset, the query patterns containing it (cover), its ICV and its position in the enumeration
	public static class Candidate {
		public final BitSet items;
		public final BitSet cover;
		public final double icv;
		public final int order;
		
		public Candidate(BitSet items, BitSet cover, double icv, int order) {
			this.items = items;
			this.cover = cover;
			this.icv = icv;
			this.order = order;
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
	
	public void setSelection(int nbSubPatterns, SubPatternSelector selector) {
		this.nbSubPatterns = Math.max(1, nbSubPatterns);
		this.selector = selector;
	}
	
	public double avg(int[] ranks) {
		double avg = 0;
		for(int i=0; i<ranks.length; i++) {
			avg += ranks[i];
		}
		if(ranks.length != 0)
			avg /= ranks.length;
		
		// the published code accumulated in an int, so the mean was truncated
		return legacyIntAverage ? Math.floor(avg) : avg;
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
		long start = System.nanoTime();
		int order = 0;
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
				candidates.add(new Candidate((BitSet) it.clone(), (BitSet) currentPattern.clone(), icv, order++));
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
						if(!legacyNoDedupe && nextSubItemsets.contains(new_subItemset))
							continue;
						
						// clone: `and' must not modify the stored cover of `sub'
						BitSet it = new BitSet(), cov = legacyCoverInPlace ? tID.get(sub) : (BitSet) tID.get(sub).clone();
						it.set(item);
						//cov.intersects(tID.get(it));
						cov.and(tID.get(it));
					
						// keep it if it occurs in at least one query pattern (|cov|>0)
						// (the published check !subItemsets.contains(new_subItemset) was always true:
						// subItemsets only holds sub-itemsets one item shorter)
						if(cov.cardinality()>0){
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
								candidates.add(new Candidate((BitSet) new_subItemset.clone(), (BitSet) cov.clone(), icv, order++));
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
		
		enumerateNanos = System.nanoTime() - start;
		start = System.nanoTime();
		selectSubPatterns();
		selectNanos = System.nanoTime() - start;
		
		// regression check: every rule starts from the first candidate, which must be the sub-pattern the
		// original top-1 rule (kept above as top1Pattern) returns
		top1Matches = selected.isEmpty() || selected.get(0).icv == 0.0 || selected.get(0).items.equals(top1Pattern);
		if(!top1Matches) {
			nbTop1Mismatches++;
			System.err.println("WARNING: first selected sub-pattern " + selected.get(0).items
					+ " differs from the original top-1 sub-pattern " + top1Pattern);
		}
		
		//BitSet cov_tempo = dataset.covers.getCoverPOP(
		//			new TItemSet(bestICVSubsetBitSet)).getListTransactions();
		//System.out.println("~~~~~~~~~");
		//System.out.println(" - " + bestICVSubset + " - " + cov_tempo + " - ");
		//System.out.println("~~~~~~~~~\n");
		
		getAllTransactions();
	}
	
	// order of the candidates: higher ICV first, then longer sub-pattern, then earlier in the enumeration
	// (this reproduces the original top-1 rule, which kept the first, longest sub-itemset with the highest ICV)
	private static int compareCandidates(Candidate a, Candidate b) {
		int c = Double.compare(b.icv, a.icv);
		if(c != 0)
			return c;
		c = Integer.compare(b.items.cardinality(), a.items.cardinality());
		if(c != 0)
			return c;
		return Integer.compare(a.order, b.order);
	}
	
	// sub-itemsets with the same query cover split the query identically, so they are the same feature for the
	// learner: keep one per cover, the first in the candidate order (longest, then earliest)
	private static List<Candidate> distinctCovers(List<Candidate> sorted) {
		List<Candidate> distinct = new ArrayList<Candidate>();
		Set<BitSet> seen = new HashSet<BitSet>();
		for(Candidate c : sorted)
			if(seen.add(c.cover))
				distinct.add(c);
		return distinct;
	}
	
	// select up to `nbSubPatterns' sub-patterns among the candidates with the configured rule;
	// every rule starts with the first candidate, i.e. the sub-pattern of the original top-1 rule
	public void selectSubPatterns() {
		selected.clear();
		List<Candidate> sorted = new ArrayList<Candidate>(candidates);
		sorted.sort(BestICVSubset::compareCandidates);
		nbCandidatesRaw = sorted.size();
		sorted = distinctCovers(sorted);
		candidates = sorted;
		nbTiedAtMax = 0;
		for(Candidate c : sorted)
			if(c.icv == sorted.get(0).icv)
				nbTiedAtMax++;
		
		if(sorted.isEmpty()) {
			// no discriminating sub-pattern: keep the original behaviour (the empty sub-pattern)
			selected.add(new Candidate(new BitSet(), new BitSet(), 0.0, 0));
			return;
		}
		selected.addAll(selector.select(sorted, nbSubPatterns, iter));
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
	
	// ranks (positions in the ranked query) of the query patterns containing the j-th selected sub-pattern
	public List<Integer> getSolutionQueryCover(int j) {
		List<Integer> ranks = new ArrayList<Integer>();
		BitSet cov = selected.get(j).cover;
		for (int r=cov.nextSetBit(0); r!=-1; r=cov.nextSetBit(r+1))
			ranks.add(r);
		return ranks;
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
