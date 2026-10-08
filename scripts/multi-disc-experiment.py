'''
Experiment: does using several discriminating sub-patterns per iteration (-nd > 1)
improve the preference model learned by DiSPaLe?

  python3 scripts/multi-disc-experiment.py run  [--xp NAME]  # launch all runs (logs in results/xp-NAME)
  python3 scripts/multi-disc-experiment.py eval [--xp NAME]  # evaluate the learned models
  python3 scripts/multi-disc-experiment.py variety [--xp NAME]  # how varied the shown patterns are

Experiments (--xp):
  multidisc: number / selection of sub-patterns, with the original initial weights (1) and LIN aggregation
  sampling:  original initial weights (1, LIN aggregation) vs initial weights 0 (ADD aggregation)

Evaluation: the weights learned after each iteration (ITER_WEIGHTS lines, Items features only)
score a fixed test set of frequent itemsets that were never shown to the user; we measure
how well this score agrees with the simulated user (surprisingness):
  - pairwise accuracy: % of test pattern pairs ordered as the user would order them
  - precision@10%: overlap between the user's top 10% and the model's top 10%
and the quality of the patterns shown to the user:
  - mean surprisingness of the queried patterns over the last 5 iterations
'''

import argparse
import itertools
import os
import random
import re
import statistics
import subprocess
from concurrent.futures import ThreadPoolExecutor

ROOT = os.path.realpath(os.path.join(os.path.dirname(os.path.realpath(__file__)), os.pardir))
DATASETS = {"chess": 0.5}      # dataset -> minimum frequency
ITERATIONS = 20
SEEDS = [1, 2, 3, 4, 5]
OLD = ["-iw", "1", "-ag", "LIN"]  # original initial weights and aggregation
# experiment -> (query sizes, {config name -> extra arguments})
EXPERIMENTS = {
    "multidisc": ([5, 10], {
        "letsip":          ["-m", "letsip"] + OLD,
        "dispale-m1":      ["-m", "dispale", "-nd", "1"] + OLD,
        "dispale-m3-top":  ["-m", "dispale", "-nd", "3", "-sel", "top"] + OLD,
        "dispale-m3-comp": ["-m", "dispale", "-nd", "3", "-sel", "complementary"] + OLD,
        "dispale-m5-comp": ["-m", "dispale", "-nd", "5", "-sel", "complementary"] + OLD,
    }),
    "sampling": ([10], {
        "letsip-w1":          ["-m", "letsip"] + OLD,
        "letsip-w0":          ["-m", "letsip", "-iw", "0"],
        "dispale-m1-w1":      ["-m", "dispale", "-nd", "1"] + OLD,
        "dispale-m1-w0":      ["-m", "dispale", "-nd", "1", "-iw", "0", "-ag", "ADD"],
        "dispale-m3-comp-w1": ["-m", "dispale", "-nd", "3", "-sel", "complementary"] + OLD,
        "dispale-m3-comp-w0": ["-m", "dispale", "-nd", "3", "-sel", "complementary", "-iw", "0", "-ag", "ADD"],
    }),
}
XP_DIR, QUERY_SIZES, CONFIGS = None, None, None


def select_xp(name):
    global XP_DIR, QUERY_SIZES, CONFIGS
    XP_DIR = os.path.join(ROOT, "results", "xp-" + name)
    QUERY_SIZES, CONFIGS = EXPERIMENTS[name]
TEST_SET_SIZE = 1000
PARALLEL = 6


def log_path(data, k, config, seed):
    return os.path.join(XP_DIR, data, f"k{k}", config, f"seed{seed}.log")


def run_one(job):
    data, freq, k, config, seed = job
    out = log_path(data, k, config, seed)
    if os.path.exists(out) and "FINAL_WEIGHTS" in open(out).read():
        return f"skip {out}"
    os.makedirs(os.path.dirname(out), exist_ok=True)
    cmd = ["python3", os.path.join(ROOT, "scripts", "test-program.py"), "-d", data, "-f", str(freq),
           "-k", str(k), "-i", str(ITERATIONS), "-o", "flexics", "-a", "eflexics", "-F", "Items",
           "-l", "0", "-s", str(seed), "-to", "1800"] + CONFIGS[config]
    # own temp dir per run: the native library loader deletes the libraries other JVMs extracted to the temp dir
    tmp = os.path.join(XP_DIR, "tmp", f"{data}-k{k}-{config}-{seed}")
    os.makedirs(tmp, exist_ok=True)
    env = dict(os.environ, JAVA_TOOL_OPTIONS=f"-Djava.io.tmpdir={tmp}")
    with open(out, "w") as f:
        subprocess.run(cmd, cwd=ROOT, stdout=f, stderr=subprocess.STDOUT, env=env)
    return f"done {out}"


def run():
    jobs = [(d, f, k, c, s) for (d, f) in DATASETS.items() for k in QUERY_SIZES for c in CONFIGS for s in SEEDS]
    with ThreadPoolExecutor(PARALLEL) as ex:
        for i, msg in enumerate(ex.map(run_one, jobs), 1):
            print(f"[{i}/{len(jobs)}] {msg}", flush=True)


#=====================================================================================

def load_transactions(data):
    # CP4IM file: one transaction per line, item ids followed by the class label (dropped)
    rows = [l.split()[:-1] for l in open(os.path.join(ROOT, "data", data + ".txt"))
            if l.strip() and not l.startswith("@")]
    covers = {}
    for t, row in enumerate(rows):
        for i in row:
            covers[int(i)] = covers.get(int(i), 0) | (1 << t)
    return len(rows), covers


def support(pattern, covers):
    c = None
    for i in pattern:
        c = covers[i] if c is None else c & covers[i]
    return bin(c).count("1")


def surprisingness(pattern, n, covers):
    # same measure as SurprisingnessRanker
    expected = 1.0
    for i in pattern:
        expected *= support([i], covers) / n
    return max(0.0, support(pattern, covers) / n - expected)


def make_test_set(data, freq, size, seed=12345):
    # random frequent itemsets of length 2..10 (like the queried patterns): grown one random item
    # at a time, an item being added only if the itemset stays frequent
    n, covers = load_transactions(data)
    minsup = freq * n
    frequent = [i for i in covers if support([i], covers) >= minsup]
    rnd = random.Random(seed)
    test = set()
    while len(test) < size:
        length = rnd.randint(2, 10)
        p = [rnd.choice(frequent)]
        cover = covers[p[0]]
        for i in rnd.sample(frequent, len(frequent)):
            if len(p) == length:
                break
            if i not in p and bin(cover & covers[i]).count("1") >= minsup:
                p.append(i)
                cover &= covers[i]
        p = frozenset(p)
        if len(p) >= 2 and p not in test:
            test.add(p)
    test = sorted(test, key=sorted)
    return test, [surprisingness(p, n, covers) for p in test]


def pairwise_accuracy(pred, truth):
    good = total = 0.0
    for a, b in itertools.combinations(range(len(truth)), 2):
        if truth[a] == truth[b]:
            continue
        total += 1
        d = (pred[a] - pred[b]) * (truth[a] - truth[b])
        good += 1 if d > 0 else (0.5 if d == 0 else 0)
    return good / total


def precision_at(pred, truth, frac=0.1):
    k = max(1, int(len(truth) * frac))
    top_truth = set(sorted(range(len(truth)), key=lambda i: -truth[i])[:k])
    top_pred = set(sorted(range(len(pred)), key=lambda i: -pred[i])[:k])
    return len(top_truth & top_pred) / k


def shown_quality(path, last=5):
    # mean surprisingness (the ranker's score, 4th field of the query lines) of the patterns shown
    # during the last `last' iterations
    scores = []
    for line in open(path):
        m = re.search(r"INFO  \w+ - (\d+);\d+;\d+;([\d.E-]+);", line)
        if m and int(m.group(1)) >= ITERATIONS - last:
            scores.append(float(m.group(2)))
    return statistics.mean(scores) if scores else None


def parse_iter_weights(path):
    weights = {}
    for line in open(path):
        if line.startswith("ITER_WEIGHTS;"):
            parts = line.strip().split(";")
            weights[int(parts[1])] = [float(x) for x in parts[2:]]
    return weights


def shown_queries(path):
    # iteration -> list of item sets shown in that query
    queries = {}
    for line in open(path):
        m = re.search(r"INFO  \w+ - (\d+);\d+;\d+;[^;]*;[^;]*;[^;]*;([\d+]+)\s*$", line)
        if m:
            queries.setdefault(int(m.group(1)), []).append(frozenset(map(int, m.group(2).split("+"))))
    return queries


def jaccard(a, b):
    return len(a & b) / len(a | b) if a | b else 0.0


def variety():
    # Does sampling with the learned model narrow what is shown (a "bubble")?
    # Over the last 10 iterations of each run:
    #  - distinct items: how many different items appear in any shown pattern
    #  - top-5 share: share of all item occurrences taken by the 5 most shown items
    #  - similarity: mean Jaccard similarity between shown patterns (within and across queries)
    # Iteration 1 is sampled uniformly in every run, so it is the "no model" reference.
    for data, freq in DATASETS.items():
        for k in QUERY_SIZES:
            print(f"\n## {data}, k = {k}, last 10 iterations, mean over {len(SEEDS)} seeds")
            print("config".ljust(20) + "distinct items".rjust(16) + "top-5 share".rjust(13) + "similarity".rjust(12) + "  (iteration 1: distinct, similarity)")
            for config in CONFIGS:
                rows, first = [], []
                for seed in SEEDS:
                    path = log_path(data, k, config, seed)
                    if not os.path.exists(path):
                        continue
                    q = shown_queries(path)
                    if not q:
                        continue
                    last = [p for it in sorted(q)[-10:] for p in q[it]]
                    counts = {}
                    for p in last:
                        for i in p:
                            counts[i] = counts.get(i, 0) + 1
                    top5 = sum(sorted(counts.values(), reverse=True)[:5]) / sum(counts.values())
                    sim = statistics.mean(jaccard(a, b) for a, b in itertools.combinations(last, 2))
                    rows.append((len(counts), top5, sim))
                    f = q[min(q)]
                    first.append((len(set().union(*f)), statistics.mean(jaccard(a, b) for a, b in itertools.combinations(f, 2))))
                if not rows:
                    continue
                mean = lambda v: statistics.mean(v)
                print(config.ljust(20) + f"{mean([r[0] for r in rows]):.1f}".rjust(16) + f"{100 * mean([r[1] for r in rows]):.1f}%".rjust(13)
                      + f"{mean([r[2] for r in rows]):.3f}".rjust(12)
                      + f"  ({mean([f[0] for f in first]):.1f}, {mean([f[1] for f in first]):.3f})")


def evaluate():
    for data, freq in DATASETS.items():
        test, truth = make_test_set(data, freq, TEST_SET_SIZE)
        print(f"\n### {data} (minfreq {freq}), test set: {len(test)} frequent itemsets, "
              f"{sum(1 for t in truth if t > 0)} with surprisingness > 0")
        for k in QUERY_SIZES:
            print(f"\n## k = {k}, {ITERATIONS} iterations, {len(SEEDS)} seeds (mean ± std)")
            checkpoints = sorted({0, 4, 9, ITERATIONS - 1})
            header = ("config".ljust(20) + "".join(f"acc@it{c + 1}".rjust(15) for c in checkpoints)
                      + "P@10% final".rjust(16) + "shown surpr.".rjust(16))
            print(header)
            for config in CONFIGS:
                accs = {c: [] for c in checkpoints}
                precs, shown = [], []
                for seed in SEEDS:
                    path = log_path(data, k, config, seed)
                    if not os.path.exists(path):
                        continue
                    weights = parse_iter_weights(path)
                    if shown_quality(path) is not None:
                        shown.append(shown_quality(path))
                    for c in checkpoints:
                        if c in weights:
                            w = weights[c]
                            pred = [sum(w[i] for i in p) for p in test]
                            accs[c].append(pairwise_accuracy(pred, truth))
                    if ITERATIONS - 1 in weights:
                        w = weights[ITERATIONS - 1]
                        precs.append(precision_at([sum(w[i] for i in p) for p in test], truth))
                fmt = lambda v: (f"{100 * statistics.mean(v):.1f}±{100 * statistics.stdev(v):.1f}"
                                 if len(v) > 1 else (f"{100 * v[0]:.1f}" if v else "-"))
                fmt3 = lambda v: f"{statistics.mean(v):.4f}±{statistics.stdev(v):.4f}" if len(v) > 1 else "-"
                print(config.ljust(20) + "".join(fmt(accs[c]).rjust(15) for c in checkpoints) + fmt(precs).rjust(16)
                      + fmt3(shown).rjust(16))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("action", choices=["run", "eval", "variety"])
    parser.add_argument("--xp", choices=list(EXPERIMENTS), default="multidisc")
    args = parser.parse_args()
    select_xp(args.xp)
    {"run": run, "eval": evaluate, "variety": variety}[args.action]()
