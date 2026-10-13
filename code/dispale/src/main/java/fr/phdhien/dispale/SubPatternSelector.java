/*
 * This file is part of the DiSPaLe project (https://gitlab.com/phdhien/dispale)
 *
 * Licensed under the MIT license.
 *
 * See LICENSE file in the project root for full license information.
 */
package fr.phdhien.dispale;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fr.phdhien.dispale.BestICVSubset.Candidate;

/**
 * Selects at most m discriminant sub-patterns among the candidates of one query.
 *
 * Every rule takes the candidates sorted by decreasing ICV (ties: longer first) and the number k of
 * query patterns, whose ranks are 0 (best) .. k-1. Every rule starts with the highest-ICV candidate,
 * so m = 1 always gives the original DiSPaLe sub-pattern.
 */
public interface SubPatternSelector {

	List<Candidate> select(List<Candidate> sorted, int m, int k);

	//==================================================================================================
	// helpers
	//==================================================================================================

	static double jaccard(BitSet a, BitSet b) {
		BitSet inter = (BitSet) a.clone();
		inter.and(b);
		BitSet union = (BitSet) a.clone();
		union.or(b);
		return union.isEmpty() ? 0.0 : (double) inter.cardinality() / union.cardinality();
	}

	/** |Pearson correlation| of the 0/1 cover vectors of two candidates over the k query patterns:
	 *  1 for the same cover and for complementary covers (both split the query the same way) */
	static double coverCorrelation(BitSet a, BitSet b, int k) {
		double pa = (double) a.cardinality() / k, pb = (double) b.cardinality() / k;
		BitSet ab = (BitSet) a.clone();
		ab.and(b);
		double cov = (double) ab.cardinality() / k - pa * pb;
		double var = pa * (1 - pa) * pb * (1 - pb);
		return var <= 0 ? 1.0 : Math.abs(cov) / Math.sqrt(var);
	}

	/** between-group sum of squares of the ranks 0..k-1, the groups being the query patterns
	 *  that contain the same members of `chosen` (generalises ICV to a set of sub-patterns) */
	static double partitionICV(List<Candidate> chosen, int k) {
		Map<String, double[]> groups = new HashMap<String, double[]>(); // signature -> {count, sum of ranks}
		for(int r = 0; r < k; r++) {
			StringBuilder sig = new StringBuilder();
			for(Candidate c : chosen)
				sig.append(c.cover.get(r) ? '1' : '0');
			double[] g = groups.computeIfAbsent(sig.toString(), s -> new double[2]);
			g[0] += 1;
			g[1] += r;
		}
		double mu = (k - 1) / 2.0, ss = 0;
		for(double[] g : groups.values())
			ss += g[0] * Math.pow(mu - g[1] / g[0], 2);
		return ss;
	}

	/** total sum of squares of the ranks 0..k-1: the largest possible partitionICV */
	static double totalSS(int k) {
		double mu = (k - 1) / 2.0, ss = 0;
		for(int r = 0; r < k; r++)
			ss += (r - mu) * (r - mu);
		return ss;
	}

	//==================================================================================================
	// rules
	//==================================================================================================

	/** the m highest-ICV candidates, no redundancy check */
	class Top implements SubPatternSelector {
		public List<Candidate> select(List<Candidate> sorted, int m, int k) {
			return new ArrayList<Candidate>(sorted.subList(0, Math.min(m, sorted.size())));
		}
	}

	/** maximal marginal relevance: ICV * (1 - rw * max redundancy), redundancy being the larger
	 *  Jaccard index of the query covers and of the items */
	class Mmr implements SubPatternSelector {
		final double rw;
		Mmr(double rw) { this.rw = rw; }

		public List<Candidate> select(List<Candidate> sorted, int m, int k) {
			List<Candidate> picked = new ArrayList<Candidate>();
			picked.add(sorted.get(0));
			while(picked.size() < m) {
				Candidate best = null;
				double bestScore = 0;
				for(Candidate c : sorted) {
					if(picked.contains(c))
						continue;
					double maxRed = 0;
					for(Candidate s : picked)
						maxRed = Math.max(maxRed, Math.max(jaccard(c.cover, s.cover), jaccard(c.items, s.items)));
					double score = c.icv * (1 - rw * maxRed);
					if(score > bestScore) {
						bestScore = score;
						best = c;
					}
				}
				if(best == null)
					break;
				picked.add(best);
			}
			return picked;
		}
	}

	/** coverage constraint: take candidates by decreasing ICV, skipping any whose cover is correlated
	 *  (|corr| > maxOverlap) with the cover of an already selected sub-pattern */
	class Coverage implements SubPatternSelector {
		final double maxOverlap;
		Coverage(double maxOverlap) { this.maxOverlap = maxOverlap; }

		public List<Candidate> select(List<Candidate> sorted, int m, int k) {
			List<Candidate> picked = new ArrayList<Candidate>();
			for(Candidate c : sorted) {
				if(picked.size() >= m)
					break;
				boolean ok = true;
				for(Candidate s : picked)
					if(coverCorrelation(c.cover, s.cover, k) > maxOverlap) {
						ok = false;
						break;
					}
				if(ok)
					picked.add(c);
			}
			return picked;
		}
	}

	/** partition gain (greedy forward selection): add the candidate that most increases the
	 *  between-group sum of squares of the ranks; stop when the gain is at most minGain x total */
	class PartitionGain implements SubPatternSelector {
		final double minGain;
		PartitionGain(double minGain) { this.minGain = minGain; }

		public List<Candidate> select(List<Candidate> sorted, int m, int k) {
			List<Candidate> picked = new ArrayList<Candidate>();
			picked.add(sorted.get(0));
			double current = partitionICV(picked, k), threshold = minGain * totalSS(k);
			while(picked.size() < m) {
				Candidate best = null;
				double bestGain = 0;
				for(Candidate c : sorted) {
					if(picked.contains(c))
						continue;
					picked.add(c);
					double gain = partitionICV(picked, k) - current;
					picked.remove(picked.size() - 1);
					if(gain > bestGain + 1e-12) {
						bestGain = gain;
						best = c;
					}
				}
				if(best == null || bestGain <= threshold)
					break;
				picked.add(best);
				current += bestGain;
			}
			return picked;
		}
	}

	/** pair coverage (monotone submodular): a sub-pattern explains a pair of query patterns when exactly
	 *  one of the two contains it; maximise the rank distance summed over pairs explained by at least one
	 *  selected sub-pattern. Seeded with the highest-ICV candidate; stop when the gain is at most
	 *  minGain x total pair weight */
	class PairCoverage implements SubPatternSelector {
		final double minGain;
		PairCoverage(double minGain) { this.minGain = minGain; }

		public List<Candidate> select(List<Candidate> sorted, int m, int k) {
			boolean[][] explained = new boolean[k][k];
			List<Candidate> picked = new ArrayList<Candidate>();
			picked.add(sorted.get(0));
			mark(sorted.get(0), explained, k);
			double total = 0;
			for(int a = 0; a < k; a++)
				for(int b = a + 1; b < k; b++)
					total += b - a;
			double threshold = minGain * total;
			while(picked.size() < m) {
				Candidate best = null;
				double bestGain = 0;
				for(Candidate c : sorted) {
					if(picked.contains(c))
						continue;
					double gain = 0;
					for(int a = 0; a < k; a++)
						for(int b = a + 1; b < k; b++)
							if(!explained[a][b] && c.cover.get(a) != c.cover.get(b))
								gain += b - a;
					if(gain > bestGain + 1e-12) {
						bestGain = gain;
						best = c;
					}
				}
				if(best == null || bestGain <= threshold)
					break;
				picked.add(best);
				mark(best, explained, k);
			}
			return picked;
		}

		private static void mark(Candidate c, boolean[][] explained, int k) {
			for(int a = 0; a < k; a++)
				for(int b = a + 1; b < k; b++)
					if(c.cover.get(a) != c.cover.get(b))
						explained[a][b] = true;
		}
	}
}
