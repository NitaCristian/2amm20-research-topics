# Results

All numbers come from `scripts/multi-disc-experiment.py` (see its docstring for the protocol) on the code
described in [CHANGES.md](CHANGES.md). Query size k = 10, 20 iterations, Items features, Flexics sampler,
SCD learner, published defaults (initial weights 1, LIN folding). Mean ± standard deviation; p values are
paired sign-flip permutation tests over (dataset, user, seed). To reproduce:

    python3 scripts/multi-disc-experiment.py run  --xp fidelity    # then main, learner, keep, timing
    python3 scripts/multi-disc-experiment.py eval --xp keep [--ref m1-keep]
    python3 scripts/multi-disc-experiment.py timing --xp timing
    python3 scripts/multi-disc-experiment.py check-user

Metrics: `acc` pairwise accuracy on all test pairs; `acc-combo` accuracy on the pairs that differ in the
number of liked item pairs (combination users); `P@10%` tie-aware precision among the top 10%.

## 1. Fidelity: the published code and each correction (surprisingness user, chess + soybean, 5 seeds each)

| config | acc | p vs published | P@10% |
|---|---|---|---|
| score = length (no learning) | 64.4 / 66.4 (chess / soybean) | | 26.0 / 17.0 |
| LetSIP | 71.8 | 0.10 | 32.9 |
| DiSPaLe, published (`-lg all`) | 71.1 | – | 30.7 |
| + fix `stalez` only | 71.7 | 0.002 | 33.2 |
| + fix `weightindex` only | 71.9 | 0.002 | 33.5 |
| + fix `nolearnedsampling` only | 71.3 | 0.27 | 31.6 |
| + fix `icvint` only | 71.1 | 0.66 | 30.5 |
| + fix `nodedupe` only | 71.1 | 1.00 | 30.7 |
| + fix `coverinplace` only | 69.4 | 0.004 | 27.6 |
| all fixes | 72.0 | 0.020 | 32.5 |
| all fixes, ADD | 72.1 | 0.023 | 32.9 |
| all fixes, initial weights 0, LIN | 66.9 | 0.004 | 32.5 |
| all fixes, initial weights 0, ADD | 65.3 | 0.002 | 31.3 |

The corrections together gain about one point (p = 0.02); the stale inner products and the wrong weight
index matter most. Fixing the covers alone makes things worse, an interaction with the other errors that
disappears once all are fixed. Initial weights 0 cost 5-7 points: with them DiSPaLe barely beats the
length baseline, so the published defaults (weights 1, LIN) are kept. Learning adds only 5-7 points over
`score = length` on this user.

## 2. Main: multiple sub-patterns (combination users, chess + mushroom + soybean, 2 users × 5 seeds each)

All datasets together (30 runs per config); p against DiSPaLe m = 1:

| config | acc | acc-combo | P@10% | p (acc-combo) |
|---|---|---|---|---|
| LetSIP | 77.0 | 91.9 | 78.1 | 0.75 |
| DiSPaLe m = 1 | 77.0 | 92.0 | 78.4 | – |
| m = 3, top | 77.0 | 92.1 | 78.2 | 0.78 |
| m = 3, mmr | 77.0 | 92.0 | 78.3 | 0.94 |
| m = 3, coverage | 77.0 | 92.0 | 78.4 | 0.93 |
| m = 3, gain | 77.0 | 92.0 | 78.4 | 0.99 |
| m = 3, pairs | 77.0 | 92.0 | 78.4 | 0.95 |
| m = 3, gain, pooled | 77.0 | 91.8 | 78.2 | <0.001 (worse) |
| m = 3, gain, `-cw zero` | 77.0 | 92.0 | 78.3 | 0.93 |

With the published fold-and-discard scheme, no selection rule changes the learned model. The learned
sub-pattern weights stay at 0.01-0.06 against about 0.9 for the items, and the model before folding equals
the model after folding. The selection rule therefore has nothing to act on.

## 3. Learner: 10 SCD sweeps instead of 1 (combination users, chess)

| config | acc | acc-combo | P@10% | \|w sub-pattern\| |
|---|---|---|---|---|
| LetSIP, 10 sweeps | 75.3 | 91.6 | 91.4 | – |
| m = 1, 10 sweeps | 75.0 | 91.8 | 92.9 | 0.44 |
| m = 3 gain, 10 sweeps | 75.1 | 91.9 | 91.8 | 0.21 |
| m = 3 gain, 10 sweeps, full history | 75.5 | 91.7 | 90.7 | 0.16 |

More sweeps give the sub-pattern features real weight, but the final model is still items only after
folding, accuracy drops by about 1.5 points for every method (overfitting the few pairs), and the rules do
not differ.

## 4. Keep: sub-patterns as permanent features (`-kp keep`)

All datasets together; first p against DiSPaLe m = 1 (fold), second against m = 1 keep:

| config | acc | acc-combo | P@10% | p vs m1 | p vs m1-keep (acc-combo / P@10%) |
|---|---|---|---|---|---|
| DiSPaLe m = 1 (fold) | 77.0 | 92.0 | 78.4 | – | |
| m = 1, keep | 78.5 | 94.6 | 86.4 | <0.001 | – |
| m = 3 top, keep | 78.8 | 95.3 | 88.3 | <0.001 | 0.024 / 0.096 |
| m = 3 gain, keep | 78.5 | 94.9 | 86.5 | <0.001 | 0.25 / 0.92 |
| m = 3 gain, keep, 10 sweeps | 78.7 | 96.0 | 90.0 | <0.001 | 0.019 / 0.005 |

Per dataset (acc-combo / P@10%):

| config | chess | mushroom | soybean |
|---|---|---|---|
| score = length | 84.3 / 67.5 | 79.5 / 50.0 | 80.4 / 50.5 |
| LetSIP | 94.0 / 93.4 | 90.7 / 68.5 | 91.0 / 72.5 |
| DiSPaLe m = 1 (fold) | 94.5 / 93.6 | 90.5 / 67.7 | 91.0 / 73.8 |
| m = 3 gain (fold) | 94.2 / 93.3 | 90.6 / 68.8 | 91.2 / 73.1 |
| m = 1, keep | 97.2 / 98.8 | 92.8 / 74.2 | 93.9 / 86.2 |
| m = 3 top, keep | 97.8 / 99.3 | 93.8 / 79.3 | 94.3 / 86.4 |
| m = 3 gain, keep | 97.4 / 99.3 | 93.6 / 77.8 | 93.8 / 82.4 |
| m = 3 gain, keep, 10 sweeps | 98.4 / 99.4 | 93.6 / 78.9 | 96.1 / 91.8 |
| offline, items only | 94.9 / 99.5 | 91.0 / 69.0 | 93.0 / 78.0 |
| offline, items + true liked pairs | 99.3 / 100 | 97.1 / 87.0 | 98.8 / 96.0 |

(Offline: pairwise logistic regression on all pairs of 2000 other random patterns, averaged over the two
users; `check-user`.)

Keeping the sub-patterns is what makes them useful. On every dataset, every kept variant beats the fold
scheme and LetSIP on acc-combo, and exceeds the offline *items-only* reference on acc-combo, which no
fold-based model does (on chess P@10% the items-only reference, 99.5, is already near the maximum); with 10
sweeps it approaches the reference that has the true liked pairs as features. Selecting several sub-patterns
per iteration helps on top of keeping them only modestly: `top` and `gain + 10 sweeps` beat `m = 1, keep`
on acc-combo (p ≈ 0.02), `gain` alone does not. These p values are not corrected for the six tests (three
configs × two metrics); with Holm's correction only the 10-sweep P@10% gain (p = 0.005) stays below 0.05. Under `keep`, the
simple `top` rule (distinct covers) works at least as well as partition gain.

## 5. Cost (chess, one combination user, 2 seeds, runs executed one at a time)

| config | sample | mine | select | learn | fold | candidates (distinct covers) |
|---|---|---|---|---|---|---|
| LetSIP | 2585 ms | – | – | 12 ms | – | – |
| m = 1 | 2605 | 35 | 2.8 | 10 | 1.0 | 70 |
| m = 3 top | 2603 | 35 | 2.5 | 10 | 1.2 | 70 |
| m = 3 mmr | 2681 | 35 | 2.9 | 10 | 1.3 | 69 |
| m = 3 coverage | 2594 | 38 | 2.7 | 10 | 1.3 | 69 |
| m = 3 gain | 2591 | 36 | 2.9 | 10 | 1.2 | 70 |
| m = 3 pairs | 2547 | 37 | 2.7 | 10 | 1.3 | 69 |

Sampling dominates (about 2.6 s per iteration); the sub-pattern search costs about 35 ms and every
selection rule under 3 ms, so selecting several sub-patterns adds no measurable cost at k = 10.

## 6. Variety of the shown patterns

Over the last 10 iterations, every config shows the same number of distinct items, the same share of the 5
most shown items and the same similarity between shown patterns (e.g. chess: 31.7-32.2 items, 31% top-5
share, Jaccard 0.24). No variant narrows what is shown more than LetSIP does.

## 7. Limitations

One query size (k = 10), 20 iterations, Items features only, simulated users only. The transfer
normalisation (`-tn`) was not ablated: under folding the transferred sub-pattern weights are 0.01-0.06, so
the divisor cannot matter, and under `keep` nothing is transferred. The test set is random
frequent itemsets grown item by item, not the Flexics distribution. The combination users are synthetic and
have exactly the structure that sub-pattern features can express. Several comparisons are reported; apart
from the large keep-vs-fold effect, the p values between close variants should be read with that in mind.
