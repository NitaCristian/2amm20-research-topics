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

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;

/**
 * @author Arnold Hien
 */
public class IncrementCovers {
	private DataSet dataSet;
	private Deque<TItemsetCover> CurrentCovers;

	IncrementCovers(DataSet data1) {
		this.dataSet = data1;
		CurrentCovers = new ArrayDeque<TItemsetCover>();
	}

	public void pushCover(TItemSet itemSet0, TTransactionSet transactionSet0) {
		CurrentCovers.push(new TItemsetCover(itemSet0, transactionSet0));
	}

	public TTransactionSet getCoverPOP(TItemSet itemSet0) {

		TTransactionSet tr0 = null;
		boolean notFound0 = true;
		//verifier si l'itemset en entrée n'est pas vide 
		if (!itemSet0.getBitItemSet().isEmpty()) {
// on boucle  tantque l'itemset n'est pas trouvé et la pile n'est pas vide 
			while (notFound0 && (!CurrentCovers.isEmpty())) {
//on prend la tete de pile 
				TItemsetCover topObject0 = CurrentCovers.peek();
				/*on verifie si l'itemset en tete de liste est egale a l'itemset en entree
				 * @return la couverture stockee en tete de pile  
				 */
				if (topObject0.itemSet.isEqualItemSet(itemSet0)) {
					tr0 = new TTransactionSet(topObject0.transactions.getListTransactions());
					notFound0 = false;
					return tr0;
				} 
				/* si l'item en tete de pile est un sous ensemble de l'itemset en entree 
				 * on recupere la couverture en tete de pile et on l'intersecte avec la couverture 
				 * des autres items de l'itemset en entree
				 */
				else if (itemSet0.subItemSet(topObject0.itemSet)) {

					TTransactionSet list = new TTransactionSet();
					BitSet DifItemSet = new BitSet();
					DifItemSet = (BitSet) itemSet0.getBitItemSet().clone();
					DifItemSet.andNot(topObject0.itemSet.getBitItemSet());
					list = new TTransactionSet(topObject0.transactions.getListTransactions());
					for (int item = DifItemSet.nextSetBit(0); item != -1; item = DifItemSet.nextSetBit(item + 1))
					 {
						list = getIntersection(list, new TTransactionSet(dataSet.getVerticalDataBase()[item]));
					}
				
					
					tr0 = list;

					pushCover(itemSet0, tr0);
					notFound0 = false;
					return tr0;
				} else
					CurrentCovers.pop();
			}
			// si l'itemset est introuvable, on calcule sa couverture
			
			if (notFound0) {
				TTransactionSet Tran = new TTransactionSet();
				int item0 = itemSet0.getBitItemSet().nextSetBit(0);
				Tran = new TTransactionSet(dataSet.getVerticalDataBase()[item0]);
				for (int item = 1; item < itemSet0.getBitItemSet().length(); item++) {
					if (itemSet0.getBitItemSet().get(item) == true) {
						Tran = getIntersection(Tran, new TTransactionSet(dataSet.getVerticalDataBase()[item]));
					}
				}
				tr0 = Tran;
				pushCover(itemSet0, tr0);
				return tr0;
			}

		} else {
			//Dans le cas ou sigma_postif est nul, on return toutes les transactions
			tr0 = dataSet.getAllTransactions();

			return tr0;
		}
		return tr0;
	}
	/**
	 * verifier si la projection d'un item par rapport a l'instanciation courante et egale a la couverture courante. 
	 * 
	 * @param coverture courante , item.
	 * @return true si on trouve la meme couverture sinon false. 
	 */
	public boolean checkSameCover(TTransactionSet coverPos, Integer ValeurItem) {
		TTransactionSet Cover = new TTransactionSet();

		Cover = getIntersection(coverPos, new TTransactionSet(dataSet.getVerticalDataBase()[ValeurItem]));
		return (sameTransacation(coverPos.getListTransactions(), Cover.getListTransactions()));
	}
	/**
	 * calcul de l'intersection  entre la couverture courante et la couverture de l'item en entree 
	 * 
	 * @param coverture courante , item
	 * @return l'intersection 
	 */
	public TTransactionSet intersectCover(TTransactionSet coverPos, Integer itemvalue) {
		BitSet T1 = new BitSet();
		BitSet T2 = new BitSet();
		T1 = (BitSet) coverPos.getListTransactions().clone();
		T2 = (BitSet) dataSet.getVerticalDataBase()[itemvalue];
		T1.and(T2);
		return new TTransactionSet(T1);

	}
	/**
	 * calcul de l'intersection des deux couvertures en entree 
	 * 
	 * @param coverture1, coverture2
	 * @return l'intersection 
	 */
	public TTransactionSet getIntersection(TTransactionSet Cover1, TTransactionSet Cover2) {
		BitSet T1 = new BitSet();
		BitSet T11 = new BitSet();
		T1 = Cover1.getListTransactions();
		T11 = (BitSet) T1.clone();
		T11.and(Cover2.getListTransactions());
		return new TTransactionSet(T11);
	};
	/**
	 * verifier si les deux coverture sont les memes
	 * 
	 * @para coverture1, coverture2
	 * @return vrai s'il sont egaux 
	 */

	public boolean sameTransacation(BitSet Cover1, BitSet Cover2) {
		return (Cover1.equals(Cover2));
	}

}
