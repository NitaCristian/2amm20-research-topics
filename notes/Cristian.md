# Two bugs in the ICV code to fix before comparing

Both affect which sub-pattern gets picked. Fix them in the baseline first, otherwise your comparison is against a buggy DiSPaLe.

## Integer division in the mean 

(BestICVSubset.java:69-78): avg stores the sum in an int, so the average is rounded down. ICV values are distorted and ties become more common.

## Covers are overwritten during enumeration 

(BestICVSubset.java:165-167): cov = tID.get(sub) gets the stored object itself, not a copy, and cov.and(...) then changes it in place. After the first extension of sub, its stored cover is wrong, so later extensions get wrong covers and wrong ICVs. Using (BitSet) tID.get(sub).clone() fixes it.


## Algorithm 

The DiSPaLe algorithm lives mainly in Dispale.scala. It builds on LetSIP.scala and replaces LetSIP's main loop and learning step.

Core of the algorithm, all in code/dispale/src/main/:

Main interactive loop (sample k patterns, user ranks them, learn, repeat)	loop() in Dispale.scala:97
One learning step: find the sub-pattern, add it as a feature, learn, remove it	run_learning() in Dispale.scala:151
Hands the k ranked patterns to the ICV search	mine_discriminating_patterns() in Dispale.scala:313
ICV computation and search for the best sub-pattern	BestICVSubset.java (interclassVariance, enumerateItemsets)
Adds or removes the temporary feature	Dispale.scala:444 and Dispale.scala:487
Spreads the sub-pattern's weight over its items (-ag lin/exp, -e eta)	update_weight_list() in Dispale.scala:376 and aggregation_value() in Dispale.scala:369

Sampling the k patterns (shared with LetSIP): mining/Miner.scala
Discriminant feature types: Patterns.scala, DiscriminativeFrequency.scala and DiscriminativeLength.scala in feature/
Simulated user: Ranker.scala
SCD weight learner: SCD.scala, in the separate scd module
Choosing the method from -m: MethodSelector.scala

## Where should be put the changes

For your multi-sub-pattern idea, you'd mainly change BestICVSubset.java and the add/learn/remove steps in Dispale.scala.

Yes, DiSPaLe goes back and forth, but the "user" in this code is a simulated one, not you. 


1. What ICV is
ICV is inter-class variance. After the user ranks the k patterns, each one has a rank from 0 (best) to k−1 (worst). Take a candidate sub-pattern S, for example (A,B), and split the k patterns into two groups:

covered: patterns that contain S
uncovered: patterns that don't
ICV measures how far apart the average ranks of the two groups are (BestICVSubset.java:81-88):


ICV(S) = n_cov · (μ − μ_cov)²  +  n_unc · (μ − μ_unc)²
Here μ is the average rank of all k patterns, μ_cov and μ_unc are the average ranks of each group, and n_cov and n_unc are the group sizes.

You are not the user in this code. user is a simulated user. The default is SurprisingnessRanker (Ranker.scala:162). It ranks patterns by how much more often they occur than you'd expect if their items were independent. This is the usual way to evaluate interactive mining: a known hidden "taste" lets you measure how fast the learner recovers it. There is no interface for a real person to rank patterns. If you wanted that, you'd write a Ranker that prints the k patterns and reads your ranking from the keyboard.

For your research question this also gives you a way to test: build a simulated user who likes two independent combinations, e.g. a GaussianRanker-style user that rewards (A,B) and (C,D) separately. Then check whether m > 1 recovers that faster than single-sub-pattern DiSPaLe.



