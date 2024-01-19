/*
 *  Copyright (c) 2021 Wei Song, Lu Liu, and Chaomin Huang
 * 
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 * 
 * It has been updated by Maxime Garfagni to be compatible with float utilities for our pattern mining purpose.
 *
 * SPMF is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF.  If not, see <http://www.gnu.org/licenses/>.
 */
package ca.pfv.spmf.algorithms.frequentpatterns.tko;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import ca.pfv.spmf.algorithms.frequentpatterns.AlgoHUI;
import ca.pfv.spmf.algorithms.frequentpatterns.Pair;
import ca.pfv.spmf.algorithms.frequentpatterns.hui_miner_float.Element;
import ca.pfv.spmf.algorithms.frequentpatterns.hui_miner_float.UtilityList;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * A simple implementation of the TKO algorithm without some of the 
 * optimizations described in the paper.
 * 
 * @author Philippe Fournier-Viger et al.
 * @author Maxime Garfagni
 * @author Arnold Hien
 */
public class AlgoTKO extends AlgoHUI { 

	/** the time the algorithm terminated */
	protected long totalTime = 0; 
	
	/** the number of HUI generated  */
	protected int huiCount = 0; 

	/** the k parameter */
	protected int k = 0;
	
	/** the internal min utility variable */
	protected double minutility = 0; 

	/** the top k rules found until now */
	protected PriorityQueue<FloatItemsetTKO> kItemsets; 
	
	protected ArrayList<Set<Integer>> covers;

	/** We create a map to store the TWU of each item */
	protected final Map<Integer, Double> mapItemToTWU = new HashMap<Integer, Double>();

	/** 
	 * Constructor
	 */
	public AlgoTKO() {

	}

	/**
	 * Run the algorithm
	 * @param input: the input file path
	 * @param k: the parameter k
	 * @param weighs: array containing the utilities weights to be used
	 * @param nb_items: the number of items of the dataset file
	 * @param transUsed: check if transactions utilities are used or not
	 * @throws IOException if an error occur for reading/writing to file.
	 */
	@Override
	public List<Set<Integer>> runAlgorithm(String input, int k, double[] weights, int nb_items, boolean transUsed)
			throws IOException {
		
        assert ((input != null) || (input != "")) : "the input file path can not be null";
        assert weights != null : "the utilities array can not be null";
        
		double[] newWeights = new double[weights.length];
		double minWeight = weights[0];

		for (int i = 1; i < weights.length; i++) {
			double x = weights[i];

			if (x<minWeight) {
				minWeight = x;
			}
		}
		if (minWeight<0) {
			for (int i = 1; i < weights.length; i++) {
				newWeights[i] = weights[i] - minWeight;
			}
		}
		else {
			newWeights=weights.clone();
		}
		
		MemoryLogger.getInstance().reset();
		long startTimestamp = System.currentTimeMillis();
		this.minutility = 1;
		this.k = k;

		this.kItemsets = new PriorityQueue<FloatItemsetTKO>();

		// We scan the database a first time to calculate the TWU of each item.
		BufferedReader myInput = null;
		String thisLine;
		try {
			FileInputStream fin = new FileInputStream(new File(input));
			myInput = new BufferedReader(new InputStreamReader(fin));

			// for each line (transaction)
			int transactionNb=0;

			while ((thisLine = myInput.readLine()) != null) {

				// if the line is  a comment, is  empty or is a kind of metadata
				if (thisLine.isEmpty() == true ||	thisLine.charAt(0) == '#' 
						|| thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}

				//items + useless element
				String[] items = thisLine.split(" "); 

				// the transaction utility
				double transactionUtility = 0;
				for (int i=0;i<items.length-1;i++) {
					int item=Integer.parseInt(items[i]);
					transactionUtility+=newWeights[item];
				}

                if(transUsed){
                    transactionUtility = transactionUtility * newWeights[nb_items+transactionNb];
                    transactionNb += 1;
                }

				// for each item, we add the transaction utility to its TWU
				for (int i = 0; i < items.length-1; i++) {
					Integer item = Integer.parseInt(items[i]);

					// get the current TWU
					Double twu = mapItemToTWU.get(item);

					// update the twu
					twu = (twu == null) ? transactionUtility : twu + transactionUtility;
					mapItemToTWU.put(item, twu);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}

		// CREATE A LIST TO STORE THE ITEMS WITH TWU >= MIN_UTILITY.
		List<UtilityList> listItems = new ArrayList<UtilityList>();

		// CREATE A MAP TO STORE THE UTILITY LIST FOR EACH ITEM.
		Map<Integer, UtilityList> mapItemToUtilityList = new HashMap<Integer, UtilityList>(10000);
		
		// For each item
		for (Integer item : mapItemToTWU.keySet()) {
			UtilityList uList = new UtilityList(item);
			// add the item to the list of high TWU items
			listItems.add(uList);
			// create an empty Utility List that we will fill later.
			mapItemToUtilityList.put(item, uList);
		}

		// SORT THE LIST OF HIGH TWU ITEMS IN ASCENDING ORDER
		Collections.sort(listItems, new Comparator<UtilityList>() {
			public int compare(UtilityList o1, UtilityList o2) {
				double compare = mapItemToTWU.get(o1.item)
						- mapItemToTWU.get(o2.item);
				if (compare == 0) {
					// return (ascendingOrder) ? o1.item - o2.item: o2.item - o1.item;
					return (o1.item - o2.item);
				}
				else if (compare<0) {
					return -1;
				}
				else {
					return 1;
				}
			}
		});

		// SECOND DATABASE PASS TO CONSTRUCT THE UTILITY LISTS
		// OF 1-ITEMSETS HAVING TWU >= minutil (promising items)
		try {
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			int tid = 0;
			// for each line (transaction)
			while ((thisLine = myInput.readLine()) != null) {
				
				// if the line is  a comment, is  empty or is a kind of metadata
				if (thisLine.isEmpty() == true ||	thisLine.charAt(0) == '#' 
						|| thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}
				
				//items + useless element
				String[] items = thisLine.split(" ");

				double remainingUtility = 0;

				// Create a list to store items
				List<Pair> revisedTransaction = new ArrayList<Pair>();

				// for each item
				for (int i = 0; i < items.length-1; i++) {

					// convert values to integers
					Pair pair = new Pair();
					pair.item = Integer.parseInt(items[i]);

                    if(transUsed){
                        pair.utility = newWeights[Integer.parseInt(items[i])]*newWeights[nb_items+tid];
                    }else{
					    pair.utility = newWeights[Integer.parseInt(items[i])]; // @TODO
                    }

					revisedTransaction.add(pair);
					remainingUtility += pair.utility;
				}

				Collections.sort(revisedTransaction, new Comparator<Pair>() {
					public int compare(Pair o1, Pair o2) {
						return compareItems(o1.item, o2.item);
					}
				});

				// for each item left in the transaction
				for (Pair pair : revisedTransaction) {

					// subtract the utility of this item from the remaining utility
					remainingUtility = remainingUtility - pair.utility;

					// get the utility list of this item
					UtilityList utilityListOfItem = mapItemToUtilityList.get(pair.item);

					// Add a new Element to the utility list of this item
					// corresponding to this transaction
					Element element = new Element(tid, pair.utility, remainingUtility);

					utilityListOfItem.addElement(element);
				}
				tid++; // increase tid number for next transaction
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}

		// check the memory usage
		MemoryLogger.getInstance().checkMemory();

		// Mine the database recursively
		search(new int[0], null, listItems);

		// check the memory usage again and close the file.
		MemoryLogger.getInstance().checkMemory();
		totalTime = (System.currentTimeMillis() - startTimestamp) / 1000;
		
		// build matrix
		List<Set<Integer>> topK = new ArrayList<Set<Integer>>();
		this.covers = new ArrayList<Set<Integer>>();
		while( !kItemsets.isEmpty() ) {

			FloatItemsetTKO FloatItemset=kItemsets.poll();
			int[] itemset=FloatItemset.getWholeItemset();
			Set<Integer> SetOfItems=new HashSet<Integer>();

			for (int l=0;l<itemset.length;l++) {
				SetOfItems.add(Integer.valueOf(itemset[l]));
			}
			topK.add(SetOfItems);
			
			List<Element> cover=FloatItemset.cover;
			Set<Integer> coverset=new HashSet<Integer>();

			for (int j=0;j<cover.size();j++) {
				coverset.add(cover.get(j).tid);
			}
			covers.add(coverset);
		}
		return topK;
	}

	/**
	 * This is the recursive method to find all high utility itemsets. 
	 * It save the itemsets to the output list.
	 * @param prefix: This is the current prefix. Initially, it is empty.
	 * @param pUL: This is the Utility List of the prefix. Initially, it is empty.
	 * @param ULs: The utility lists corresponding to each extension of the prefix.
	 * @throws IOException
	 */
	private void search(int[] prefix, UtilityList pUL, List<UtilityList> ULs) throws IOException {
		MemoryLogger.getInstance().checkMemory();

		// For each extension X of prefix P
		for (int i = 0; i < ULs.size(); i++) {
			UtilityList X = ULs.get(i);

			// If pX is a high utility itemset, we save the itemset: pX
			if (X.sumIutils >= minutility) {
				saveHUI(prefix, X.item, X.sumIutils, X.elements);	
			}

			// If the sum of the remaining utilities for pX is higher than minUtility, 
			// we explore extensions of pX (this is the pruning condition)
			if (X.sumRutils + X.sumIutils >= minutility) {

				// This list will contain the utility lists of pX extensions.
				List<UtilityList> exULs = new ArrayList<UtilityList>();

				// For each extension of p appearing after X according to the ascending order
				for (int j = i + 1; j < ULs.size(); j++) {
					UtilityList Y = ULs.get(j);

					// we construct the extension pXY and add it to the list of extensions of pX
					exULs.add(construct(pUL, X, Y));
				}

				// We create new prefix pX
				int[] newPrefix = new int[prefix.length + 1];
				System.arraycopy(prefix, 0, newPrefix, 0, prefix.length);
				newPrefix[prefix.length] = X.item;

				// We make a recursive call to discover all itemsets with the prefix pX
				search(newPrefix, X, exULs);
			}
		}
	}

	/**
	 * Save a high utility itemset to the output list.
	 * @param prefix: a prefix itemset
	 * @param item: an item to be appended to the prefix
	 * @param utility: the utility of the prefix concatenated with the item
	 * @param cover: the cover of the itemset
	 */
	private void saveHUI(int[] prefix, int item, double utility, List<Element> cover) {
		FloatItemsetTKO itemset = new FloatItemsetTKO(prefix, item, utility,cover);
		kItemsets.add(itemset);

		if (kItemsets.size() > k) {
			FloatItemsetTKO lower;
			do {
				lower = kItemsets.peek();
				if (lower == null) {
					break; //IMPORTANT
				}
				kItemsets.remove(lower);
			} while (kItemsets.size() > k);

			this.minutility = kItemsets.peek().utility;
		}
	}

	/**
	 * This method constructs the utility list of pXY
	 * @param P:  the utility list of prefix P.
	 * @param px: the utility list of pX
	 * @param py: the utility list of pY
	 * @return the utility list of pXY
	 */
	private UtilityList construct(UtilityList P, UtilityList px, UtilityList py) {

		// create an empy utility list for pXY
		UtilityList pxyUL = new UtilityList(py.item);

		// for each element in the utility list of pX
		for(Element ex : px.elements){

			// do a binary search to find element ey in py with tid = ex.tid
			Element ey = findElementWithTID(py, ex.tid);

			if(ey == null){
				continue;
			}
			// if the prefix p is null
			if(P == null){
				// Create the new element
				Element eXY = new Element(ex.tid, ex.iutils + ey.iutils, ey.rutils);

				// add the new element to the utility list of pXY
				pxyUL.addElement(eXY);
				
			}else{
				// find the element in the utility list of p wih the same tid
				Element e = findElementWithTID(P, ex.tid);
				if(e != null){
					// Create new element
					Element eXY = new Element(ex.tid, ex.iutils + ey.iutils - e.iutils, ey.rutils);

					// add the new element to the utility list of pXY
					pxyUL.addElement(eXY);
				}
			}
		}
		// return the utility list of pXY.
		return pxyUL;
	}
	
	/**
	 * Do a binary search to find the element with a given tid in a utility list
	 * @param ulist: the utility list
	 * @param tid:  the tid
	 * @return  the element or null if none has the tid.
	 */
	private Element findElementWithTID(UtilityList ulist, int tid){
		List<Element> list = ulist.elements;
		
		// perform a binary search to check if  the subset appears in  level k-1.
        int first = 0;
        int last = list.size() - 1;
       
        // the binary search
        while( first <= last )
        {
        	int middle = ( first + last ) >>> 1; // divide by 2

            if(list.get(middle).tid < tid){
            	first = middle + 1;  //  the itemset compared is larger than the subset according to the lexical order
            }
            else if(list.get(middle).tid > tid){
            	last = middle - 1; //  the itemset compared is smaller than the subset  is smaller according to the lexical order
            }
            else{
            	return list.get(middle);
            }
        }
		return null;
	}

	/**
	 * Write the result to a file
	 * @param path: the output file path
	 * @throws IOException if an exception for reading/writing to file
	 */
	public void writeResultTofile(String path) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(path));
		Iterator<FloatItemsetTKO> iter = kItemsets.iterator();
		while (iter.hasNext()) {
			StringBuffer buffer = new StringBuffer();
			FloatItemsetTKO itemset = (FloatItemsetTKO) iter.next();
			
			// append the prefix
			for (int i = 0; i < itemset.getItemset().length; i++) {
				buffer.append(itemset.getItemset()[i]);
				buffer.append(' ');
			}
			buffer.append(itemset.item);
			
			// append the utility value
			buffer.append(" #UTIL: ");
			buffer.append(itemset.utility);
			
			// write to file
			writer.write(buffer.toString());
			if(iter.hasNext()){
				writer.newLine();
			}
		}
		writer.close();
	}


	private int compareItems(int item1, int item2) {
		double compare = mapItemToTWU.get(item1)- mapItemToTWU.get(item2);
		if (compare == 0) {
			// return (ascendingOrder) ? o1.item - o2.item: o2.item - o1.item;
			return (item1 - item2);
		}
		else if (compare < 0) {
			return -1;
		}
		else {
			return 1;
		}
		
	}

	/**
	 * Print statistics about the latest execution to System.out.
	 */
	public void printStats() {
		System.out
				.println("=============  TKO-BASIC - v.2.28 =============");
		System.out
				.println(" High-utility itemsets count : " + kItemsets.size());
		System.out.println(" Total time ~ " + totalTime
				+ " s");
		System.out.println(" Memory ~ " + MemoryLogger.getInstance().getMaxMemory() + " MB");
		System.out.println("===================================================");
	}
	
    /*
     * Get the cover of all itemsets
     */
	public ArrayList<Set<Integer>> get_all_itemsets_cover() {
		return this.covers;
	}
	
    /*
     * Get the cover of one itemset
     */
	public Set<Integer> get_one_itemset_cover(int i) {
		return this.covers.get(i);
	}
	
}
