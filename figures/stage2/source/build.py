"""Writes every Stage 2 redraw as SVG, then renders PNGs into figures/stage2/ (see umlgen.py).

    python3 figures/stage2/source/build.py [name-filter]
"""
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import sequences  # noqa: E402

try:
    import classes  # noqa: E402
    CLASSES = classes.ALL
except ImportError:
    CLASSES = {}

OUT = os.path.dirname(HERE)
only = sys.argv[1] if len(sys.argv) > 1 else ""
tmp = tempfile.mkdtemp()
jobs = []
for name, make in sequences.ALL.items():
    if only in name:
        jobs.append((name, make().render(), os.path.join(OUT, "sequence-diagrams", name + ".png")))
for name, make in CLASSES.items():
    if only in name:
        jobs.append((name, make(), os.path.join(OUT, "class-diagram", name + ".png")))
args = []
for name, svg, png in jobs:
    path = os.path.join(tmp, name + ".svg")
    with open(path, "w", encoding="utf-8") as f:
        f.write(svg)
    args += [path, png]
subprocess.run(["node", os.path.join(HERE, "render.js")] + args, check=True)
print("\n".join(png for _, _, png in jobs))
