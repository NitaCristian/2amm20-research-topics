package fr.phdhien.dispale.tools;

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
