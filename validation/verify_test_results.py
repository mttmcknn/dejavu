#!/usr/bin/env python3
"""Require executed JUnit tests, with no failures/errors/skips, in every report directory."""

import sys
from pathlib import Path
from xml.etree import ElementTree


def verify(directory: Path) -> bool:
    reports = sorted(directory.rglob("TEST-*.xml"))
    counts = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    for report in reports:
        root = ElementTree.parse(report).getroot()
        counts["tests"] += sum(1 for _ in root.iter("testcase"))
        for kind in ("failure", "error", "skipped"):
            key = {"failure": "failures", "error": "errors", "skipped": "skipped"}[kind]
            counts[key] += sum(1 for _ in root.iter(kind))
    passed = counts["tests"] > 0 and not any(counts[k] for k in ("failures", "errors", "skipped"))
    print(f"{'PASS' if passed else 'FAIL'} {directory}: {counts}")
    return passed


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("Usage: verify_test_results.py REPORT_DIRECTORY [REPORT_DIRECTORY ...]")
    results = [verify(Path(argument)) for argument in sys.argv[1:]]
    sys.exit(0 if all(results) else 1)
