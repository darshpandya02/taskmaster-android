#!/usr/bin/env python3
"""Prints per-class test counts from Gradle's JUnit XML results.

usage: python3 scripts/test-summary.py unit|connected
"""
import glob
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

DIRS = {
    "unit": "app/build/test-results/testDebugUnitTest/*.xml",
    "connected": "app/build/outputs/androidTest-results/connected/debug/*.xml",
}

counts = defaultdict(lambda: [0, 0])  # class -> [tests, failed]
for path in glob.glob(DIRS[sys.argv[1]]):
    for case in ET.parse(path).getroot().iter("testcase"):
        name = case.get("classname").rsplit(".", 1)[-1]
        counts[name][0] += 1
        if case.find("failure") is not None or case.find("error") is not None:
            counts[name][1] += 1

total = sum(t for t, _ in counts.values())
failed = sum(f for _, f in counts.values())
for name, (t, f) in sorted(counts.items()):
    print(f"  {name:<34} {t:>3} tests  {t - f:>3} passed  {f} failed")
print(f"  {'total':<34} {total:>3} tests  {total - failed:>3} passed  {failed} failed")
sys.exit(1 if failed or not total else 0)
