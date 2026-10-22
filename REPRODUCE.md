# Reproducing the experiments

This file lists the commands behind every table and figure of the report
*Interactive Pattern Mining with Multiple Complementary Dynamic Features*.
Run them from the repository root.

## Requirements

- Java 11 and Python 3 (no extra Python packages are needed).
- About 6 hours on a laptop for all runs (6 runs in parallel, see `PARALLEL` in
  `scripts/multi-disc-experiment.py`). Chess is by far the slowest dataset.

## 1. Build

```bash
python3 scripts/build-code.py -fb
```

## 2. Run the experiments

Each run simulates a user who ranks 10 patterns per iteration for 20 iterations and writes
a structured trace to `results/xp-<experiment>/<dataset>/<user>/<config>/seedN.trace.jsonl`.
Runs are seeded; finished runs are skipped, so a command can be restarted after an interruption.

```bash
X=scripts/multi-disc-experiment.py
python3 $X run --xp fidelity   # published code vs. each correction (surprisingness user, chess + soybean)
python3 $X run --xp main       # combination users, folding: LetSIP, m = 1 and every selection rule
python3 $X run --xp learner    # folding with 10 SCD sweeps per iteration (chess)
python3 $X run --xp keep       # keeping the sub-patterns, incl. the 10-sweep baselines (shares results/xp-main)
python3 $X run --xp timing     # per-step timings; runs one at a time, so run it alone on the machine
```

## 3. Produce the numbers

```bash
python3 $X eval --xp fidelity                  # table: published code vs. corrections
python3 $X eval --xp keep                      # tables: all configurations, per dataset and pooled (p vs. fold m = 1)
python3 $X eval --xp keep --ref m1-keep        # p-values against keep, m = 1
python3 $X eval --xp keep --ref m1-keep-sw10   # table: m = 1 vs. m = 3 under one and ten sweeps
python3 $X eval --xp learner                   # sub-pattern weights and accuracy with 10 sweeps
python3 $X check-user                          # offline references (items only / items + liked pairs)
python3 $X timing --xp timing                  # cost per step
python3 $X variety --xp keep                   # diversity of the shown patterns
python3 $X curve --xp keep                     # figure: acc-combo per iteration (pgfplots coordinates)
python3 $X tastes                              # the randomly drawn combination users
```

All accuracies and p-values are reproduced exactly; only the timings depend on the machine.

## 4. The running example

```bash
python3 scripts/worked-example.py results/xp-main/soybean/taste1/m3-gain/seed3.trace.jsonl \
        -it 0 1 2 --liked "29,37;33,45"
python3 scripts/trace-view.py results/xp-main/soybean/taste1/m3-gain/seed3.trace.jsonl -it 0
```

## 5. Checking the corrections against the published code

With `-lg all`, every correction of the published implementation is undone, and the program reproduces
the published code exactly (same queries, sub-patterns and learned weights):

```bash
python3 scripts/test-program.py -m dispale -d chess -f 0.5 -k 5 -i 10 -o flexics -a eflexics \
        -F Items -l 0 -to 300 -lg all
```

Individual corrections can be undone with `-lg icvint,coverinplace,nodedupe,stalez,nolearnedsampling,weightindex`
(any subset).
