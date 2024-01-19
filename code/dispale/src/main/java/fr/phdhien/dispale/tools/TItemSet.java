/*
 * This file is part of the DiSPaLe project (https://gitlab.com/phdhien/dispale)
 *
 * Copyright (c) 2022, Normandie Université, France
 *
 * Licensed under the MIT license.
 *
 * See LICENSE file in the project root for full license information.
 */
package fr.phdhien.dispale.tools;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

/**
 * @author Arnold Hien
 */
public class TItemSet {
	int offset;
	BitSet items3;

	List<Integer> items2;
	Integer[] items;

	///////////////////////// ///////////////bitSet////////////////////////////////////////////
	public TItemSet(BitSet itemset0) {

		this.items3 = itemset0;

	}

	public BitSet getBitItemSet() {
		return items3;
	}

	public boolean subItemSet(TItemSet itemset0) {
		// TODO
		if (testContains(items3, itemset0.getBitItemSet()))
			return true;
		else
			return false;
	}

	public boolean testContains(BitSet myItemSet, BitSet itemset0) {

		BitSet T11 = new BitSet();

		T11 = (BitSet) itemset0.clone();
		T11.andNot(myItemSet);

		if (T11.isEmpty()) {
			return true;
		}
		return false;

	}

	public boolean isEqualItemSet(TItemSet itemset0) {
		// TODO

		if (items3.equals(itemset0.getBitItemSet()))
			return true;
		else
			return false;
	}

	public boolean containsItemSet2(Integer item) {
		// TODO
		if (items3.get(item) == true)
			return true;
		else
			return false;
	}

	public String toString2() {
		StringBuilder string = new StringBuilder();

		string.append(items3);
		string.append(" ");

		return string.toString();
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////
	public TItemSet(TItemSet itemset0) {

		this.items2 = itemset0.getListItems();
		Collections.sort(items2);
	}

	public TItemSet(List<Integer> items) {
		Collections.sort(items);
		this.items2 = items;
		this.items = new Integer[items.size()];
		this.items = items.toArray(this.items);
		this.offset = 0;
	}

	public List<Integer> getListItems() {

		return items2;
	}

	public TItemSet(List<Integer> items0, Integer item) {
		List<Integer> itemSet = new ArrayList<Integer>();
		itemSet.addAll(items0);
		itemSet.add(item);
		Collections.sort(itemSet);
		this.items2 = itemSet;
		this.offset = 0;
	}

	@Override
	public String toString() {
		StringBuilder string = new StringBuilder();

		for (Integer item : items2) {
			string.append(item.intValue());
			string.append(" ");
		}
		return string.toString();
	}

}
