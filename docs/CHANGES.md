# Changes relative to the published DiSPaLe code

Reference: commit `2cf78bd` (the published code plus a `.gitignore`). This file lists every change that
affects results, how to undo it, and what it does to the numbers. The experiments are in
`scripts/multi-disc-experiment.py`; the results are in [RESULTS.md](RESULTS.md).

## 1. Corrections of the published code

Each correction is on by default. Each one that can change the result in our setting (Items features, Flexics
sampler, SCD learner) can be undone with `-lg <name>` (`-lg all` undoes them all), so its effect can be
measured on its own (experiment `fidelity`).

| `-lg` name | What the published code did | Effect |
|---|---|---|
| `nolearnedsampling` | `run_learning` assigned the learned weights to a local `val learnedWeight`, so the field passed to the sampler was never updated: **DiSPaLe always sampled with the initial weights** (with weights 1 and tilt 10, almost uniformly). LetSIP was not affected. | The learned model never chose the queries. |
| `stalez` | SCD resumed from the inner products w·x of the previous iteration, although the sub-pattern features had been added and the weights folded since then. | Wrong gradients from the second iteration on. |
| `weightindex` | The weight transferred to the items was read at index `allFeatures.size` (the number of feature *types*, e.g. 1) instead of after the base features: it transferred the weight of item 1, not the sub-pattern's. | The fold used an unrelated weight. |
| `icvint` | The mean ranks of the ICV were accumulated in an `int` and truncated. | Wrong ICVs, so sometimes a different sub-pattern. |
| `coverinplace` | The cover of Y ∪ {i} was computed by intersecting the stored cover of Y in place, which shrank Y's cover for its later extensions. | Wrong covers and ICVs for longer sub-patterns. |
| `nodedupe` | A sub-itemset reachable from several parents was evaluated once per parent (combined with `coverinplace`, with different covers). | Wasted time; possibly different ICVs. |

**Verification.** With `-lg all`, the current code reproduces a run of the published code
(`results/compare/baseline-original.log`: chess, minimum frequency 0.5, k = 5, 10 iterations) exactly: the
same queries, the same user scores, the same sub-pattern in every iteration, and the same learned weights
(largest difference 0.0). The only difference is one logged column, the model score of each shown pattern,
which the published code computed with the never-updated weights.

Corrections that cannot change the results in our setting, so they have no switch:

- the item index used when folding was wrong when Items is not the first feature type (we only use Items);
- the stored pairs could only grow by one feature (wrong with frequency/length features or m > 1);
- the launcher crashed when `-s` was given (so every run used the default seed), and passed `-ag` as a Python
  list when it was given (`nargs=1`): the program received `['LIN']`, which is not `lin`, so an explicit
  `-ag LIN` ran EXP (without `-ag`, LIN was used as intended);
- `GaussianRanker` crashed after reading one weight per item.

A dead check (`!subItemsets.contains(new_subItemset)`, always true) was removed.

## 2. Design choices: published defaults kept

The defaults are those of the published code; everything else is opt-in.

| Option | Default (published) | Alternatives | Note |
|---|---|---|---|
| `-iw` initial weight | 1 | any, e.g. 0 | The paper states 0. With 1, the initial score of a pattern is its length. |
| `-ag` folding rule | LIN, w(1 + ηd) | EXP, ADD (w + ηd) | LIN and EXP cannot move a weight that is exactly 0. |
| `-cw` start of a re-selected sub-pattern | memory (its last learned weight) | zero | That weight was already folded into the items; `zero` avoids counting it twice. |
| `-ch` sub-pattern features of earlier pairs | none (0) | full (recomputed) | |
| `-sw` SCD sweeps per iteration | 1 | any | |
| `-la` L1 penalty (×(t+1)) | 1e-6 | any | |

LetSIP now starts SCD from inner products recomputed from the current weights, as DiSPaLe does. For LetSIP
this is the same computation as before (its features and weights do not change between iterations), up to
floating-point rounding.

## 3. Our extension (multiple sub-patterns)

- `-nd m`: up to m sub-patterns per iteration. Every sub-itemset with ICV > 0 is a candidate. Sub-itemsets
  with the same cover in the query split it identically, so only one per cover is kept (the longest, then the
  first found). The candidates are sorted by ICV, then length, then enumeration order.
- The first candidate is, by construction, the sub-pattern the original top-1 rule returns on the same
  search. `BestICVSubset` recomputes the original rule and checks this in every iteration (trace field
  `top1_matches`, warning on stderr); with m = 1 every selection rule therefore returns the corrected
  DiSPaLe sub-pattern.
- `-sel`: `top` (highest ICV, distinct covers), `mmr` (ICV discounted by Jaccard similarity: *diversity*),
  `coverage` (skips sub-patterns whose cover is correlated with a selected one), `gain` (partition gain:
  *complementarity*, the default), `pairs` (pair coverage, monotone submodular). The former name
  `complementary` for MMR was dropped: MMR measures diversity, not complementarity.
- `-fx separate|pooled`: one feature per sub-pattern, or one pooled feature (built per iteration, no global
  state).
- `-kp fold|keep`: `fold` (default, as published) adds the sub-pattern features for one iteration and folds
  their weights into the items; `keep` turns every selected sub-pattern into a permanent presence feature,
  learned from all pairs collected so far and never folded, so the model (items + kept sub-patterns) can
  represent a preference for items *together*. The sampler then uses that model.
- `-tn fixed|selected|none`: the transferred weight is divided by the requested m (`fixed`, default), by the
  number selected in that iteration (`selected`), or not at all.

## 4. Bug found in our own extension

Until this review, the stored pairs of the *current* iteration were not stripped of their sub-pattern columns
at the end of the iteration. With `-ch none`, the next iteration then read those values as the values of its
own (different) sub-patterns. The published code, with one sub-pattern feature, dropped that column when
adding the next one, so it was not affected. Every DiSPaLe result obtained before this review
(`results/archive-before-review/`) is affected and should not be used.

## 5. Evaluation changes

- Every run writes a structured trace (`-tr`); the evaluation reads only the traces (no more parsing of
  stdout lines).
- The test patterns shown to the user in a run are excluded when that run is evaluated.
- The combination users are drawn at random from the data (seeded; pairs contained in 5-25% of the test
  patterns), two per dataset, on three datasets. The chess pairs used before (29,52;40,58) were picked by
  hand from pair frequencies.
- New metrics: accuracy on the pairs that differ in the number of liked combinations (`acc-combo`, ignores
  the surprisingness tie-break), tie-aware P@10%, the model before folding, a `score = length` baseline,
  paired permutation tests, and the mean number of candidates tied at the highest ICV.
- Timing: experiment `timing`, runs executed one at a time, per-step wall-clock times from the trace.
