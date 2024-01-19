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
package ca.pfv.spmf.algorithms.frequentpatterns.tkuce;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ca.pfv.spmf.algorithms.frequentpatterns.AlgoHUI;
import ca.pfv.spmf.algorithms.frequentpatterns.HUI;
import ca.pfv.spmf.algorithms.frequentpatterns.Item;
import ca.pfv.spmf.algorithms.frequentpatterns.Pair;
import ca.pfv.spmf.algorithms.frequentpatterns.Particle;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the "TKU-CE+ algorithm" for Top-K High-Utility
 * Itemsets Mining as described in the conference paper :
 * <p>
 * Heuristically mining the top-k high-utility itemsets with cross-entropy
 * optimization
 *
 * @author Wei Song, Chuanlong Zheng, Chaomin Huang, and Lu Liu
 * @author Maxime Garfagni
 * @author Arnold Hien
 */

public class AlgoTKUCEP extends AlgoHUI {
    // variable for statistics
    /** max memory usage */
    double maxMemory = 0;
    /** the time the algorithm started */
    long startTimestamp = 0;
    /** the time the algorithm terminated */
    long endTimestamp = 0;

    /** the sample count */
    final int sampleSize = 2000;
    /** the iterations of algorithm */
    final int maxIteration = 2000;
    /** the number of actual iteration */
    int actualIterations = 0;

    /** the size of transactions */
    int transactionCount = 0;

    /** the number of desired HUIs */
    static int K = 0;

    /** the critical utility value */
    double CUV = 0;

    /** probability vector */
    float[] p;
    /** the quantile parameter ρ */
    final float rho = (float) 0.2;
    /** the smooth factor */
    final float SF = (float) 0.2;

    /** a map that stores the utility of each item */
    Map<Integer, Double> mapItemToU;
    /** create a map to store the TWU of each item */
    Map<Integer, Double> mapItemToTWU;
    /** the items which has twu value more than minUtil */
    List<Integer> twuPattern;

    /** writer to write the output file */
    BufferedWriter writer = null;

    /** samples */
    List<Particle> samples = new ArrayList<Particle>();
    /** the set of HUIs */
    List<HUI> huiSets = new ArrayList<HUI>();

    /** a list to store database */
    List<List<Pair>> database = new ArrayList<List<Pair>>();
    /** the list of items */
    List<Item> Items;
    /** Top-k high utility itemsets */
    List<Particle> TopKHuiParticle = new ArrayList<Particle>();

    ArrayList<Set<Integer>> covers;

    /**
     * Default constructor
     */
    public AlgoTKUCEP() {
    }

    /**
     * Run the algorithm
     *
	 * @param input: the input file path
	 * @param k: the parameter k
	 * @param weighs: array containing the utilities weights to be used
	 * @param nb_items: the number of items of the dataset file
	 * @param transUsed: check if transactions utilities are used or not
     * @throws IOException exception if error while writing the file
     */
	@Override
    public List<Set<Integer>> runAlgorithm(String input, int k, double[] weights, int nb_items, boolean transUsed) 
            throws IOException {
		
        assert ((input != null) || (input != "")) : "the input file path can not be null";
        assert weights != null : "the utilities array can not be null";
        assert k > 0 : "the number of HUIs must be greater than zero";
        
        double[] newWeights = new double[weights.length];
        double minWeight = weights[0];

        for (int i = 1; i < weights.length; i++) {
            double x = weights[i];
            if (x < minWeight) {
                minWeight = x;
            }
        }
        if (minWeight < 0) {
            for (int i = 1; i < weights.length; i++) {
                newWeights[i] = weights[i] - minWeight;
            }
        } else {
            newWeights = weights.clone();
        }

        // reset memory usage
        MemoryLogger.getInstance().reset();

        // save the k parameter
        K = k;

        // initialization
        startTimestamp = System.currentTimeMillis();
        mapItemToU = new HashMap<>();
        mapItemToTWU = new HashMap<>();

        // scan the database first time to calculate the TWU of each item.
        BufferedReader myInput = null;
        String thisLine;
        try {
            // prepare the object for reading the file
            myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));

            // for each line (transaction) until the end of file
            while ((thisLine = myInput.readLine()) != null) {

                // if the line is a comment, is empty or is a kind of metadata
                if (thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@') {
                    continue;
                }

                ++transactionCount;// Count the number of transactions in the database

                // the first part is the list of items
                String[] items = thisLine.split(" ");

                // the second part is the transaction utility
                double transactionUtility = 0;
                for (int i = 0; i < items.length - 1; i++) {
                    int item = Integer.parseInt(items[i]);
                    transactionUtility += newWeights[item];
                }
                if(transUsed){
                    transactionUtility = transactionUtility * newWeights[nb_items+transactionCount-1];
                }

                // the third part is the list of utility
                // for each item, we add the transaction utility to its TWU
                for (int i = 0; i < items.length - 1; i++) {

                    // convert item to integer
                    Integer item = Integer.parseInt(items[i]);
                    Double utility = newWeights[item];
                    Double u = mapItemToU.get(item);

                    // add the utility of the item
                    u = (u == null) ? utility : u + utility;
                    mapItemToU.put(item, u);

                    // get the current TWU of the item
                    Double twu = mapItemToTWU.get(item);

                    // add the utility of the item in the current transaction to its twu
                    twu = (twu == null) ? transactionUtility : twu + transactionUtility;
                    mapItemToTWU.put(item, twu);
                }
            }
        } catch (Exception e) {
            // catches exception if error while reading the input file
            e.printStackTrace();
        } finally {
            if (myInput != null) {
                myInput.close();
            }
        }

        // this function is for calculating the critical utility value
        calculateCUV(mapItemToU);

        twuPattern = new ArrayList<>();
        for (Entry<Integer, Double> vo : mapItemToTWU.entrySet()) {
            Integer item = vo.getKey();
            if (mapItemToTWU.get(item) >= CUV) {
                twuPattern.add(item);
            }
        }
        // the probability vector
        p = new float[twuPattern.size()];

        // Collections.sort(twuPattern);
        // SECOND DATABASE PASS TO CONSTRUCT THE DATABASE OF 1-ITEMSETS HAVING TWU >= minutil (promising items)
        try {
            // prepare object for reading the file
            myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
            int tid = 0;

            // variable to count the number of transaction
            // for each line (transaction) until the end of file
            while ((thisLine = myInput.readLine()) != null) {

                // if the line is a comment, is empty or is a kind of metadata
                if (thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                                        || thisLine.charAt(0) == '@') {
                    continue;
                }

                // get the list of items
                String[] items = thisLine.split(" ");

                // Create a list to store items and its utility
                List<Pair> revisedTransaction = new ArrayList<>();

                // for each item
                for (int i = 0; i < items.length - 1; i++) {

                    // convert values to integers
                    Integer item = Integer.parseInt(items[i]);

                    // if the item is contained, add it to revisedTransaction
                    if (mapItemToTWU.get(item) >= CUV) {
                        Pair pair = new Pair();
                        pair.item = Integer.parseInt(items[i]);

                        if(transUsed){
                            pair.utility = newWeights[pair.item] * newWeights[nb_items+tid];
                        }else{
                            pair.utility = weights[pair.item];
                        }
                        revisedTransaction.add(pair);
                    }
                }
                database.add(revisedTransaction);
                tid++;
            }
        } catch (Exception e) {
            // to catch error while reading the input file
            e.printStackTrace();
        } finally {
            if (myInput != null) {
                myInput.close();
            }
        }

        Items = new ArrayList<>();
        for (Integer tempItem : twuPattern) {
            Items.add(new Item(tempItem, transactionCount));
        }

        // construct the database
        for (int i = 0; i < database.size(); ++i) {
            for (Item item : Items) {
                for (int k_ = 0; k_ < database.get(i).size(); ++k_) {
                    if (item.item == database.get(i).get(k_).item) {
                        item.TIDS.set(i);
                    }
                }
            }
        }

        // check the memory usage
        MemoryLogger.getInstance().checkMemory();

        // Mine the database recursively
        if (twuPattern.size() > 0) {
            // generate sample
            generateSample(1.0f);

            // the end symbol
            double max_min;
            for (int i = 0; i < maxIteration; i++) {
                actualIterations++;
                samples.sort(new Comparator<Particle>() {
                    public int compare(Particle itemset1, Particle itemset2) {
                        if (itemset1.fitness - itemset2.fitness > 0) {
                            return -1;
                        } else if (itemset1.fitness - itemset2.fitness < 0) {
                            return 1;
                        } else {
                            return 0;
                        }
                    }
                });
                max_min = samples.get(0).fitness - samples.get((int) (rho * sampleSize) - 1).fitness;
                double propotion = (max_min / samples.get(0).fitness);

                // stopping criterion
                if (max_min == 0) {
                    break;
                }

                // update population and HUIset
                update((1 - SF) * propotion);
            }

            endTimestamp = System.currentTimeMillis();

            // add Top-K huis
            for (int i = 0; i < K; ++i) {
                if (i <= TopKHuiParticle.size() - 1) {
                    insert(TopKHuiParticle.get(i));
                }
            }
        }

        // check the memory usage again and close the file.
        MemoryLogger.getInstance().checkMemory();
        maxMemory = MemoryLogger.getInstance().getMaxMemory();

        // record end time
        endTimestamp = System.currentTimeMillis();

        List<Set<Integer>> topK = new ArrayList<Set<Integer>>();
        this.covers = new ArrayList<Set<Integer>>();
        for (int i = 0; i < this.huiSets.size(); i++) {
            HUI hui = this.huiSets.get(i);

            String[] arrayitemset = hui.itemset.split(" ");
            Set<Integer> SetOfItems = new HashSet<Integer>();

            for (int l = 0; l < arrayitemset.length; l++) {
                SetOfItems.add(Integer.valueOf(arrayitemset[l]));
            }
            topK.add(SetOfItems);

            List<Integer> cover = hui.cover;
            Set<Integer> coverset = new HashSet<Integer>();
            for (int j = 0; j < cover.size(); j++) {
                coverset.add(cover.get(j));
            }
            covers.add(coverset);
        }
        return topK;
    }

    /**
     * this method calculate the critical utility value
     *
     * @param map the map stored the single item and its utility
     */
    public void calculateCUV(Map<Integer, Double> map) {
        if (map == null)
            return;
        int s = map.size();
        Collection<Double> c = map.values();
        Object[] obj = c.toArray();
        Arrays.sort(obj, Collections.reverseOrder());
        s = Math.min(s, K);
        CUV = (double) obj[s - 1];
    }

    /**
     * This is the method to initialization of sample
     *
     * @param proportion the proportion of smooth mutation in sample
     */
    private void generateSample(float proportion) {
        int i, j, k, temp;
        List<Integer> transList;

        for (i = 0; i < (int) (proportion * sampleSize); ++i) {
            Particle tempParticle = new Particle(twuPattern.size());
            j = 0;

            // k is the count of 1 in probability vector
            k = (int) (Math.random() * twuPattern.size() + 1);

            while (j < k) {
                // roulette select the position of 1 in population
                temp = (int) (Math.random() * twuPattern.size());

                // if this position is not occupied
                if (!tempParticle.X.get(temp)) {
                    j++;
                    tempParticle.X.set(temp);
                }
            }

            // calculate fitness
            transList = new ArrayList<>();
            isRBAIndividual(tempParticle, transList);
            tempParticle.calculateFitness(k, transList, twuPattern, database);

            // insert itemset into itemsets collection of current iteration
            samples.add(i, tempParticle);

            // insert into sets of top-k high utility itemsets
            insertTopList(samples.get(i));
        }
    }

    /**
     * This is the method to update the set of top-k HUIs and the probability vector
     * using the new sample
     *
     * @param proportion the proportion of smooth mutation in sample
     */
    private void update(double proportion) {
        int[] num = new int[twuPattern.size()];
        for (int i = 0; i < rho * sampleSize; ++i) {
            for (int j = 0; j < twuPattern.size(); ++j) {
                if (samples.get(i).X.get(j)) {
                    num[j] += 1;
                }
            }
        }
        CUV = samples.get((int) (rho * sampleSize - 1)).fitness;
        for (int i = 0; i < twuPattern.size(); ++i) {
            p[i] = (float) (num[i] / (rho * sampleSize + 0.0));
        }
        List<Integer> transList;
        int k;
        for (int i = 0; i < (int) (proportion * sampleSize); ++i) {
            Particle tempParticle = new Particle(twuPattern.size());
            update_Particle(tempParticle);
            transList = new ArrayList<>();

            if (isRBAIndividual(tempParticle, transList)) {
                k = tempParticle.X.cardinality();
                tempParticle.calculateFitness(k, transList, twuPattern, database);

                if (tempParticle.fitness > CUV) {
                    samples.add(i, tempParticle);
                    insertTopList(samples.get(i));
                }
            }
        }
        generateSample(SF);

    }

    /**
     * generate the particle by probability vector
     *
     * @param temp the temporary particle
     */
    private void update_Particle(Particle temp) {
        for (int i = 0; i < twuPattern.size(); ++i) {
            if (Math.random() < p[i]) {
                temp.X.set(i);
            }
        }
    }

    /**
     * insert Top-K HUIs list
     *
     * @param tmp the temporary particle
     */
    private void insertTopList(Particle tmp) {
        Particle temp = new Particle(twuPattern);
        temp.copyParticle(tmp);
        if (TopKHuiParticle.size() == 0) {
            TopKHuiParticle.add(temp);
            return;
        }

        int max = 0, min = K - 1, mid = 0;
        // If the size of the list is not enough K and the utility value of temp
        // is less than or equal to the minimum utility value append it directly at the end.
        if (TopKHuiParticle.size() < K) {
            min = TopKHuiParticle.size() - 1;
            if (temp.fitness < TopKHuiParticle.get(min).fitness) {
                TopKHuiParticle.add(temp);
                return;
            }
        } else {
            if (temp.fitness < TopKHuiParticle.get(min).fitness) {
                return;
            }
        }

        // find the ordered position using binary search
        while (max <= min) {
            mid = (max + min) / 2;
            if (temp.fitness > TopKHuiParticle.get(mid).fitness) {
                min = mid - 1;
            } else if (temp.fitness < TopKHuiParticle.get(mid).fitness) {
                max = mid + 1;
            } else {
                break;
            }
        }

        int mid_start = mid, mid_end = mid;
        if (temp.fitness > TopKHuiParticle.get(mid).fitness) {
            TopKHuiParticle.add(mid, temp);
        } else if (temp.fitness < TopKHuiParticle.get(mid).fitness) {
            TopKHuiParticle.add(mid + 1, temp);
        } else {

            // if exists some particles with same fitness and the TopKHuiParticle
            // does not contain the new temporary particle then add it to the
            // TopKHuiParticle
            if (!TopKHuiParticle.contains(temp)) {
                while (TopKHuiParticle.get(mid_start).fitness == temp.fitness) {
                    if (TopKHuiParticle.get(mid_start).X.equals(temp.X)
                            || TopKHuiParticle.get(mid_end).X.equals(temp.X)) {
                        return;
                    }
                    mid_start--;

                    // out of bound
                    if (mid_start == -1) {
                        break;
                    }
                }

                while (TopKHuiParticle.get(mid_end).fitness == temp.fitness) {
                    if (TopKHuiParticle.get(mid_end).X.equals(temp.X)) {
                        return;
                    }
                    mid_end++;

                    // out of bound
                    if (mid_end == TopKHuiParticle.size()) {
                        break;
                    }
                }
                TopKHuiParticle.add(mid, temp);
            }
        }
    }

    /**
     * It is used to get the collection of transactions in which the itemset
     * resides. If the itemset itself is unreasonable, it is automatically
     * fine-tuned during the calculation. Make it reasonable and get the set of
     * transactions in which it is located
     *
     * @param tempParticle the temporary particle
     * @param tempList     store the existed position of this particle
     * @return a bool value representing the temporary particle is a reasonable particle
     */
    public boolean isRBAIndividual(Particle tempParticle, List<Integer> tempList) {
        List<Integer> templist = new ArrayList<>();
        for (int i = 0; i < twuPattern.size(); ++i) {
            if (tempParticle.X.get(i)) {
                templist.add(i);
            }
        }
        if (templist.size() == 0) {
            return false;
        }
        BitSet tempBitSet = new BitSet(transactionCount);
        BitSet midBitSet = new BitSet(transactionCount);
        tempBitSet = (BitSet) Items.get(templist.get(0)).TIDS.clone();
        midBitSet = (BitSet) tempBitSet.clone();

        for (int i = 1; i < templist.size(); ++i) {

            tempBitSet.and(Items.get(templist.get(i)).TIDS);

            if (tempBitSet.cardinality() != 0) {
                midBitSet = (BitSet) tempBitSet.clone();
            } else {
                tempBitSet = (BitSet) midBitSet.clone();
                tempParticle.X.clear(templist.get(i));
            }
        }
        if (tempBitSet.cardinality() == 0) {
            return false;
        } else {
            for (int m = 0; m < tempBitSet.length(); ++m) {
                if (tempBitSet.get(m)) {
                    tempList.add(m);
                }
            }
            return true;
        }
    }

    /**
     * Method to inseret tempItemset to huiSets
     *
     * @param tempParticle the itemset to be inserted
     */
    private void insert(Particle tempParticle) {
        int i;
        StringBuilder temp = new StringBuilder();
        for (i = 0; i < twuPattern.size(); i++) {
            if (tempParticle.X.get(i)) {
                temp.append(twuPattern.get(i));
                temp.append(' ');
            }
        }
        // huiSets is null
        if (huiSets.size() == 0) {
            huiSets.add(new HUI(tempParticle.X, temp.toString(), tempParticle.fitness, tempParticle.cover));
        } else {

            // huiSets is not null, judge whether exist an itemset in huiSets same with tempParticle
            for (i = 0; i < huiSets.size(); i++) {
                if (temp.toString().equals(huiSets.get(i).itemset)) {
                    break;
                }
            }

            // if not exist same itemset in huiSets with tempParticle,insert it into huiSets
            if (i == huiSets.size())
                huiSets.add(new HUI(tempParticle.X, temp.toString(), tempParticle.fitness, tempParticle.cover));
        }
    }

    /**
     * Method to write a high utility itemset to the output file.
     *
     * @throws IOException throw exception
     */
    /*
     * private void writeOut() throws IOException {
     * for (HUI huiSet : huiSets) {
     * String buffer = huiSet.itemset +
     * // append the utility value
     * "#UTIL:" + huiSet.fitness;
     * writer.write(buffer);
     * writer.newLine();
     * }
     * }
     */

    /**
     * Print statistics about the latest execution to System.out.
     */
    public void printStats() {
        System.out.println("============ TKU-CE+ Algorithm v 2.52 ===========");
        System.out.println(" Total time: " + (endTimestamp - startTimestamp) + " ms");
        System.out.println(" Memory: " + maxMemory + " MB");
        System.out.println(" Actual iterations: " + actualIterations);
        System.out.println(" High-utility itemsets count: " + huiSets.size());
        System.out.println("=================================================");
    }
	
    /*
     * Get the cover of all itemsets
     */
    @Override
	public ArrayList<Set<Integer>> get_all_itemsets_cover() {
		return this.covers;
	}
	
    /*
     * Get the cover of one itemset
     */
    @Override
	public Set<Integer> get_one_itemset_cover(int i) {
		return this.covers.get(i);
	}

}
