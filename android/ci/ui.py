"""Prints the centre of the first UI node whose text or description matches. Usage: ui.py dump.xml "Label" """
import re
import sys
import xml.etree.ElementTree as ET

path, target = sys.argv[1], sys.argv[2]
nodes = list(ET.parse(path).getroot().iter("node"))


def label(n):
    return (n.get("text") or "", n.get("content-desc") or "")


match = next((n for n in nodes if target in label(n)), None)
if match is None:
    low = target.lower()
    match = next((n for n in nodes if any(low in part.lower() for part in label(n))), None)
if match is None:
    sys.exit(1)
x1, y1, x2, y2 = map(int, re.findall(r"\d+", match.get("bounds")))
print((x1 + x2) // 2, (y1 + y2) // 2)
