#!/usr/bin/env python3
"""
Worked examples from a run's trace (-tr): for each chosen iteration, the patterns shown to the user, the
user's ranking, the ICV computation of the selected sub-patterns and the partition-gain steps.

  python3 scripts/worked-example.py TRACE.jsonl -it 0 1 2 [--liked 29,37;33,45] [--top 5]

The candidates are recomputed from the ranked query (every sub-itemset contained in at least one query
pattern, one per distinct cover, ICV > 0) and the greedy partition gain is replayed on them; the script checks
that the sub-patterns it explains are the ones the program selected (the trace's "clues" event).
"""
import argparse
import itertools
import json


def fmt_set(s):
    return "{" + ",".join(map(str, sorted(s))) + "}"


def icv(cover, k):
    # inter-class variance of the ranks 0..k-1 split into covered / not covered
    mu = (k - 1) / 2
    inside = [r for r in range(k) if r in cover]
    outside = [r for r in range(k) if r not in cover]
    total = 0.0
    for group in (inside, outside):
        if group:
            total += len(group) * (mu - sum(group) / len(group)) ** 2
    return total


def icv_steps(cover, k):
    mu = (k - 1) / 2
    inside = [r for r in range(k) if r in cover]
    outside = [r for r in range(k) if r not in cover]
    mi, mo = sum(inside) / len(inside), sum(outside) / len(outside)
    return (f"covered ranks {inside}: mean {mi:.3g}; not covered {outside}: mean {mo:.3g}; "
            f"ICV = {len(inside)}·({mu:g} − {mi:.3g})² + {len(outside)}·({mu:g} − {mo:.3g})² = "
            f"{len(inside) * (mu - mi) ** 2:.3f} + {len(outside) * (mu - mo) ** 2:.3f} = {icv(cover, k):.3f}")


def partition(chosen_covers, k):
    groups = {}
    for r in range(k):
        sig = tuple(int(r in c) for c in chosen_covers)
        groups.setdefault(sig, []).append(r)
    return groups


def picv(chosen_covers, k):
    mu = (k - 1) / 2
    return sum(len(g) * (mu - sum(g) / len(g)) ** 2 for g in partition(chosen_covers, k).values())


def candidates(ranked, k):
    # every sub-itemset contained in at least one query pattern, with its cover (ranks containing it)
    cover_of = {}
    for p in ranked:
        for size in range(1, len(p) + 1):
            for sub in itertools.combinations(sorted(p), size):
                s = frozenset(sub)
                if s not in cover_of:
                    cover_of[s] = frozenset(r for r, q in enumerate(ranked) if s <= q)
    # one per cover (the longest), ICV > 0, sorted by ICV then length
    best = {}
    for s, c in cover_of.items():
        if icv(c, k) > 0 and (c not in best or len(s) > len(best[c])):
            best[c] = s
    return sorted(((s, c, icv(c, k)) for c, s in best.items()), key=lambda t: (-t[2], -len(t[0])))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("trace")
    ap.add_argument("-it", type=int, nargs="+", required=True)
    ap.add_argument("--liked", default=None, help="the user's liked combinations, to mark them (e.g. 29,37;33,45)")
    ap.add_argument("--top", type=int, default=5, help="competing candidates shown per greedy step")
    args = ap.parse_args()
    liked = [frozenset(map(int, x.split(","))) for x in args.liked.split(";")] if args.liked else []

    by_it = {}
    for line in open(args.trace):
        e = json.loads(line)
        by_it.setdefault(e["it"], {})[e["event"]] = e
    run = by_it[-1]["run"]
    print(f"run: {run['method']}, {run['dataset']}, user {run['user']}, k = {run['query_size']}")
    if "run_dispale" in by_it[-1]:
        d = by_it[-1]["run_dispale"]
        print(f"     m = {d['nb_sub_patterns']}, selection {d['selection']}, sub-patterns {d.get('keep_sub_patterns', 'fold')}")

    for it in args.it:
        ev = by_it[it]
        k = len(ev["ranking"]["patterns"])
        mark = lambda s: " ".join("[likes " + fmt_set(L) + "]" for L in liked if L <= s)
        print(f"\n==================== iteration {it} ====================")
        print("\n1. Query shown to the user (sampled order, model score w·x):")
        for p in ev["query"]["patterns"]:
            print(f"   #{p['pos']}  {fmt_set(p['items']):34s} w·x = {p['model_score']:+.3f}")
        print("\n2. User's ranking (rank 0 = preferred):")
        ranked = []
        for p in ev["ranking"]["patterns"]:
            ranked.append(frozenset(p["items"]))
            print(f"   r{p['rank']}  (was #{p['sampled_pos']})  {fmt_set(p['items']):34s} score {p['user_score']:.4f}  {mark(set(p['items']))}")

        cands = candidates(ranked, k)
        selected = [frozenset(s["items"]) for s in ev["clues"]["sub_patterns"]]
        print(f"\n3. ICV of the selected sub-patterns (μ = (k−1)/2 = {(k - 1) / 2:g}); "
              f"{len(cands)} candidates with distinct covers and ICV > 0 (program: {ev['clues']['candidates']})")
        for s in ev["clues"]["sub_patterns"]:
            cover = frozenset(s["query_cover"])
            print(f"   {fmt_set(s['items'])} {mark(set(s['items']))}\n      {icv_steps(cover, k)}   (program: {s['icv']:.3f})")
        print("   highest-ICV candidates:")
        for s, c, v in cands[:args.top]:
            print(f"      {fmt_set(s):24s} ranks {sorted(c)}  ICV {v:.3f} {mark(s)}")

        print(f"\n4. Partition gain (TSS = {picv([frozenset([r]) for r in range(k)], k):.3f}; greedy, start from the highest-ICV candidate):")
        chosen = [frozenset(selected[0])]
        cover = {s: c for s, c, _ in cands}
        sel_covers = [frozenset(ev["clues"]["sub_patterns"][0]["query_cover"])]
        print(f"   step 1: {fmt_set(selected[0])}, PICV = {picv(sel_covers, k):.3f}")
        for step, s in enumerate(ev["clues"]["sub_patterns"][1:], 2):
            current = picv(sel_covers, k)
            gains = sorted(((picv(sel_covers + [c], k) - current, x, c) for x, c, _ in cands
                            if c not in sel_covers), key=lambda t: (-t[0], -icv(t[2], k), -len(t[1])))
            print(f"   step {step}: current PICV = {current:.3f}; best gains:")
            for g, x, c in gains[:args.top]:
                print(f"      {fmt_set(x):24s} ranks {sorted(c)}  PICV {current + g:.3f}  gain {g:+.3f} {mark(x)}")
            sel_covers.append(frozenset(s["query_cover"]))
            print(f"      -> selected {fmt_set(s['items'])} (gain {picv(sel_covers, k) - current:+.3f})")
        groups = partition(sel_covers, k)
        print(f"   final partition (signature over the selected sub-patterns -> ranks):")
        mu = (k - 1) / 2
        for sig, g in sorted(groups.items(), reverse=True):
            print(f"      {''.join(map(str, sig))}: ranks {g}, mean {sum(g) / len(g):.3g}, "
                  f"contribution {len(g)}·({mu:g} − {sum(g) / len(g):.3g})² = {len(g) * (mu - sum(g) / len(g)) ** 2:.3f}")
        print(f"   PICV = {picv(sel_covers, k):.3f} of TSS {picv([frozenset([r]) for r in range(k)], k):.3f}")


if __name__ == "__main__":
    main()
