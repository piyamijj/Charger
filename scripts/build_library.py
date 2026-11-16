#!/usr/bin/env python3
import csv
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
entries = []
with (root / "scripts/library.tsv").open(encoding="utf-8", newline="") as source:
    for row in csv.reader(source, delimiter="|"):
        category, author, work, persian, turkish = row
        entries.append(dict(category=category, author=author, source=work, fa=persian.replace("\\n", "\n"), tr=turkish.replace("\\n", "\n")))
(root / "app/src/main/assets/library.json").write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"{len(entries)} entries")
