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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * this class represent the sample 
 * 
 * @author Maxime Garfagni
 * @author Arnold Hien
 */
public class Particle {
    /** the sample */
    public BitSet X;

    /** fitness value of sample */
    public double fitness;

    public List<Integer> cover;

    /** Constructor of particle */
    public Particle(List<Integer> twuPattern) {
        X = new BitSet(twuPattern.size());
    }

    /**
     * Constructor of particle
     * 
     * @param length the particle length
     */
    public Particle(int length) {
        X = new BitSet(length);
    }

    /**
     * Copy a particle
     * 
     * @param particle1 the particle
     */
    public void copyParticle(Particle particle1) {
        this.X = (BitSet) particle1.X.clone();
        this.fitness = particle1.fitness;
        this.cover = particle1.cover;
    }

    /**
     * Calculate the fitness
     * 
     * @param k        the k value
     * @param templist a temporary list
     */
    public void calculateFitness(int k, List<Integer> templist, List<Integer> twuPattern, List<List<Pair>> database) {
        this.cover = new ArrayList<Integer>();
        for (int i = 0; i < templist.size(); i++) {
            this.cover.add(templist.get(i));
        }
        if (k == 0)
            return;
        int i, p, q, temp, m;
        double sum, fitness = 0;
        for (m = 0; m < templist.size(); m++) {
            p = templist.get(m);
            i = 0;
            q = 0;
            temp = 0;
            sum = 0;

            while (q < database.get(p).size() && i < twuPattern.size()) {
                if (this.X.get(i)) {
                    // using a loop for solving the unordered datasets
                    for (int t = 0; t < database.get(p).size(); t++) {
                        if (database.get(p).get(t).item == twuPattern.get(i)) {
                            sum = sum + database.get(p).get(t).utility;
                            ++i;
                            ++temp;
                            break;
                        }
                    }
                } else {
                    ++i;
                }
            }
            if (temp == k) {
                fitness = fitness + sum;
            }
        }
        this.fitness = fitness;
    }
}
