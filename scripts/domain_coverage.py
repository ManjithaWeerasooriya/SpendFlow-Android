#!/usr/bin/env python3
"""Print JaCoCo LINE coverage for the domain packages as a markdown table.

Usage: domain_coverage.py <jacoco-report.xml>
Exits 1 only if the report is missing/unreadable or has no domain packages; being under
TARGET only prints a warning (the 80% is a target, not a gate).
"""
import re
import sys
import xml.etree.ElementTree as ET

PREFIX = "com/example/spendflow/domain"
TARGET = 80.0


def main(path):
    try:
        with open(path, encoding="utf-8") as f:
            # JaCoCo XML starts with a DOCTYPE pointing at report.dtd. ElementTree never fetches
            # external DTDs, but drop the line anyway so no parser can try.
            text = re.sub(r"<!DOCTYPE[^>]*>", "", f.read())
        root = ET.fromstring(text)
    except (OSError, ET.ParseError) as e:
        print(f"Coverage report unreadable: {path}: {e}", file=sys.stderr)
        return 1

    rows = []
    for pkg in root.iter("package"):
        name = pkg.get("name", "")
        if not name.startswith(PREFIX):
            continue
        # Direct children only: the report also repeats counters inside every class/method.
        c = next((x for x in pkg.findall("counter") if x.get("type") == "LINE"), None)
        if c is None:
            continue
        rows.append((name, int(c.get("covered")), int(c.get("missed"))))

    if not rows:
        print(f"No packages under {PREFIX} in {path}", file=sys.stderr)
        return 1

    def pct(cov, miss):
        return 100.0 * cov / (cov + miss) if cov + miss else 100.0

    print("### Domain line coverage\n")
    print("| Package | Covered | Missed | Coverage |")
    print("|---|---:|---:|---:|")
    for name, cov, miss in sorted(rows):
        print(f"| {name} | {cov} | {miss} | {pct(cov, miss):.1f}% |")
    cov = sum(r[1] for r in rows)
    miss = sum(r[2] for r in rows)
    total = pct(cov, miss)
    print(f"| **Total** | {cov} | {miss} | **{total:.1f}%** |")
    if total < TARGET:
        print(f"\n:warning: Domain coverage {total:.1f}% is below the {TARGET:.0f}% target.")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    sys.exit(main(sys.argv[1]))
