'''
Experiments on DiSPaLe with several discriminating sub-patterns per iteration (-nd > 1).

  python3 scripts/multi-disc-experiment.py run   --xp NAME   # launch the runs (results/xp-NAME/...)
  python3 scripts/multi-disc-experiment.py eval  --xp NAME   # evaluate the learned models
  python3 scripts/multi-disc-experiment.py timing --xp timing # time per step (runs are sequential)
  python3 scripts/multi-disc-experiment.py variety --xp NAME  # how varied the shown patterns are
  python3 scripts/multi-disc-experiment.py tastes             # the combination users of every dataset
  python3 scripts/multi-disc-experiment.py check-user         # offline items-only vs items+combinations models
  python3 scripts/multi-disc-experiment.py curve --xp keep    # acc-combo after every iteration (pgfplots)

Experiments (--xp):
  fidelity: surprisingness user (the paper's). The published code (-lg all) against each correction
            applied alone, all corrections, and the initial weight / aggregation choices.
  main:     combination users. LetSIP, DiSPaLe (m = 1) and every selection rule with m = 3.
  learner:  combination users. 10 SCD sweeps per iteration instead of 1.
  keep:     main plus -kp keep: every selected sub-pattern stays as a permanent feature (never folded), so the
            model can represent a preference for items together. Shares results/xp-main with main.
  timing:   one combination user, runs one at a time, for the per-step times.

Every run writes a structured trace (-tr); the evaluation reads only the traces.

Simulated users. "surprisingness" is the paper's user. A combination user likes two disjoint item pairs
(weight 1 each) plus 0.01 x surprisingness as a tie-break. The pairs are drawn at random (seeded) among the
pairs of frequent items contained in 5-25% of the test patterns; they depend only on the data, not on any
run. Two such users per dataset (taste 0 and 1).

Test set. 1000 random frequent itemsets of length 2..10 per dataset, each grown one random item at a time
while it stays frequent. This is not the distribution Flexics samples from. When a run is evaluated, the test
patterns that were shown to the user in that run are left out.

Metrics (on the model after the last iteration, and at a few earlier iterations):
  acc       pairwise accuracy: % of test-pattern pairs ordered as the user orders them (pairs the user ties
            are skipped, model ties count 1/2)
  acc-combo combination users only: the same, on the pairs whose number of liked combinations differs, i.e.
            ignoring the surprisingness tie-break
  P@10%     precision among the top 10% (ties-aware: a predicted pattern counts if the user scores it at
            least as high as the 10%-th best). Combination users: on the number of liked combinations.
Each DiSPaLe model is evaluated after folding (item weights only, DiSPaLe's output) and before folding
(item weights plus the sub-pattern weights of the last iteration). Baselines: score = pattern length, and
an offline items-only model fitted on all pairs of 2000 other random patterns (check-user).
Significance: paired sign-flip permutation test over (user, seed) pairs, against the reference config.
'''

import argparse
import itertools
import json
import math
import os
import random
import statistics
import subprocess
from concurrent.futures import ThreadPoolExecutor

ROOT = os.path.realpath(os.path.join(os.path.dirname(os.path.realpath(__file__)), os.pardir))
ITERATIONS = 20
QUERY_SIZE = 10
SEEDS = [1, 2, 3, 4, 5]
TASTES = [0, 1]
TEST_SET_SIZE = 1000
PARALLEL = 6
SURPRISINGNESS = "surprisingness"

M3 = ["-m", "dispale", "-nd", "3"]
EXPERIMENTS = {
    # name: (datasets {name: minimum frequency}, users, {config: arguments}, reference config)
    "fidelity": ({"chess": 0.5, "soybean": 0.044}, [SURPRISINGNESS], {
        "letsip":                   ["-m", "letsip"],
        "letsip-iw0":               ["-m", "letsip", "-iw", "0"],
        "dispale-published":        ["-m", "dispale", "-lg", "all"],
        "fix-icvint":               ["-m", "dispale", "-lg", "coverinplace,nodedupe,stalez,nolearnedsampling,weightindex"],
        "fix-coverinplace":         ["-m", "dispale", "-lg", "icvint,nodedupe,stalez,nolearnedsampling,weightindex"],
        "fix-nodedupe":             ["-m", "dispale", "-lg", "icvint,coverinplace,stalez,nolearnedsampling,weightindex"],
        "fix-stalez":               ["-m", "dispale", "-lg", "icvint,coverinplace,nodedupe,nolearnedsampling,weightindex"],
        "fix-nolearnedsampling":    ["-m", "dispale", "-lg", "icvint,coverinplace,nodedupe,stalez,weightindex"],
        "fix-weightindex":          ["-m", "dispale", "-lg", "icvint,coverinplace,nodedupe,stalez,nolearnedsampling"],
        "dispale-fixed":            ["-m", "dispale"],
        "dispale-fixed-add":        ["-m", "dispale", "-ag", "ADD"],
        "dispale-fixed-iw0-lin":    ["-m", "dispale", "-iw", "0"],
        "dispale-fixed-iw0-add":    ["-m", "dispale", "-iw", "0", "-ag", "ADD"],
    }, "dispale-published"),
    "main": ({"chess": 0.5, "mushroom": 0.1, "soybean": 0.044}, TASTES, {
        "letsip":            ["-m", "letsip"],
        "m1":                ["-m", "dispale", "-nd", "1"],
        "m3-top":            M3 + ["-sel", "top"],
        "m3-mmr":            M3 + ["-sel", "mmr"],
        "m3-coverage":       M3 + ["-sel", "coverage"],
        "m3-gain":           M3 + ["-sel", "gain"],
        "m3-pairs":          M3 + ["-sel", "pairs"],
        "m3-gain-pooled":    M3 + ["-sel", "gain", "-fx", "pooled"],
        "m3-gain-cwzero":    M3 + ["-sel", "gain", "-cw", "zero"],
    }, "m1"),
    "learner": ({"chess": 0.5}, TASTES, {
        "letsip-sw10":       ["-m", "letsip", "-sw", "10"],
        "m1-sw10":           ["-m", "dispale", "-nd", "1", "-sw", "10"],
        "m3-gain-sw10":      M3 + ["-sel", "gain", "-sw", "10"],
        "m3-gain-sw10-full": M3 + ["-sel", "gain", "-sw", "10", "-ch", "full"],
    }, "m1-sw10"),
    "timing": ({"chess": 0.5}, [0], {
        "letsip":       ["-m", "letsip"],
        "m1":           ["-m", "dispale", "-nd", "1"],
        "m3-top":       M3 + ["-sel", "top"],
        "m3-mmr":       M3 + ["-sel", "mmr"],
        "m3-coverage":  M3 + ["-sel", "coverage"],
        "m3-gain":      M3 + ["-sel", "gain"],
        "m3-pairs":     M3 + ["-sel", "pairs"],
    }, "m1"),
}
# keep: the main experiment plus permanent sub-pattern features (-kp keep); stored with the main runs
EXPERIMENTS["keep"] = (EXPERIMENTS["main"][0], TASTES, dict(EXPERIMENTS["main"][2], **{
    "m1-keep":           ["-m", "dispale", "-nd", "1", "-kp", "keep"],
    "m3-top-keep":       M3 + ["-sel", "top", "-kp", "keep"],
    "m3-gain-keep":      M3 + ["-sel", "gain", "-kp", "keep"],
    "m3-gain-keep-sw10": M3 + ["-sel", "gain", "-kp", "keep", "-sw", "10"],
    # baselines for the 10-sweep row: separate the effect of m from the effect of the extra sweeps
    "m1-keep-sw10":      ["-m", "dispale", "-nd", "1", "-kp", "keep", "-sw", "10"],
    "m3-top-keep-sw10":  M3 + ["-sel", "top", "-kp", "keep", "-sw", "10"],
}), "m1")
XP_DIRS = {"keep": "main"}
TIMING_SEEDS = [1, 2]
XP = DATASETS = USERS = CONFIGS = REFERENCE = XP_DIR = None


def select_xp(name):
    global XP, DATASETS, USERS, CONFIGS, REFERENCE, XP_DIR
    XP = name
    DATASETS, USERS, CONFIGS, REFERENCE = EXPERIMENTS[name]
    XP_DIR = os.path.join(ROOT, "results", "xp-" + XP_DIRS.get(name, name))


def seeds():
    return TIMING_SEEDS if XP == "timing" else SEEDS


def user_name(user):
    return user if user == SURPRISINGNESS else f"taste{user}"


def trace_path(data, user, config, seed):
    return os.path.join(XP_DIR, data, user_name(user), config, f"seed{seed}.trace.jsonl")


#=====================================================================================
# data, test set, users
#=====================================================================================

_cache = {}


def load_transactions(data):
    # CP4IM file: one transaction per line, item ids followed by the class label (dropped)
    if ("tx", data) not in _cache:
        rows = [l.split()[:-1] for l in open(os.path.join(ROOT, "data", data + ".txt"))
                if l.strip() and not l.startswith("@")]
        covers = {}
        for t, row in enumerate(rows):
            for i in row:
                covers[int(i)] = covers.get(int(i), 0) | (1 << t)
        _cache[("tx", data)] = (len(rows), covers)
    return _cache[("tx", data)]


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


def random_patterns(data, freq, size, seed):
    # random frequent itemsets of length 2..10, each grown one random item at a time while it stays frequent
    key = ("patterns", data, freq, size, seed)
    if key not in _cache:
        n, covers = load_transactions(data)
        minsup = freq * n
        frequent = sorted(i for i in covers if support([i], covers) >= minsup)
        rnd = random.Random(seed)
        out = set()
        attempts = 0
        while len(out) < size:
            attempts += 1
            if attempts > 100 * size:
                raise RuntimeError(f"{data} at minimum frequency {freq}: fewer than {size} distinct frequent itemsets found")
            length = rnd.randint(2, 10)
            p = [rnd.choice(frequent)]
            cover = covers[p[0]]
            for i in rnd.sample(frequent, len(frequent)):
                if len(p) == length:
                    break
                if i not in p and bin(cover & covers[i]).count("1") >= minsup:
                    p.append(i)
                    cover &= covers[i]
            if len(p) >= 2:
                out.add(frozenset(p))
        _cache[key] = sorted(out, key=sorted)
    return _cache[key]


def test_set(data, freq):
    return random_patterns(data, freq, TEST_SET_SIZE, 12345)


def taste(data, freq, index):
    # two disjoint pairs of frequent items, drawn at random among the pairs contained in 5-25% of the test
    # patterns (so that the taste matters for a fair share of patterns); uses the data only
    test = test_set(data, freq)
    n, covers = load_transactions(data)
    items = sorted({i for p in test for i in p})
    share = lambda a, b: sum(1 for p in test if a in p and b in p) / len(test)
    pairs = [(a, b) for a, b in itertools.combinations(items, 2) if 0.05 <= share(a, b) <= 0.25]
    rnd = random.Random(f"{data}-{index}")
    while True:
        (a, b), (c, d) = rnd.sample(pairs, 2)
        if not {a, b} & {c, d}:
            return f"{a},{b};{c},{d}"


def user_args(data, freq, user):
    if user == SURPRISINGNESS:
        return ["-r", "SurprisingnessRanker"]
    return ["-r", "ComboRanker", "-cb", taste(data, freq, user)]


def parse_combos(spec):
    combos = []
    for part in filter(None, (x.strip() for x in spec.split(";"))):
        items, _, w = part.partition(":")
        combos.append((frozenset(int(i) for i in items.split(",")), float(w) if w else 1.0))
    return combos


def truths(data, freq, user, patterns):
    # (full score the user ranks with, main score used by acc-combo and P@10%)
    n, covers = load_transactions(data)
    surpr = [surprisingness(p, n, covers) for p in patterns]
    if user == SURPRISINGNESS:
        return surpr, surpr
    combos = parse_combos(taste(data, freq, user))
    level = [sum(w for S, w in combos if S <= p) for p in patterns]
    return [l + 0.01 * s for l, s in zip(level, surpr)], level


#=====================================================================================
# metrics
#=====================================================================================

def pairwise_accuracy(pred, truth):
    # pairs with different truth; concordant = 1, model tie = 1/2. O(n log n) with a Fenwick tree
    order = sorted(range(len(truth)), key=lambda i: truth[i])
    ranks = {v: r for r, v in enumerate(sorted(set(pred)))}
    tree = [0] * (len(ranks) + 1)

    def add(r):
        r += 1
        while r < len(tree):
            tree[r] += 1
            r += r & -r

    def below(r):  # inserted elements with pred rank < r
        s = 0
        while r > 0:
            s += tree[r]
            r -= r & -r
        return s

    good = total = inserted = 0
    i = 0
    while i < len(order):
        j = i
        while j < len(order) and truth[order[j]] == truth[order[i]]:
            j += 1
        group = order[i:j]
        for g in group:
            r = ranks[pred[g]]
            lower, upto = below(r), below(r + 1)
            good += lower + 0.5 * (upto - lower)
        total += len(group) * inserted
        for g in group:
            add(ranks[pred[g]])
        inserted += len(group)
        i = j
    return good / total if total else float("nan")


def precision_at(pred, truth, frac=0.1):
    k = max(1, int(len(truth) * frac))
    threshold = sorted(truth, reverse=True)[k - 1]
    top_pred = sorted(range(len(pred)), key=lambda i: -pred[i])[:k]
    return sum(1 for i in top_pred if truth[i] >= threshold) / k


def permutation_p(diffs, rounds=20000, seed=0):
    # two-sided paired sign-flip test of mean(diffs) = 0 (exact when 2^n <= rounds)
    diffs = [d for d in diffs if d == d]
    n = len(diffs)
    if n == 0:
        return float("nan")
    obs = abs(sum(diffs))
    if 2 ** n <= rounds:
        signs = itertools.product([1, -1], repeat=n)
        hits = total = 0
        for s in signs:
            total += 1
            hits += abs(sum(a * b for a, b in zip(s, diffs))) >= obs - 1e-12
        return hits / total
    rnd = random.Random(seed)
    hits = sum(abs(sum(d if rnd.random() < 0.5 else -d for d in diffs)) >= obs - 1e-12 for _ in range(rounds))
    return (hits + 1) / (rounds + 1)


#=====================================================================================
# traces
#=====================================================================================

def read_trace(path):
    by_it = {}
    for line in open(path):
        e = json.loads(line)
        by_it.setdefault(e["it"], {})[e["event"]] = e
    return by_it


def finished(path):
    return os.path.exists(path) and '"event":"end"' in open(path).read()


def models(trace, it):
    # (score after folding, score before folding) at iteration `it'; a score maps a pattern to w.x
    ev = trace.get(it, {})
    after_w = (ev.get("weights_after_fold") or ev.get("weights_after_learning") or {}).get("weights")
    if after_w is None:
        return None, None
    item_w = list(after_w.values())   # base features are the items, in item order
    after = lambda p: sum(item_w[i] for i in p)
    learned = ev.get("weights_after_learning")
    if learned and "kept" in learned:
        # -kp keep: nothing is folded; the model is items + kept sub-patterns, before and after
        kept = [(frozenset(k["items"]), k["weight"]) for k in learned["kept"]]
        model = lambda p: after(p) + sum(w for S, w in kept if S <= p)
        return model, model
    if not learned or "clues" not in learned:
        return after, after
    pre_w = list(learned["weights"].values())
    clues = [([frozenset(s) for s in c["items"]], c["presence"]) for c in learned["clues"]]

    def before(p):
        s = sum(pre_w[i] for i in p)
        for subs, w in clues:
            if len(subs) == 1:
                s += w * (subs[0] <= p)
            else:   # pooled: share of the sub-patterns contained in p
                s += w * sum(S <= p for S in subs) / len(subs)
        return s
    return after, before


def shown_patterns(trace):
    return {frozenset(p["items"]) for it, ev in trace.items() if it >= 0 and "query" in ev
            for p in ev["query"]["patterns"]}


#=====================================================================================
# actions
#=====================================================================================

def run_one(job):
    data, freq, user, config, seed = job
    out = trace_path(data, user, config, seed)
    if finished(out):
        return f"skip {out}"
    os.makedirs(os.path.dirname(out), exist_ok=True)
    cmd = (["python3", os.path.join(ROOT, "scripts", "test-program.py"), "-d", data, "-f", str(freq),
            "-k", str(QUERY_SIZE), "-i", str(ITERATIONS), "-o", "flexics", "-a", "eflexics", "-F", "Items",
            "-l", "0", "-s", str(seed), "-to", "3600", "-tr", out]
           + user_args(data, freq, user) + CONFIGS[config])
    # own temp dir per run: the native library loader deletes the libraries other JVMs extracted to the temp dir
    tmp = os.path.join(XP_DIR, "tmp", f"{data}-{user_name(user)}-{config}-{seed}")
    os.makedirs(tmp, exist_ok=True)
    env = dict(os.environ, JAVA_TOOL_OPTIONS=f"-Djava.io.tmpdir={tmp}")
    with open(out[:-len(".trace.jsonl")] + ".log", "w") as f:
        subprocess.run(cmd, cwd=ROOT, stdout=f, stderr=subprocess.STDOUT, env=env)
    return f"{'done' if finished(out) else 'FAILED'} {out}"


def run():
    jobs = [(d, f, u, c, s) for d, f in DATASETS.items() for u in USERS for c in CONFIGS for s in seeds()]
    # timing runs one at a time, so that the measured times are not affected by other runs
    with ThreadPoolExecutor(1 if XP == "timing" else PARALLEL) as ex:
        for i, msg in enumerate(ex.map(run_one, jobs), 1):
            print(f"[{i}/{len(jobs)}] {msg}", flush=True)


def table(header, rows):
    widths = [max(len(str(r[i])) for r in [header] + rows) for i in range(len(header))]
    line = lambda r: "  ".join(str(v).ljust(w) if i == 0 else str(v).rjust(w) for i, (v, w) in enumerate(zip(r, widths)))
    print(line(header))
    for r in rows:
        print(line(r))


def fmt(values, scale=100, digits=1):
    values = [v for v in values if v == v]
    if not values:
        return "-"
    m = scale * statistics.mean(values)
    if len(values) == 1:
        return f"{m:.{digits}f}"
    return f"{m:.{digits}f}±{scale * statistics.stdev(values):.{digits}f}"


def fmt_p(p):
    return "-" if p != p else (f"{p:.3f}" if p >= 0.001 else "<0.001")


def evaluate():
    last = ITERATIONS - 1
    checkpoints = [0, 4, 9, last]
    mismatches = 0
    pooled = {}   # config -> metric -> values over (dataset, user, seed), in the same order for every config
    for data, freq in DATASETS.items():
        test = test_set(data, freq)
        users = USERS
        print(f"\n### {data} (minimum frequency {freq}), k = {QUERY_SIZE}, {ITERATIONS} iterations, "
              f"seeds {seeds()}, users: " + ", ".join(
                  user_name(u) + ("" if u == SURPRISINGNESS else f" ({taste(data, freq, u)})") for u in users))
        combo = users != [SURPRISINGNESS]
        # per config: metric -> list over (user, seed); kept in (user, seed) order for paired tests
        results = {}
        for config in CONFIGS:
            r = {key: [] for key in ["acc"] + [f"acc@{c + 1}" for c in checkpoints[:-1]]
                 + ["acc-before", "acc-combo", "acc-combo-before", "p10", "p10-before", "shown", "tied", "w-items", "w-clues"]}
            for user in users:
                for seed in seeds():
                    path = trace_path(data, user, config, seed)
                    if not finished(path):
                        for v in r.values():
                            v.append(float("nan"))
                        continue
                    trace = read_trace(path)
                    mismatches += sum(1 for ev in trace.values() if "clues" in ev and not ev["clues"]["top1_matches"])
                    shown = shown_patterns(trace)
                    patterns = [p for p in test if p not in shown]
                    full, main = truths(data, freq, user, patterns)
                    for c in checkpoints[:-1]:
                        after, _ = models(trace, c)
                        r[f"acc@{c + 1}"].append(pairwise_accuracy([after(p) for p in patterns], full) if after else float("nan"))
                    after, before = models(trace, last)
                    a = [after(p) for p in patterns]
                    b = [before(p) for p in patterns]
                    r["acc"].append(pairwise_accuracy(a, full))
                    r["acc-before"].append(pairwise_accuracy(b, full))
                    r["acc-combo"].append(pairwise_accuracy(a, main) if combo else float("nan"))
                    r["acc-combo-before"].append(pairwise_accuracy(b, main) if combo else float("nan"))
                    r["p10"].append(precision_at(a, main))
                    r["p10-before"].append(precision_at(b, main))
                    # user score of the patterns shown in the last 5 iterations
                    r["shown"].append(statistics.mean(p["user_score"] for it, ev in trace.items() if it >= last - 4
                                                      and "ranking" in ev for p in ev["ranking"]["patterns"]))
                    clues = [ev["clues"] for ev in trace.values() if "clues" in ev]
                    r["tied"].append(statistics.mean(c["tied_at_max"] for c in clues) if clues else float("nan"))
                    learned = trace[last].get("weights_after_learning", {})
                    r["w-items"].append(statistics.mean(abs(v) for v in learned["weights"].values()) if learned else float("nan"))
                    subs = ([abs(c["presence"]) for c in learned["clues"]] if learned.get("clues") else
                            [abs(k["weight"]) for k in learned["kept"]] if learned.get("kept") else [])
                    r["w-clues"].append(statistics.mean(subs) if subs else float("nan"))
            results[config] = r
            for key in ("acc", "acc-combo", "p10", "acc-before", "p10-before"):
                pooled.setdefault(config, {}).setdefault(key, []).extend(r[key])

        # length baseline: score = |pattern| (what the initial weights 1 give before any feedback)
        base = {"acc": [], "acc-combo": [], "p10": []}
        for user in users:
            full, main = truths(data, freq, user, test)
            length = [len(p) for p in test]
            base["acc"].append(pairwise_accuracy(length, full))
            base["acc-combo"].append(pairwise_accuracy(length, main) if combo else float("nan"))
            base["p10"].append(precision_at(length, main))

        ref = results.get(REFERENCE)
        p_vs = lambda key, config: permutation_p([x - y for x, y in zip(results[config][key], ref[key])]) if ref else float("nan")
        print("\nFinal model, last iteration (fold: item weights only; -kp keep: items + kept sub-patterns); "
              "p: paired test against " + REFERENCE)
        header = ["config"] + [f"acc@{c + 1}" for c in checkpoints[:-1]] + ["acc", "p"]
        header += (["acc-combo", "p"] if combo else []) + ["P@10%", "p", "shown score"]
        rows = []
        for config, r in results.items():
            row = [config] + [fmt(r[f"acc@{c + 1}"]) for c in checkpoints[:-1]] + [fmt(r["acc"]), fmt_p(p_vs("acc", config))]
            row += ([fmt(r["acc-combo"]), fmt_p(p_vs("acc-combo", config))] if combo else [])
            row += [fmt(r["p10"]), fmt_p(p_vs("p10", config)), fmt(r["shown"], 1, 4)]
            rows.append(row)
        row = ["score = length"] + ["-"] * (len(checkpoints) - 1) + [fmt(base["acc"]), "-"]
        row += ([fmt(base["acc-combo"]), "-"] if combo else []) + [fmt(base["p10"]), "-", "-"]
        rows.append(row)
        table(header, rows)

        print("\nBefore vs after folding (DiSPaLe: item weights + last iteration's sub-pattern weights), "
              "mean |weight| of items and sub-patterns, ties at the highest ICV; p: before vs after")
        header = ["config", "acc after", "acc before", "p"] + (["acc-combo after", "acc-combo before", "p"] if combo else [])
        header += ["P@10% after", "P@10% before", "p", "|w item|", "|w sub-pattern|", "tied at max ICV"]
        rows = []
        for config, r in results.items():
            if all(v != v for v in r["tied"]):
                continue
            pd = lambda a, b: fmt_p(permutation_p([x - y for x, y in zip(r[b], r[a])]))
            row = [config, fmt(r["acc"]), fmt(r["acc-before"]), pd("acc", "acc-before")]
            row += [fmt(r["acc-combo"]), fmt(r["acc-combo-before"]), pd("acc-combo", "acc-combo-before")] if combo else []
            row += [fmt(r["p10"]), fmt(r["p10-before"]), pd("p10", "p10-before"),
                    fmt(r["w-items"], 1, 3), fmt(r["w-clues"], 1, 3), fmt(r["tied"], 1, 1)]
            rows.append(row)
        table(header, rows)
    if len(DATASETS) > 1:
        ref = pooled.get(REFERENCE)
        combo = USERS != [SURPRISINGNESS]
        print(f"\n### All datasets together ({', '.join(DATASETS)}): mean over every (dataset, user, seed); "
              f"p: paired test against {REFERENCE} over all of them")
        header = ["config", "acc", "p"] + (["acc-combo", "p"] if combo else []) + ["P@10%", "p", "acc before folding", "P@10% before folding"]
        rows = []
        for config, r in pooled.items():
            pv = lambda key: fmt_p(permutation_p([x - y for x, y in zip(r[key], ref[key])])) if ref else "-"
            row = [config, fmt(r["acc"]), pv("acc")] + ([fmt(r["acc-combo"]), pv("acc-combo")] if combo else [])
            row += [fmt(r["p10"]), pv("p10"), fmt(r["acc-before"]), fmt(r["p10-before"])]
            rows.append(row)
        table(header, rows)
    print(f"\nregression check: first selected sub-pattern differs from the original top-1 rule in {mismatches} iterations")


def timing():
    rows = []
    steps = ["sample", "mine", "select", "learn", "fold"]
    for data, freq in DATASETS.items():
        for config in CONFIGS:
            t = {s: [] for s in steps}
            cands = []
            for user in USERS:
                for seed in seeds():
                    path = trace_path(data, user, config, seed)
                    if not finished(path):
                        continue
                    for it, ev in read_trace(path).items():
                        if "timing" in ev:
                            for s in steps:
                                if f"{s}_ms" in ev["timing"]:
                                    t[s].append(ev["timing"][f"{s}_ms"])
                        if "clues" in ev:
                            cands.append(ev["clues"]["candidates"])
            rows.append([data, config] + [fmt(t[s], 1, 1) for s in steps]
                        + [fmt(cands, 1, 0), f"{sum(statistics.mean(v) for v in t.values() if v):.0f}"])
    print(f"Mean wall-clock time per iteration (ms), runs executed one at a time; candidates = distinct covers")
    table(["dataset", "config"] + [f"{s} ms" for s in steps] + ["candidates", "total ms"], rows)


def variety():
    # Does sampling with the learned model narrow what is shown (a "bubble")? Over the last 10 iterations:
    # distinct items shown, share of the 5 most shown items, mean Jaccard similarity of the shown patterns
    jac = lambda a, b: len(a & b) / len(a | b) if a | b else 0.0
    for data, freq in DATASETS.items():
        rows = []
        for config in CONFIGS:
            vals = []
            for user in USERS:
                for seed in seeds():
                    path = trace_path(data, user, config, seed)
                    if not finished(path):
                        continue
                    trace = read_trace(path)
                    last = [frozenset(p["items"]) for it in range(ITERATIONS - 10, ITERATIONS)
                            for p in trace[it]["query"]["patterns"]]
                    counts = {}
                    for p in last:
                        for i in p:
                            counts[i] = counts.get(i, 0) + 1
                    vals.append((len(counts), sum(sorted(counts.values(), reverse=True)[:5]) / sum(counts.values()),
                                 statistics.mean(jac(a, b) for a, b in itertools.combinations(last, 2))))
            if vals:
                rows.append([config, fmt([v[0] for v in vals], 1, 1), fmt([v[1] for v in vals]), fmt([v[2] for v in vals], 1, 3)])
        print(f"\n## {data}: last 10 iterations")
        table(["config", "distinct items", "top-5 share %", "similarity"], rows)


def tastes():
    for name, (datasets, users, _, _) in EXPERIMENTS.items():
        for data, freq in datasets.items():
            for u in users:
                if u != SURPRISINGNESS:
                    spec = taste(data, freq, u)
                    combos = parse_combos(spec)
                    test = test_set(data, freq)
                    share = sum(1 for p in test if any(S <= p for S, _ in combos)) / len(test)
                    print(f"{name:9s} {data:9s} taste{u}: {spec:14s} test patterns with a liked pair: {100 * share:.1f}%")


def curve():
    # mean acc-combo of the final-model type after every iteration, over all (dataset, user, seed); printed as
    # pgfplots coordinates (the shown patterns of the whole run are excluded from the test set)
    configs = [c for c in ["m1", "m3-gain", "m1-keep", "m3-gain-keep"] if c in CONFIGS]
    for config in configs:
        per_it = {it: [] for it in range(ITERATIONS)}
        for data, freq in DATASETS.items():
            test = test_set(data, freq)
            for user in USERS:
                for seed in seeds():
                    path = trace_path(data, user, config, seed)
                    if not finished(path):
                        continue
                    trace = read_trace(path)
                    shown = shown_patterns(trace)
                    patterns = [p for p in test if p not in shown]
                    _, main = truths(data, freq, user, patterns)
                    for it in range(ITERATIONS):
                        model, _ = models(trace, it)
                        if model:
                            per_it[it].append(pairwise_accuracy([model(p) for p in patterns], main))
        coords = " ".join(f"({it + 1},{100 * statistics.mean(v):.2f})" for it, v in per_it.items() if v)
        print(f"% {config}: n = {len(per_it[0])} runs\n\\addplot coordinates {{{coords}}};")


def fit_pairwise(train, truth, features, epochs=200, lr=0.5, pairs_per_epoch=20000, seed=0):
    # offline pairwise logistic regression (no penalty), mini-batch gradient steps on random pairs with
    # different truth; features(p) -> set of active feature ids
    rnd = random.Random(seed)
    data = [(features(p), y) for p, y in zip(train, truth)]
    w = {}
    for epoch in range(epochs):
        step = lr / (1 + epoch / 20)
        grad = {}
        for _ in range(pairs_per_epoch):
            (fa, ya), (fb, yb) = rnd.sample(data, 2)
            if ya == yb:
                continue
            if ya < yb:
                fa, fb = fb, fa
            d = sum(w.get(f, 0) for f in fa) - sum(w.get(f, 0) for f in fb)
            g = 1 / (1 + math.exp(min(30, d)))
            for f in fa - fb:
                grad[f] = grad.get(f, 0) + g
            for f in fb - fa:
                grad[f] = grad.get(f, 0) - g
        for f, g in grad.items():
            w[f] = w.get(f, 0) + step * g / pairs_per_epoch
    return lambda p: sum(w.get(f, 0) for f in features(p))


def check_user():
    # offline reference models for the combination users, fitted on 2000 other random patterns and scored on the
    # test set with the same metrics as the online runs: items only (what DiSPaLe's folded model can express)
    # and items + one feature per liked pair (what sub-pattern features could add)
    select_xp("main")
    rows = []
    for data, freq in DATASETS.items():
        test = test_set(data, freq)
        train = [p for p in random_patterns(data, freq, 2 * TEST_SET_SIZE, 999) if p not in set(test)]
        for user in USERS:
            combos = parse_combos(taste(data, freq, user))
            full_tr, _ = truths(data, freq, user, train)
            full, main = truths(data, freq, user, test)
            for name, feats in [("items only", lambda p: set(p)),
                                ("items + liked pairs", lambda p: set(p) | {("c", j) for j, (S, _) in enumerate(combos) if S <= p})]:
                model = fit_pairwise(train, full_tr, feats)
                pred = [model(p) for p in test]
                rows.append([data, user_name(user), name, fmt([pairwise_accuracy(pred, full)]),
                             fmt([pairwise_accuracy(pred, main)]), fmt([precision_at(pred, main)])])
    table(["dataset", "user", "offline model", "acc", "acc-combo", "P@10%"], rows)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("action", choices=["run", "eval", "timing", "variety", "tastes", "check-user", "curve"])
    parser.add_argument("--xp", choices=list(EXPERIMENTS), default="main")
    parser.add_argument("--ref", help="reference config of the paired tests (default: the experiment's)")
    args = parser.parse_args()
    select_xp(args.xp)
    if args.ref:
        assert args.ref in CONFIGS, f"unknown config {args.ref}"
        REFERENCE = args.ref
    {"run": run, "eval": evaluate, "timing": timing, "variety": variety, "tastes": tastes,
     "check-user": check_user, "curve": curve}[args.action]()
