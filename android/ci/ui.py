"""Prints the centre of the UI node best matching a label. Usage: ui.py dump.xml "Label"

Compose merges a clickable row's texts into one node ("Music, 2 folders • 6 songs"), so a node
matches exactly when any of its comma/newline separated parts equals the label.
"""
import re
import sys
import xml.etree.ElementTree as ET

path, target = sys.argv[1], sys.argv[2]
nodes = list(ET.parse(path).getroot().iter("node"))


def parts(n):
    text = (n.get("text") or "") + "\n" + (n.get("content-desc") or "")
    return [p.strip() for p in re.split(r"[,\n]", text) if p.strip()]


def area(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    return (x2 - x1) * (y2 - y1)


exact = [n for n in nodes if target in parts(n) and area(n) > 0]
loose = [n for n in nodes if any(target.lower() in p.lower() for p in parts(n)) and area(n) > 0]
candidates = exact or loose
if not candidates:
    sys.exit(1)
# Prefer clickable nodes, then the smallest one (the most specific element).
candidates.sort(key=lambda n: (n.get("clickable") != "true", area(n)))
x1, y1, x2, y2 = map(int, re.findall(r"\d+", candidates[0].get("bounds")))
print((x1 + x2) // 2, (y1 + y2) // 2)
