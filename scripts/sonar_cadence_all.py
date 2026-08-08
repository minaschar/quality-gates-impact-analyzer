#!/usr/bin/env python3
"""
sonar_cadence_all.py
====================

Per-repository view of HOW the SonarQube analyses we have are spread over each
repo's life: continuously, only at the beginning, only at the end, or one-off.

  >>> THIS SCRIPT COUNTS ALL ROWS, WITHOUT ANY FILTER. <<<
  (For the "only qualityGateStatus == OK" version, see sonar_cadence_ok.py.)

Designed for a HUGE CSV (e.g. 3.5 GB) that does NOT fit in RAM:
  * the file is streamed in chunks (never fully loaded);
  * only a handful of columns are read;
  * all per-chunk work is VECTORISED (pandas groupby, no per-row Python loops),
    so it scales to tens of millions of rows;
  * memory stays flat -- we keep only small per-repo aggregates
    (a repo x time-bucket matrix), independent of file size.

A logger prints steady progress (rows processed, throughput, % and ETA on the
second pass) so you can see the run is alive and nothing has crashed.

Time axis:
  --axis pr   (default)  `pullRequestNumber` as a development-progress axis.
                         GitHub PR numbers increase monotonically: low = early,
                         high = late. Position is measured over the WHOLE life
                         of the repo: PR #1 -> 0%, latest known PR -> 100%.
                         Answers "did we analyze the BEGINNING / END?".
  --axis date --date-col createdAt
                         Buckets by real calendar month. Answers "every month?"
                         -- only meaningful if the column is the PR's real date,
                         not the moment you ran a batch scan.

Outputs (in --out, default ./sonar_cadence_all_out):
  - cadence_timeline.html    <- main deliverable: interactive timeline
  - repo_cadence_summary.csv
  - repo_segment_matrix.csv
  - repo_cadence_heatmap.png (static, if matplotlib is available)

Examples:
  python sonar_cadence_all.py results.csv
  python sonar_cadence_all.py results.csv --segments 40 --chunksize 250000
  python sonar_cadence_all.py results.csv --axis date --date-col createdAt
  python sonar_cadence_all.py results.csv --log-file run.log --log-level DEBUG
"""

import argparse
import json
import logging
import os
import sys
import time
from collections import defaultdict

import pandas as pd
import numpy as np

# ----------------------------------------------------------------------------
# THE ONLY DIFFERENCE between this script and sonar_cadence_ok.py:
#   None  -> count every row.
#   "OK"  -> count only rows whose qualityGateStatus equals this value.
# ----------------------------------------------------------------------------
QUALITY_GATE_FILTER = None

BASE_COLS = ["username", "repositoryName", "pullRequestNumber",
             "pullRequestTitle", "qualityGateStatus"]
STR_DTYPES = {"username": "string", "repositoryName": "string",
              "pullRequestTitle": "string", "qualityGateStatus": "string"}
PROCESSING_ERROR_TITLE = "PROCESSING_ERROR"
COARSE_BUCKETS = 12          # grid used only to derive the coverage/pattern label
SPARK_CHARS = " ▁▂▃▄▅▆▇█"

log = logging.getLogger("sonar_cadence")


# ----------------------------------------------------------------------------
# CLI + logging
# ----------------------------------------------------------------------------
def parse_args():
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("input", help="Path to the (large) CSV file.")
    p.add_argument("--axis", choices=["pr", "date"], default="pr")
    p.add_argument("--date-col", default="createdAt")
    p.add_argument("--segments", type=int, default=40,
                   help="Time buckets across each repo's life (default 40).")
    p.add_argument("--min-analyses", type=int, default=1)
    p.add_argument("--top", type=int, default=60,
                   help="Max repos to render in the HTML/PNG (by count).")
    p.add_argument("--chunksize", type=int, default=250_000,
                   help="Rows per streaming chunk (default 250000). Lower it if "
                        "rows are very wide and you hit memory pressure.")
    p.add_argument("--sep", default=",")
    p.add_argument("--encoding", default="utf-8",
                   help="CSV encoding (try latin-1 if you get UnicodeDecodeError).")
    p.add_argument("--on-bad-lines", default="warn",
                   choices=["error", "warn", "skip"],
                   help="What to do with malformed CSV lines (default warn).")
    p.add_argument("--out", default="sonar_cadence_all_out")
    p.add_argument("--log-level", default="INFO",
                   choices=["DEBUG", "INFO", "WARNING", "ERROR"])
    p.add_argument("--log-file", default=None,
                   help="Also write logs to this file.")
    return p.parse_args()


def setup_logging(level, log_file):
    log.setLevel(getattr(logging, level))
    log.handlers.clear()
    fmt = logging.Formatter("%(asctime)s | %(levelname)-7s | %(message)s", "%H:%M:%S")
    sh = logging.StreamHandler(sys.stderr)
    sh.setFormatter(fmt)
    log.addHandler(sh)
    if log_file:
        fh = logging.FileHandler(log_file, encoding="utf-8")
        fh.setFormatter(fmt)
        log.addHandler(fh)


def fmt_dur(seconds):
    seconds = int(max(0, seconds))
    h, rem = divmod(seconds, 3600)
    m, s = divmod(rem, 60)
    return f"{h:d}:{m:02d}:{s:02d}" if h else f"{m:d}:{s:02d}"


# ----------------------------------------------------------------------------
# Streaming
# ----------------------------------------------------------------------------
def read_cols(args):
    cols = list(BASE_COLS)
    if args.axis == "date" and args.date_col not in cols:
        cols.append(args.date_col)
    return cols


def iter_chunks(args, usecols):
    """Yield (chunk, cumulative_rows). Reads only `usecols`, in chunks."""
    dtypes = {c: t for c, t in STR_DTYPES.items() if c in usecols}
    try:
        reader = pd.read_csv(
            args.input, sep=args.sep, usecols=usecols, dtype=dtypes,
            chunksize=args.chunksize, encoding=args.encoding,
            on_bad_lines=args.on_bad_lines,
        )
    except ValueError as e:
        log.error("Could not start reading the CSV. Required columns are: %s",
                  ", ".join(usecols))
        log.error("pandas said: %s", e)
        raise
    cum = 0
    for chunk in reader:
        cum += len(chunk)
        yield chunk, cum


def add_repo(df):
    df["repo"] = (df["username"].astype("string") + "/"
                  + df["repositoryName"].astype("string"))
    return df


def apply_filter(chunk):
    if QUALITY_GATE_FILTER is not None:
        chunk = chunk[chunk["qualityGateStatus"].astype("string") == QUALITY_GATE_FILTER]
    return chunk


def progress(label, t0, cum, chunk_idx, total=None):
    el = time.time() - t0
    rate = cum / el if el > 0 else 0.0
    msg = (f"[{label}] chunk {chunk_idx:>4} | {cum:>12,} rows | "
           f"{rate:>10,.0f} rows/s | {fmt_dur(el):>8} elapsed")
    if total:
        pct = 100.0 * cum / total
        eta = (total - cum) / rate if rate > 0 else 0
        msg += f" | {pct:5.1f}% | ETA {fmt_dur(eta)}"
    log.info(msg)


# ----------------------------------------------------------------------------
# PASS 1 -- per-repo extents (need pr_max before we can place buckets) + totals
# ----------------------------------------------------------------------------
def pass1_extents(args, usecols):
    log.info("PASS 1/2: scanning for per-repo PR range and totals ...")
    stats = {}
    t0, cum, kept = time.time(), 0, 0
    for i, (chunk, cum) in enumerate(iter_chunks(args, usecols), 1):
        chunk = apply_filter(add_repo(chunk))
        kept += len(chunk)
        pr = pd.to_numeric(chunk["pullRequestNumber"], errors="coerce")
        ok = chunk["pullRequestTitle"].fillna("") != PROCESSING_ERROR_TITLE
        tmp = pd.DataFrame({"repo": chunk["repo"].values, "pr": pr.values, "ok": ok.values})
        agg = tmp.groupby("repo").agg(total=("pr", "size"), pr_min=("pr", "min"),
                                      pr_max=("pr", "max"), succ=("ok", "sum"))
        if args.axis == "date":
            dt = pd.to_datetime(chunk[args.date_col], errors="coerce", utc=True)
            dmm = (pd.DataFrame({"repo": chunk["repo"].values, "d": dt.values})
                   .groupby("repo")["d"].agg(["min", "max"]))
        for repo, row in agg.iterrows():
            s = stats.get(repo)
            if s is None:
                s = stats[repo] = {"total": 0, "successful": 0, "pr_min": None,
                                   "pr_max": None, "date_min": None, "date_max": None}
            s["total"] += int(row["total"])
            s["successful"] += int(row["succ"])
            if pd.notna(row["pr_max"]):
                s["pr_min"] = row["pr_min"] if s["pr_min"] is None else min(s["pr_min"], row["pr_min"])
                s["pr_max"] = row["pr_max"] if s["pr_max"] is None else max(s["pr_max"], row["pr_max"])
            if args.axis == "date" and repo in dmm.index:
                dmin, dmax = dmm.loc[repo, "min"], dmm.loc[repo, "max"]
                if pd.notna(dmin):
                    s["date_min"] = dmin if s["date_min"] is None else min(s["date_min"], dmin)
                    s["date_max"] = dmax if s["date_max"] is None else max(s["date_max"], dmax)
        progress("pass 1/2", t0, cum, i)
    log.info("PASS 1/2 done: %s rows read, %s kept after filter, %s repos found.",
             f"{cum:,}", f"{kept:,}", f"{len(stats):,}")
    return stats, cum


# ----------------------------------------------------------------------------
# PASS 2 -- build the repo x bucket matrix (fully vectorised)
# ----------------------------------------------------------------------------
def pass2_matrix(args, usecols, stats, total_rows):
    log.info("PASS 2/2: building repo x time-bucket matrix ...")
    matrix = defaultdict(lambda: defaultdict(int))
    prmax = {r: s["pr_max"] for r, s in stats.items() if s["pr_max"] is not None}
    t0 = time.time()
    for i, (chunk, cum) in enumerate(iter_chunks(args, usecols), 1):
        chunk = apply_filter(add_repo(chunk))
        repo = chunk["repo"]

        if args.axis == "pr":
            pr = pd.to_numeric(chunk["pullRequestNumber"], errors="coerce")
            pmx = repo.map(prmax)
            valid = pr.notna() & pmx.notna()
            if valid.any():
                prf = pr[valid].to_numpy()
                pmf = pmx[valid].to_numpy()
                span = pmf - 1.0
                denom = np.where(span > 0, span, 1.0)          # avoid 0-division warning
                pos = (prf - 1.0) / denom
                pos[span <= 0] = 0.0
                pos = pos.clip(0.0, 1.0)
                bucket = (pos * args.segments).astype(int)
                bucket[bucket >= args.segments] = args.segments - 1
                sub = pd.DataFrame({"repo": repo[valid].to_numpy(), "b": bucket})
                counts = sub.groupby(["repo", "b"]).size()
                for (rp, b), n in counts.items():
                    matrix[rp][int(b)] += int(n)
        else:
            dt = pd.to_datetime(chunk[args.date_col], errors="coerce", utc=True)
            months = dt.dt.strftime("%Y-%m")
            valid = months.notna()
            if valid.any():
                sub = pd.DataFrame({"repo": repo[valid].to_numpy(),
                                    "m": months[valid].to_numpy()})
                counts = sub.groupby(["repo", "m"]).size()
                for (rp, m), n in counts.items():
                    matrix[rp][m] += int(n)
        progress("pass 2/2", t0, cum, i, total=total_rows)
    log.info("PASS 2/2 done.")
    return matrix


# ----------------------------------------------------------------------------
# Metrics + human-readable pattern label
# ----------------------------------------------------------------------------
def _rebin(series, n_coarse):
    n = len(series)
    out = [0] * n_coarse
    for i, c in enumerate(series):
        out[min(int(i / n * n_coarse), n_coarse - 1)] += c
    return out


def classify(series):
    total = sum(series)
    n = len(series)
    if total == 0:
        return 0.0, 0.5, "—", ""
    com = sum((i / (n - 1)) * c for i, c in enumerate(series)) / total if n > 1 else 0.5
    coarse = _rebin(series, COARSE_BUCKETS)
    coverage = sum(1 for c in coarse if c > 0) / COARSE_BUCKETS
    if total == 1:
        label = "One-shot"
    elif coverage >= 0.70:
        label = "Continuous"
    elif com < 0.35:
        label = "Front-loaded (early)"
    elif com > 0.65:
        label = "Back-loaded (late)"
    else:
        label = "Sporadic / middle"
    mx = max(coarse) or 1
    spark = "".join(SPARK_CHARS[0] if c == 0
                    else SPARK_CHARS[min(8, 1 + int((c / mx) * 7))] for c in coarse)
    return coverage, com, label, spark


def build(args, stats, matrix):
    if args.axis == "pr":
        bucket_keys = list(range(args.segments))
        bucket_labels = [f"{round(100*i/args.segments)}–{round(100*(i+1)/args.segments)}%"
                         for i in bucket_keys]
    else:
        bucket_keys = sorted({k for d in matrix.values() for k in d})
        bucket_labels = [str(k) for k in bucket_keys]

    repos = []
    for repo, s in stats.items():
        if s["total"] < args.min_analyses:
            continue
        counts = [matrix.get(repo, {}).get(k, 0) for k in bucket_keys]
        coverage, com, label, spark = classify(counts)
        rec = {"repo": repo, "total": s["total"], "successful": s["successful"],
               "pattern": label, "coverage": round(coverage, 3),
               "com": round(com, 3), "spark": spark, "counts": counts}
        if args.axis == "pr":
            rec["pr_min"] = None if s["pr_min"] is None else int(s["pr_min"])
            rec["pr_max"] = None if s["pr_max"] is None else int(s["pr_max"])
        else:
            rec["first"] = None if s["date_min"] is None else s["date_min"].strftime("%Y-%m-%d")
            rec["last"] = None if s["date_max"] is None else s["date_max"].strftime("%Y-%m-%d")
        repos.append(rec)
    repos.sort(key=lambda r: r["total"], reverse=True)
    log.info("Kept %s repos with >= %d analyses.", f"{len(repos):,}", args.min_analyses)
    return repos, bucket_keys, bucket_labels


def write_csvs(args, repos, bucket_labels):
    sm = pd.DataFrame([{k: v for k, v in r.items() if k != "counts"} for r in repos])
    sm.to_csv(os.path.join(args.out, "repo_cadence_summary.csv"), index=False)
    mat = pd.DataFrame({r["repo"]: dict(zip(bucket_labels, r["counts"])) for r in repos}).T
    mat.index.name = "repo"
    mat.to_csv(os.path.join(args.out, "repo_segment_matrix.csv"))
    log.info("Wrote CSVs: repo_cadence_summary.csv, repo_segment_matrix.csv")


def draw_png(args, repos, bucket_labels, path):
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        from matplotlib.colors import LinearSegmentedColormap
    except Exception as e:
        log.warning("Skipping PNG heatmap (matplotlib unavailable: %s)", e)
        return
    sel = repos[:args.top]
    if not sel:
        return
    data = [r["counts"] for r in sel]
    fig_h = max(2.5, 0.32 * len(sel) + 1.4)
    fig, ax = plt.subplots(figsize=(max(8, 0.22 * len(bucket_labels) + 4), fig_h), dpi=130)
    cmap = LinearSegmentedColormap.from_list("s", ["#f5f7fa", "#9ecae1", "#3182bd", "#08306b"])
    ax.imshow(data, aspect="auto", cmap=cmap)
    step = max(1, len(bucket_labels) // 12)
    ax.set_xticks(range(0, len(bucket_labels), step))
    ax.set_xticklabels(bucket_labels[::step], rotation=45, ha="right", fontsize=7)
    ax.set_yticks(range(len(sel)))
    ax.set_yticklabels([r["repo"] for r in sel], fontsize=7)
    ax.set_xlabel("Position in repo life  (0% first PR  →  100% latest PR)"
                  if args.axis == "pr" else "Month", fontsize=9)
    mode = "ALL analyses" if QUALITY_GATE_FILTER is None else f"qualityGateStatus = {QUALITY_GATE_FILTER}"
    ax.set_title(f"SonarQube analyses per repo over time  ({mode})", fontsize=11)
    fig.tight_layout()
    fig.savefig(path, bbox_inches="tight")
    plt.close(fig)
    log.info("Wrote PNG heatmap: %s", path)


# ----------------------------------------------------------------------------
# Interactive, self-contained HTML timeline (no external dependencies / CDN).
# ----------------------------------------------------------------------------
HTML_TEMPLATE = r"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>__TITLE__</title>
<style>
  :root{--bg:#0f1419;--panel:#171c24;--ink:#e6edf3;--muted:#8b98a5;--line:#222b36;
        --c0:#11212e;--c1:#1d4e6b;--c2:#2f81b8;--c3:#5fb3e6;--c4:#aee0ff;}
  *{box-sizing:border-box}
  body{margin:0;background:var(--bg);color:var(--ink);
       font:14px/1.45 -apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif}
  header{padding:18px 22px;border-bottom:1px solid var(--line);position:sticky;top:0;
         background:var(--bg);z-index:5}
  h1{font-size:17px;margin:0 0 4px}
  .sub{color:var(--muted);font-size:12.5px}
  .controls{display:flex;gap:10px;flex-wrap:wrap;margin-top:12px;align-items:center}
  select,input{background:var(--panel);color:var(--ink);border:1px solid var(--line);
       border-radius:7px;padding:6px 9px;font-size:13px}
  input{min-width:180px}
  .legend{display:flex;align-items:center;gap:6px;color:var(--muted);font-size:12px;margin-left:auto}
  .swatch{width:13px;height:13px;border-radius:3px;display:inline-block}
  .wrap{padding:8px 22px 60px}
  .ruler{display:grid;grid-template-columns:300px 1fr;align-items:end;
         padding:6px 0 4px;color:var(--muted);font-size:11px;position:sticky;top:118px;
         background:var(--bg);z-index:4}
  .ruler .axis{display:flex;justify-content:space-between}
  .lane{display:grid;grid-template-columns:300px 1fr;align-items:center;gap:0;
        border-top:1px solid var(--line);padding:7px 0}
  .meta{padding-right:14px;overflow:hidden}
  .repo{font-weight:600;font-size:13px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
  .tags{display:flex;gap:7px;align-items:center;margin-top:3px;color:var(--muted);font-size:11.5px}
  .badge{padding:1.5px 7px;border-radius:20px;font-size:10.5px;font-weight:600;white-space:nowrap}
  .b-cont{background:#15412b;color:#7ee2a8}
  .b-front{background:#3a2f10;color:#f0c869}
  .b-back{background:#3a1726;color:#f08bb0}
  .b-spor{background:#2a2336;color:#c3a6ee}
  .b-one{background:#23303b;color:#9cc4dd}
  .strip{width:100%}
  .strip svg{display:block;width:100%;height:30px}
  .strip rect{shape-rendering:crispEdges}
  .num{font-variant-numeric:tabular-nums}
  #tip{position:fixed;pointer-events:none;background:#0b0e12;border:1px solid var(--line);
       border-radius:7px;padding:7px 9px;font-size:12px;color:var(--ink);opacity:0;
       transition:opacity .08s;z-index:20;box-shadow:0 6px 22px rgba(0,0,0,.5)}
  #tip b{color:#aee0ff}
  .empty{color:var(--muted);padding:30px 0;text-align:center}
</style></head>
<body>
<header>
  <h1>__TITLE__</h1>
  <div class="sub" id="subtitle"></div>
  <div class="controls">
    <label>Sort
      <select id="sort">
        <option value="total">by analyses (most first)</option>
        <option value="pattern">by pattern</option>
        <option value="com">by position (early→late)</option>
        <option value="repo">by name</option>
      </select>
    </label>
    <label>Colour
      <select id="cmode">
        <option value="repo">per-repo intensity (shape)</option>
        <option value="global">absolute count</option>
      </select>
    </label>
    <input id="search" placeholder="filter repos…" autocomplete="off">
    <span class="legend">fewer
      <span class="swatch" style="background:var(--c0)"></span>
      <span class="swatch" style="background:var(--c1)"></span>
      <span class="swatch" style="background:var(--c2)"></span>
      <span class="swatch" style="background:var(--c3)"></span>
      <span class="swatch" style="background:var(--c4)"></span> more</span>
  </div>
</header>
<div class="ruler" id="ruler"></div>
<div class="wrap" id="lanes"></div>
<div id="tip"></div>
<script>
const DATA = __DATA__;
const COLORS = ["#11212e","#1d4e6b","#2f81b8","#5fb3e6","#aee0ff"];
const BADGE = {"Continuous":"b-cont","Front-loaded (early)":"b-front",
  "Back-loaded (late)":"b-back","Sporadic / middle":"b-spor","One-shot":"b-one","—":"b-one"};
const tip = document.getElementById('tip');
const lanesEl = document.getElementById('lanes');
const globalMax = (()=>{let m=1;for(const r of DATA.repos){for(const c of r.counts){if(c>m)m=c;}}return m;})();

document.getElementById('subtitle').textContent =
  DATA.mode + "  ·  " + DATA.repos.length + " repos  ·  " +
  DATA.repos.reduce((a,r)=>a+r.total,0).toLocaleString() + " analyses  ·  x-axis: " + DATA.axisLabel;

function colorFor(v,max){
  if(v<=0) return COLORS[0];
  const t = Math.min(1, v/max);
  return COLORS[Math.min(COLORS.length-1, Math.round(t*(COLORS.length-1)))];
}

function strip(r, cmode){
  const n = r.counts.length;
  let max = 1; for(const c of r.counts){ if(c>max) max=c; }
  if(cmode==='global') max = globalMax;
  const w = 1000, h = 30;
  let cells = "";
  for(let i=0;i<n;i++){
    const x=(i/n*w).toFixed(2), bw=(w/n).toFixed(2);
    const c=colorFor(r.counts[i], max);
    cells += `<rect x="${x}" y="0" width="${bw}" height="${h}" fill="${c}" `
           + `data-i="${i}" data-c="${r.counts[i]}" data-repo="${r.repo}"></rect>`;
  }
  return `<div class="strip"><svg viewBox="0 0 ${w} ${h}" preserveAspectRatio="none">${cells}</svg></div>`;
}

function render(){
  const sort=document.getElementById('sort').value;
  const cmode=document.getElementById('cmode').value;
  const q=document.getElementById('search').value.trim().toLowerCase();
  let rows=DATA.repos.filter(r=>!q || r.repo.toLowerCase().includes(q));
  const cmp={
    total:(a,b)=>b.total-a.total,
    com:(a,b)=>a.com-b.com,
    repo:(a,b)=>a.repo.localeCompare(b.repo),
    pattern:(a,b)=>a.pattern.localeCompare(b.pattern)||b.total-a.total
  }[sort];
  rows=rows.slice().sort(cmp);
  if(!rows.length){ lanesEl.innerHTML='<div class="empty">No repos match.</div>'; return; }
  lanesEl.innerHTML=rows.map(r=>{
    const range=DATA.axis==='pr'
      ? (r.pr_min!=null ? `PR #${r.pr_min}–#${r.pr_max}` : '')
      : `${r.first||''} → ${r.last||''}`;
    const badge=BADGE[r.pattern]||'b-one';
    return `<div class="lane"><div class="meta">
        <div class="repo" title="${r.repo}">${r.repo}</div>
        <div class="tags"><span class="badge ${badge}">${r.pattern}</span>
          <span class="num">${r.total.toLocaleString()} analyses</span>
          <span>·</span><span class="num">${range}</span></div>
      </div>${strip(r,cmode)}</div>`;
  }).join("");
}

lanesEl.addEventListener('mousemove', e=>{
  const t=e.target;
  if(t.tagName!=='rect'){ tip.style.opacity=0; return; }
  const i=+t.dataset.i, c=+t.dataset.c;
  tip.innerHTML=`<b>${t.dataset.repo}</b><br>${DATA.bucketLabels[i]} · <b>${c}</b> analyses`;
  tip.style.opacity=1;
  let x=e.clientX+12, y=e.clientY+12;
  if(x+200>innerWidth) x=e.clientX-200;
  tip.style.left=x+'px'; tip.style.top=y+'px';
});
lanesEl.addEventListener('mouseleave', ()=>tip.style.opacity=0);

(function(){
  const r=document.getElementById('ruler'); let axis='';
  if(DATA.axis==='pr'){
    axis='<span>0% (first PR)</span><span>25%</span><span>50%</span><span>75%</span><span>100% (latest PR)</span>';
  }else{
    const L=DATA.bucketLabels;
    axis=`<span>${L[0]||''}</span><span>${L[Math.floor(L.length/2)]||''}</span><span>${L[L.length-1]||''}</span>`;
  }
  r.innerHTML=`<div></div><div class="axis">${axis}</div>`;
})();

['sort','cmode','search'].forEach(id=>
  document.getElementById(id).addEventListener('input', render));
render();
</script>
</body></html>"""


def write_html(args, repos, bucket_labels, path):
    mode = "ALL analyses (no filter)" if QUALITY_GATE_FILTER is None \
           else f"Only qualityGateStatus = {QUALITY_GATE_FILTER}"
    axis_label = ("position in repo life (PR-number based)"
                  if args.axis == "pr" else "calendar month")
    payload = {"mode": mode, "axis": args.axis, "axisLabel": axis_label,
               "bucketLabels": bucket_labels,
               "repos": [{k: v for k, v in r.items() if k != "spark"} for r in repos[:args.top]]}
    title = "SonarQube analysis cadence per repository  —  " + \
            ("ALL data" if QUALITY_GATE_FILTER is None else f"{QUALITY_GATE_FILTER} only")
    html = HTML_TEMPLATE.replace("__DATA__", json.dumps(payload)).replace("__TITLE__", title)
    with open(path, "w", encoding="utf-8") as f:
        f.write(html)
    log.info("Wrote interactive timeline: %s", path)


# ----------------------------------------------------------------------------
def main():
    args = parse_args()
    setup_logging(args.log_level, args.log_file)
    os.makedirs(args.out, exist_ok=True)
    usecols = read_cols(args)

    try:
        size_mb = os.path.getsize(args.input) / (1024 * 1024)
    except OSError:
        size_mb = float("nan")
    mode = "ALL rows" if QUALITY_GATE_FILTER is None else f"qualityGateStatus == '{QUALITY_GATE_FILTER}'"
    log.info("Input: %s (%.1f MB) | mode: %s", args.input, size_mb, mode)
    log.info("Config: axis=%s, segments=%d, chunksize=%s, columns=%s",
             args.axis, args.segments, f"{args.chunksize:,}", usecols)

    t_start = time.time()
    stats, total_rows = pass1_extents(args, usecols)
    if not stats:
        log.error("No rows matched. Nothing to do.")
        return
    matrix = pass2_matrix(args, usecols, stats, total_rows)
    repos, _, bucket_labels = build(args, stats, matrix)

    write_csvs(args, repos, bucket_labels)
    write_html(args, repos, bucket_labels, os.path.join(args.out, "cadence_timeline.html"))
    draw_png(args, repos, bucket_labels, os.path.join(args.out, "repo_cadence_heatmap.png"))

    log.info("ALL DONE in %s. Outputs in: %s", fmt_dur(time.time() - t_start), args.out)

    tag = "ALL" if QUALITY_GATE_FILTER is None else QUALITY_GATE_FILTER
    print(f"\n=== Cadence ({tag}) — top by analyses ===")
    cols = "repo total pattern coverage com spark".split()
    print(pd.DataFrame([{c: r[c] for c in cols} for r in repos]).head(args.top).to_string(index=False))


if __name__ == "__main__":
    main()
