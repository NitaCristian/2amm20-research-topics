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
/**
 * this class represent an item and its utility in a transaction
 * 
 * @author Maxime Garfagni
 * @author Arnold Hien
 */
public class Pair {
	/** an item */
	public int item = 0;
	
	/** the utility of the item */
	public double utility = 0;
}
