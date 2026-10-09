/*
 * This file is part of the DiSPaLe project (https://gitlab.com/phdhien/dispale)
 *
 * Licensed under the MIT license.
 *
 * See LICENSE file in the project root for full license information.
 */
package fr.phdhien.dispale;

/** builds the sub-pattern selector named on the command line (-sel); a class rather than a static
 *  interface method, because Scala 2.11 cannot call static methods of Java interfaces */
public final class SubPatternSelectors {
	private SubPatternSelectors() {}

	public static SubPatternSelector create(String name, double redundancyWeight, double maxOverlap, double minGain) {
		switch(name.toLowerCase()) {
			case "top":           return new SubPatternSelector.Top();
			case "complementary":
			case "mmr":           return new SubPatternSelector.Mmr(redundancyWeight);
			case "coverage":      return new SubPatternSelector.Coverage(maxOverlap);
			case "gain":          return new SubPatternSelector.PartitionGain(minGain);
			case "pairs":         return new SubPatternSelector.PairCoverage(minGain);
			default: throw new IllegalArgumentException("unknown selection rule: " + name
					+ " (expected top, complementary, coverage, gain or pairs)");
		}
	}
}
