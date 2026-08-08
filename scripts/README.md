# Scripts

Standalone Python utilities for analyzing Sonar/PR cadence data. Unrelated to the
backend's runtime — these operate on exported CSV data, not the live application.

## Setup

```bash
cd scripts
python -m venv .venv
.venv\Scripts\activate      # Windows
# source .venv/bin/activate # macOS/Linux
pip install -r requirements.txt
```

`matplotlib` is only needed for the optional PNG heatmap output; both scripts run
without it if it's not installed.

## Scripts

- `sonar_cadence_all.py` — per-repository cadence view counting **all** rows.
- `sonar_cadence_ok.py` — same view, but counting only rows where
  `qualityGateStatus == "OK"`.

Both are designed to stream a huge CSV (e.g. 3.5 GB) in chunks rather than load it
into memory. See the docstring at the top of each file for full usage, options,
and output details, e.g.:

```bash
python sonar_cadence_all.py --help
```

## Input data

Expects a CSV like `resources/github_pull_request_sonarqube_results_*.csv`
(ignored by git — not checked in due to size).
