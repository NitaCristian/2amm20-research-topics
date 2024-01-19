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

import java.util.BitSet;

/**
 * @author Arnold Hien
 */
public class TTransactionSet {


	private BitSet ListTransaction;
	public TTransactionSet() {

		this.ListTransaction = new BitSet();
	}
	public TTransactionSet(BitSet list) {

		this.ListTransaction = (BitSet) list.clone();
	}

	public BitSet getListTransactions() {
		return ListTransaction;

	}

	@Override
	public String toString() {
		StringBuilder string = new StringBuilder();

		
			string.append(ListTransaction);
			string.append(" ");
		
		return string.toString();
	}

}
