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

/**
 * Class representing an item
 * 
 * @author Maxime Garfagni
 * @author Arnold Hien
 */
public class Item {
    /** the item */
    public int item;
    /** the item's bitset */
    public BitSet TIDS;

    /**
     * Constructor with an item
     * 
     * @param item the item
     */
    public Item(int item, int transactionCount) {
        TIDS = new BitSet(transactionCount);
        this.item = item;
    }
}
