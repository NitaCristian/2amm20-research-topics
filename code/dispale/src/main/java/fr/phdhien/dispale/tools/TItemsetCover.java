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

/**
 * @author Arnold Hien
 */
public class TItemsetCover {
	 TItemSet itemSet;
	 TTransactionSet transactions;

	TItemsetCover(TItemSet itemset, TTransactionSet coverture) {
		this.itemSet = itemset;
		this.transactions = coverture;
	}

	public String toString() {
		StringBuilder string = new StringBuilder();

		string.append(itemSet);
		string.append(" ");
		string.append(transactions);
		return string.toString();

	}

	public TItemSet getItemSet() {
		return itemSet;
	}

	public TTransactionSet getTransactions() {
		return transactions;
	}
}
