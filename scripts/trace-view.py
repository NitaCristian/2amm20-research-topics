#!/usr/bin/env python3
"""
Readable view of a run's structured trace (the JSON Lines file written with -tr).

  python3 scripts/trace-view.py TRACE.jsonl              # every iteration
  python3 scripts/trace-view.py TRACE.jsonl -it 0 5 19   # only these iterations
  python3 scripts/trace-view.py TRACE.jsonl --top 10     # show the 10 largest weights (default 8)

Per iteration: the query in sampled order, the user's ranking, the selected sub-patterns,
the clue weights before/after learning, the item weights changed by learning and by folding.
"""
import argparse
import json
import re


def fmt_items(p):
    # item ids (feature indices), as used by -cb and in the logs
    return "{" + ",".join(map(str, p["items"])) + "}"


def short(label):
    # item feature labels look like "@5<TAB>item_5=6": keep the item id
    m = re.match(r"@(\d+)\s", label)
    return m.group(1) if m else label


def top_weights(w, n):
    return ", ".join(f"{short(k)}={v:+.3f}" for k, v in sorted(w.items(), key=lambda kv: -abs(kv[1] or 0))[:n])


def changed(before, after, n):
    diff = sorted(((k, before[k], after[k]) for k in after if k in before and before[k] != after[k]),
                  key=lambda t: -abs(t[2] - t[1]))
    return [f"item {short(k)}: {b:+.3f} -> {a:+.3f}" for k, b, a in diff[:n]], len(diff)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("trace")
    ap.add_argument("-it", type=int, nargs="*", help="iterations to show (default: all)")
    ap.add_argument("--top", type=int, default=8)
    args = ap.parse_args()

    by_it = {}
    for line in open(args.trace):
        e = json.loads(line)
        by_it.setdefault(e["it"], {})[e["event"]] = e

    for e in by_it.get(-1, {}).values():
        print(e["event"].upper() + ": " + ", ".join(f"{k}={v}" for k, v in e.items()
                                                   if k not in ("event", "it", "features")))

    for it in sorted(k for k in by_it if k >= 0):
        if args.it and it not in args.it:
            continue
        ev = by_it[it]
        print(f"\n================ iteration {it} ================")
        if "query" in ev:
            print("query (sampled order; model score w.x, sampling weight):")
            for p in ev["query"]["patterns"]:
                print(f"  #{p['pos']:<2} {fmt_items(p):38s} sup={p['support']:<5} w.x={p['model_score']:+.3f}  phi={p['sampling_weight']}")
        if "ranking" in ev:
            print("ranking by the user (rank 0 = preferred):")
            for p in ev["ranking"]["patterns"]:
                print(f"  r{p['rank']:<2} (was #{p['sampled_pos']:<2}) {fmt_items(p):38s} user={p['user_score']}")
        if "clues" in ev:
            c = ev["clues"]
            print(f"sub-patterns ({c['selection']}, {c['candidates']} candidates):")
            for s in c["sub_patterns"]:
                print(f"  j={s['j']} {fmt_items(s):20s} icv={s['icv']:.3f}  in ranks {s['query_cover']}")
        b, a = ev.get("weights_before_learning"), ev.get("weights_after_learning")
        if b and a:
            for cb, ca in zip(b.get("clues", []), a.get("clues", [])):
                vals = ", ".join(f"{k}: {cb[k]:+.3f} -> {ca[k]:+.3f}" for k in cb if k not in ("j", "items"))
                print(f"  clue j={cb['j']} {cb['items']}: {vals}")
            if a.get("kept"):
                top = sorted(a["kept"], key=lambda k: -abs(k["weight"]))[:args.top]
                print(f"kept sub-patterns ({len(a['kept'])}), largest weights: " +
                      ", ".join("{" + ",".join(map(str, k["items"])) + f"}}={k['weight']:+.3f}" for k in top))
            lines, n = changed(b["weights"], a["weights"], args.top)
            print(f"learning changed {n} base weights; largest changes:")
            for l in lines:
                print("  " + l)
        if "fold" in ev:
            f = ev["fold"]
            print(f"fold ({f['aggregation']}, eta={f['eta']}, norm={f['norm']}, {f['expansion']}): "
                  f"{len(f['changes'])} weights changed")
            for ch in sorted(f["changes"], key=lambda c: -abs(c["after"] - c["before"]))[:args.top]:
                print(f"  item {short(ch['label'])}: {ch['before']:+.3f} -> {ch['after']:+.3f}  (d={ch['d']:+.3f})")
        last = ev.get("weights_after_fold") or a
        if last:
            print(f"largest weights after this iteration: {top_weights(last['weights'], args.top)}")


if __name__ == "__main__":
    main()
