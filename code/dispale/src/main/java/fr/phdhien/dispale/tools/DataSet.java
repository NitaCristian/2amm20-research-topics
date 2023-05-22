package fr.phdhien.dispale.tools;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

public class DataSet {
	private BitSet[] VerticalDataBase;
	private List<TItemSet> HorizontalBase;

	public Set<Integer> uniqueItems = new HashSet<Integer>();
	private int maxItem = 0;
	private int minItem = Integer.MAX_VALUE;
	//private int nbrVar = 0;
	int i = 0;
	TTransactionSet AllTransactions;
	
	double borneLB; //lobnury
	double borneUB; //lobnury -- 09-10-2019
	public IncrementCovers covers; //lobnury
	public IncrementCovers coversBorne; // lobnury -- 09-10-2019
	
	public DataSet(String DataObjectSetPath) throws IOException {

		covers = new IncrementCovers(this); //lobnury
		coversBorne = new IncrementCovers(this); // lobnury
		borneLB = 1; //lobnury
		borneUB = 0; // lobnury
		
		HorizontalBase = new ArrayList<TItemSet>();
		BufferedReader br = new BufferedReader(new FileReader(DataObjectSetPath));
		String items;
		while ((items = br.readLine()) != null) { // iterate over the lines to
													// build the transaction
			if (items.equals("[EOF]"))
				break;
			// if the line is a comment, is empty or is metadata
			if (items.isEmpty() == true || items.charAt(0) == '#' || items.charAt(0) == '%' || items.charAt(0) == '@') {
				continue;
			}

			HorizontalBase.add(createTransaction(items));
		}
		br.close();

		/// sort transactions by increasing last item (optimization)
		// Collections.sort(HorizontalBase, new Comparator<TItemSet>() {
		// public int compare(TItemSet arg0, TItemSet arg1) {
		// // return arg0.getItems().length - arg1.getItems().length;
		// return arg0.getListItems().get(arg0.getListItems().size() - 1)
		// - arg1.getListItems().get(arg1.getListItems().size() - 1);
		// }
		// });

		////////////////////////////////////// representation
		////////////////////////////////////// vertical///////////////////////

		// nombre de variables
		// nbrVar = maxItem - minItem + 1;
		
		VerticalDataBase = new BitSet[getNbrVar()];
		for (int item = 0; item < getNbrVar(); item++) {
			VerticalDataBase[item] = new BitSet();
		}

		BitSet Transactions = new BitSet();
		for (i = 0; i < HorizontalBase.size(); i++) {
			TItemSet itemSet = HorizontalBase.get(i);
			Transactions.set(i);
			
			for (Integer item : itemSet.getListItems()) {
				// for each item get its bucket and add the current transaction

				if (minItem == 1) {
					VerticalDataBase[item - 1].set(i);

				} else {
					VerticalDataBase[item - 1].set(i);
				}
			}

		}
		
		AllTransactions = new TTransactionSet(Transactions);
	}

	public List<String> getTokensWithCollection(String str) {
		return Collections.list(new StringTokenizer(str, " ")).stream().map(token -> (String) token)
				.collect(Collectors.toList());
	}

	// ------ MinArray function
	public Integer minArrayListComparator(List<Integer> listOfIntegers) {
		return listOfIntegers.stream().mapToInt(v -> v).min().orElseThrow(NoSuchElementException::new);
	}

	// ------ MaxArray function
	public Integer maxArrayListComparator(List<Integer> listOfIntegers) {
		return listOfIntegers.stream().mapToInt(v -> v).max().orElseThrow(NoSuchElementException::new);
	}

	// -------------------------

	/**
	 * Create a transaction object from a line from the input file
	 * 
	 * @param line
	 *            a line from input file
	 * @return a transaction
	 */
	private TItemSet createTransaction(String line) {

		///////////////////////////////////////////// build the
		///////////////////////////////////////////// items//////////////////////////////////////////

		// Aribi
		List<Integer> itemsSorted = new ArrayList<Integer>();
		// getTokensWithCollection(line).forEach(System.out::println);
		getTokensWithCollection(line).forEach(elt -> {
			itemsSorted.add(Integer.parseInt(elt));
			uniqueItems.add(Integer.parseInt(elt));
		});

		// Pattern splitPattern = Pattern.compile(" ");
		// String[] items = splitPattern.split(line);
		//
		// for (int i = 0; i < items.length; i++) {
		// Integer item = Integer.valueOf(items[i]);
		// itemsSorted.add(item);
		// uniqueItems.add(item);
		// }

		/////////////////////////////// update max item by checking the last
		/////////////////////////////// item of the
		/////////////////////////////// transaction//////////////////////////
		// Aribi: La list peut ne pas etre ordonnÃ©e
		// NommÃ© itemsSorted mais pas triÃ©!!!
		// int lastItem = itemsSorted.get(itemsSorted.size() - 1);
		int lastItem = maxArrayListComparator(itemsSorted);
		if (lastItem > maxItem) {
			maxItem = lastItem;
		}
		// Aribi Ici aussi
		// int firstItem = itemsSorted.get(0);
		int firstItem = minArrayListComparator(itemsSorted);
		if (minItem > firstItem) {
			minItem = firstItem;
		}
		return new TItemSet(itemsSorted);
	}

	public List<TItemSet> getObjectTransactions() {
		return HorizontalBase;
	}

	public Set<Integer> getUniqueItems() {
		return uniqueItems;
	}

	public int getMaxItem() {
		return maxItem;
	}

	public int getNbrVar() {
//		return nbrVar;
		return maxItem;
	}

	public int getTransactionsSize() {
		return HorizontalBase.size();
	}

	@Override
	public String toString() {
		StringBuilder DataObjectSetContent = new StringBuilder();

		for (TItemSet transaction : HorizontalBase) {
			DataObjectSetContent.append(transaction);
			DataObjectSetContent.append("\n");
		}
		return DataObjectSetContent.toString();
	}

	public BitSet[] getVerticalDataBase() {
		return VerticalDataBase;
	}

	public List<TItemSet> getHorizontalDataBase() {
		return HorizontalBase;
	}

	public TTransactionSet getAllTransactions() {
		return AllTransactions;
	}
	
	public double getBorneLB() {
		return borneLB;
	}

	public void setBorneLB(double bornLB) {
		this.borneLB = bornLB;
	}

	public IncrementCovers getCovers() {
		return covers;
	}

	public void setCovers(IncrementCovers covers) {
		this.covers = covers;
	}
	
	//*
	public IncrementCovers getCoversBorne() {
		return coversBorne;
	}

	public void setCoversBorne(IncrementCovers coversBorne) {
		this.coversBorne = coversBorne;
	}
	//*/
	
}
