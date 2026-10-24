#!/usr/bin/env python3
"""Assemble app/src/main/assets/veronelli_2026.json from the row files in tools/.

Row format (same columnar layout as the original Tasting_Ledger.html):
[nome, annata, cantina, regione, curatore, punti, ric, tipo, comune?, prov?, cat?, nota?]
"""
import json, glob, os, sys

here = os.path.dirname(os.path.abspath(__file__))
rows = []
for path in sorted(glob.glob(os.path.join(here, "veronelli_rows_*.jsonl"))):
    with open(path, encoding="utf-8") as f:
        for n, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as e:
                sys.exit(f"{path}:{n}: {e}")
            if not (8 <= len(row) <= 12):
                sys.exit(f"{path}:{n}: unexpected field count {len(row)}")
            rows.append(row)

out = {
    "edizione": "2026",
    "fonte": "Guida Oro I Vini di Veronelli 2026",
    "campi": ["nome", "annata", "cantina", "regione", "curatore", "punti", "ric", "tipo",
              "comune", "prov", "cat", "nota"],
    "vini": rows,
}
dest = os.path.join(here, "..", "app", "src", "main", "assets", "veronelli_2026.json")
with open(dest, "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
print(f"{len(rows)} wines written to {os.path.normpath(dest)}")
