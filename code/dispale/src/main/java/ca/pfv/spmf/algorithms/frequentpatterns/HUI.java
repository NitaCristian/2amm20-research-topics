/*
 * This file is part of the DiSPaLe project (https://gitlab.com/phdhien/dispale)
 *
 * Copyright (c) 2023, Normandie Université and IMT Atlantique, France
 *
 * Licensed under the MIT license.
 *
 * See LICENSE file in the project root for full license information.
 */
package ca.pfv.spmf.algorithms.frequentpatterns;

import java.util.BitSet;
import java.util.List;

/**
 * A high utility itemset
 * 
 * @author Maxime Garfagni
 * @author Arnold Hien.
 */
public class HUI {
    /** the itemset */
    public BitSet X;
    /** the written itemset */
    public String itemset;
    /** the itemset's fitness */
    public double fitness;
    /** the itemset's cover */
    public List<Integer> cover;

    /**
     * Constructor
     * 
     * @param itemset an itemset
     * @param fitness its fitness
     */
    public HUI(BitSet X, String itemset, double fitness, List<Integer> cover) {
        super();
        this.X = X;
        this.itemset = itemset;
        this.fitness = fitness;
        this.cover = cover;
    }
}
