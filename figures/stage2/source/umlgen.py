"""Small SVG generator for the Stage 2 UML redraws (class and sequence diagrams).

Diagrams are described in classes.py and sequences.py; render.js turns the SVG files into PNGs at
2x scale with Playwright's Chromium. Run from the repository root:

    python3 figures/stage2/source/build.py

The look follows the Stage 1 figures: DejaVu Sans Mono, grey class headers, teal pattern roles and
combined-fragment frames.
"""
from html import escape

FONT = "DejaVu Sans Mono, monospace"
SANS = "DejaVu Sans, sans-serif"
INK = "#111820"
MUTED = "#56606b"
TEAL = "#0b5e55"
TEAL_FILL = "#d7ece9"
HEAD = "#e8ebf0"
ACT = "#e1e5ec"
PKG = "#f1f3f6"
PKG_LINE = "#9aa3ad"
CH = 8.43  # width of one 14px DejaVu Sans Mono character


def text_w(s, size=14):
    return len(s) * CH * size / 14


def t(x, y, s, size=14, weight="normal", fill=INK, anchor="start", italic=False, family=FONT):
    style = ' font-style="italic"' if italic else ""
    return (f'<text x="{x:.1f}" y="{y:.1f}" font-family="{family}" font-size="{size}" font-weight="{weight}" '
            f'fill="{fill}" text-anchor="{anchor}"{style}>{escape(s)}</text>')


def svg_doc(w, h, body, bg="#ffffff"):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w:.0f}" height="{h:.0f}" '
            f'viewBox="0 0 {w:.0f} {h:.0f}"><rect width="100%" height="100%" fill="{bg}"/>' + "".join(body) + "</svg>")


# ---------------------------------------------------------------- class diagrams

class Box:
    """A class box. fields/methods are UML lines such as "+ save(session: StorySession): void"."""

    def __init__(self, name, x, y, fields=(), methods=(), stereo=None, role=None, ref=False, compact=False):
        self.name, self.x, self.y = name, x, y
        self.fields, self.methods = list(fields), list(methods)
        self.stereo, self.role, self.ref, self.compact = stereo, role, ref, compact
        self.single = stereo == "enumeration" and not self.methods  # one compartment of literals
        lines = [name] + self.fields + self.methods
        widths = [text_w(name, 16) + 40] + [text_w(l) + 40 for l in self.fields + self.methods]
        if role:
            widths.append(text_w(role, 11) + 30)
        self.w = max(widths + [120])
        self.head_h = 30 + (18 if stereo else 0) + (18 if role else 0) + 8
        if ref or compact:
            self.h = self.head_h
        elif self.single:
            self.h = self.head_h + len(self.fields) * 24 + 16
        else:
            self.h = self.head_h + max(len(self.fields), 0) * 24 + 16 + len(self.methods) * 24 + 12

    def anchor(self, side, frac=0.5):
        if side == "l":
            return self.x, self.y + self.h * frac
        if side == "r":
            return self.x + self.w, self.y + self.h * frac
        if side == "t":
            return self.x + self.w * frac, self.y
        return self.x + self.w * frac, self.y + self.h

    def svg(self):
        out = []
        dash = ' stroke-dasharray="7 5"' if self.ref else ""
        out.append(f'<rect x="{self.x}" y="{self.y}" width="{self.w:.1f}" height="{self.h:.1f}" fill="{HEAD}" '
                   f'stroke="{INK}" stroke-width="1.6"{dash}/>')
        cx = self.x + self.w / 2
        y = self.y + 24
        if self.stereo:
            out.append(t(cx, y, f"«{self.stereo}»", 12, fill=MUTED, anchor="middle"))
            y += 20
        out.append(t(cx, y + 2, self.name, 16, "bold", anchor="middle"))
        y += 20
        if self.role:
            out.append(t(cx, y, self.role, 11, fill=TEAL, anchor="middle"))
        if self.ref or self.compact:
            return "".join(out)
        y0 = self.y + self.head_h
        fh = len(self.fields) * 24 + 8
        out.append(f'<rect x="{self.x}" y="{y0:.1f}" width="{self.w:.1f}" height="{self.h - self.head_h:.1f}" '
                   f'fill="#ffffff" stroke="{INK}" stroke-width="1.6"/>')
        for i, f in enumerate(self.fields):
            out.append(t(self.x + 18, y0 + 22 + i * 24, f))
        if self.single:
            return "".join(out)
        y1 = y0 + fh + (8 if self.fields else 0)
        out.append(f'<line x1="{self.x}" y1="{y1:.1f}" x2="{self.x + self.w:.1f}" y2="{y1:.1f}" stroke="{INK}" stroke-width="1.6"/>')
        for i, m in enumerate(self.methods):
            out.append(t(self.x + 18, y1 + 24 + i * 24, m))
        return "".join(out)


def _head(kind, x, y, dx, dy):
    """Arrow head at (x, y) pointing in direction (dx, dy)."""
    import math
    L = math.hypot(dx, dy) or 1
    ux, uy = dx / L, dy / L
    px, py = -uy, ux
    if kind == "open":
        a = (x - ux * 14 + px * 7, y - uy * 14 + py * 7)
        b = (x - ux * 14 - px * 7, y - uy * 14 - py * 7)
        return f'<polyline points="{a[0]:.1f},{a[1]:.1f} {x:.1f},{y:.1f} {b[0]:.1f},{b[1]:.1f}" fill="none" stroke="{INK}" stroke-width="1.6"/>'
    if kind == "triangle":
        a = (x - ux * 18 + px * 10, y - uy * 18 + py * 10)
        b = (x - ux * 18 - px * 10, y - uy * 18 - py * 10)
        return f'<polygon points="{a[0]:.1f},{a[1]:.1f} {x:.1f},{y:.1f} {b[0]:.1f},{b[1]:.1f}" fill="#ffffff" stroke="{INK}" stroke-width="1.6"/>'
    if kind in ("diamond", "odiamond"):
        m = (x - ux * 14 + px * 7, y - uy * 14 + py * 7)
        e = (x - ux * 28, y - uy * 28)
        n = (x - ux * 14 - px * 7, y - uy * 14 - py * 7)
        fill = INK if kind == "diamond" else "#ffffff"
        return (f'<polygon points="{x:.1f},{y:.1f} {m[0]:.1f},{m[1]:.1f} {e[0]:.1f},{e[1]:.1f} {n[0]:.1f},{n[1]:.1f}" '
                f'fill="{fill}" stroke="{INK}" stroke-width="1.6"/>')
    if kind == "filled":
        a = (x - ux * 14 + px * 6, y - uy * 14 + py * 6)
        b = (x - ux * 14 - px * 6, y - uy * 14 - py * 6)
        return f'<polygon points="{a[0]:.1f},{a[1]:.1f} {x:.1f},{y:.1f} {b[0]:.1f},{b[1]:.1f}" fill="{INK}"/>'
    return ""


def link(points, kind, label=None, mult=None, label_at=0.5):
    """A connector through the given points (first is the source).

    kind: assoc (open arrow at target), dep (dashed open arrow), real (dashed, hollow triangle),
    gen (solid, hollow triangle), comp (filled diamond at source, open arrow at target),
    agg (hollow diamond at source), line (plain).
    """
    dashed = kind in ("dep", "real")
    pts = " ".join(f"{x:.1f},{y:.1f}" for x, y in points)
    out = [f'<polyline points="{pts}" fill="none" stroke="{INK}" stroke-width="1.6"'
           + (' stroke-dasharray="7 5"' if dashed else "") + "/>"]
    (x1, y1), (x0, y0) = points[-1], points[-2]
    if kind in ("assoc", "dep", "comp", "agg"):
        out.append(_head("open", x1, y1, x1 - x0, y1 - y0))
    if kind in ("real", "gen"):
        out.append(_head("triangle", x1, y1, x1 - x0, y1 - y0))
    if kind in ("comp", "agg"):
        (sx, sy), (nx, ny) = points[0], points[1]
        out.append(_head("diamond" if kind == "comp" else "odiamond", sx, sy, sx - nx, sy - ny))
    if label:
        i = max(0, min(len(points) - 2, int(label_at * (len(points) - 1))))
        (ax, ay), (bx, by) = points[i], points[i + 1]
        out.append(t((ax + bx) / 2 + 4, (ay + by) / 2 - 6, label, 12, fill=MUTED, italic=True,
                     anchor="middle" if ay == by else "start"))
    if mult:  # placed just outside the target end, beside the line
        if y1 == y0:
            mx = x1 - 20 - text_w(mult, 12) if x1 >= x0 else x1 + 20
            out.append(t(mx, y1 - 10, mult, 12))
        else:
            out.append(t(x1 + 12, y1 - 12 if y1 > y0 else y1 + 26, mult, 12))
    return "".join(out)


def class_diagram(title, subtitle, boxes, links, w, h):
    body = [f'<rect x="12" y="12" width="{w - 24}" height="{h - 24}" rx="12" fill="{PKG}" stroke="{PKG_LINE}" stroke-width="1.6"/>',
            t(40, 60, title, 22, "bold"), t(48 + text_w(title, 22), 60, subtitle, 18, fill=MUTED, family=SANS)]
    body += links
    body += [b.svg() for b in boxes]
    return svg_doc(w, h, body, PKG)


# ---------------------------------------------------------------- sequence diagrams

class Seq:
    """A sequence diagram built step by step; render() lays it out."""

    def __init__(self, participants, gap=60):
        self.parts = participants  # list of names; "User" is drawn as an actor
        self.steps = []
        self.gap = gap

    def call(self, a, b, label):
        self.steps.append(("call", a, b, label))
        return self

    def ret(self, a, b, label=""):
        self.steps.append(("ret", a, b, label))
        return self

    def self_(self, a, label):
        self.steps.append(("self", a, a, label))
        return self

    def done(self, a):
        """Ends a's outermost activation (a front end that has finished handling the user's action)."""
        self.steps.append(("done", a, a, ""))
        return self

    def note(self, a, text):
        self.steps.append(("note", a, a, text))
        return self

    def frame(self, kind, cond):
        self.steps.append(("frame", kind, None, cond))
        return self

    def orelse(self, cond="[else]"):
        self.steps.append(("else", None, None, cond))
        return self

    def end(self):
        self.steps.append(("end", None, None, ""))
        return self

    def render(self):
        names = self.parts
        idx = {n: i for i, n in enumerate(names)}
        bw = [10 if n == "*" else max(text_w(n, 15) + 34, 90) for n in names]
        need = [0.0] * len(names)  # min distance between centre i-1 and i
        for i in range(1, len(names)):
            need[i] = bw[i - 1] / 2 + bw[i] / 2 + self.gap
        for kind, a, b, label in self.steps:
            if kind in ("call", "ret") and a != b:
                i, j = sorted((idx[a], idx[b]))
                lw = text_w(label, 14) + 40
                span = sum(need[i + 1:j + 1])
                if span < lw:
                    need[j] += lw - span
            if kind == "self":
                i = idx[a]
                lw = text_w(label, 14) + 70
                if i + 1 < len(names):
                    need[i + 1] = max(need[i + 1], min(lw, need[i + 1] + 60))
        xs, x = [], (24 if names[0] == "*" else 70)
        for i in range(len(names)):
            x += need[i] if i else 0
            xs.append(x)
        width = xs[-1] + max(bw[-1] / 2 + 40, 60)
        for kind, a, b, label in self.steps:
            if kind in ("self", "note") and idx[a] == len(names) - 1:
                width = max(width, xs[-1] + text_w(label, 14) + 80)

        body, y = [], 160
        stacks = {n: [] for n in names}  # open activations: (start y, depth)
        bars = []
        frames, frame_stack = [], []

        def depth(n):
            return len(stacks[n])

        def bar_x(n, d=None):
            d = depth(n) if d is None else d
            return xs[idx[n]] + max(0, d - 1) * 8

        def touch(*ns, right=None):
            for f in frame_stack:
                for n in ns:
                    f["xs"].append(xs[idx[n]])
                if right is not None:
                    f["xs"].append(right - 30)

        msgs = []
        snapshots = []
        for kind, a, b, label in self.steps:
            if kind == "call":
                if not stacks[a] and a not in ("User", "*"):
                    stacks[a].append(y - 10)
                xa = bar_x(a) + (8 if a not in ("User", "*") else 0)
                stacks[b].append(y)
                xb = bar_x(b) - 8 if xs[idx[b]] > xs[idx[a]] else bar_x(b) + 8
                if xs[idx[b]] < xs[idx[a]]:
                    xa = bar_x(a) - 8
                msgs.append(("solid", xa, xb, y, label))
                touch(a, b)
                y += 46
            elif kind == "ret":
                start = stacks[a].pop() if stacks[a] else y - 30
                d = depth(a) + 1
                bx = xs[idx[a]] + max(0, d - 1) * 8
                bars.append((bx, start, y + 4))
                left = xs[idx[b]] < xs[idx[a]]
                xa = bx - 8 if left else bx + 8
                xb = bar_x(b) + 8 if left else bar_x(b) - 8
                if b in ("User", "*"):
                    xb = xs[idx[b]] + (30 if b == "*" else 0)
                msgs.append(("dash", xa, xb, y, label))
                touch(a, b)
                y += 46
            elif kind == "self":
                if not stacks[a]:
                    stacks[a].append(y - 10)
                bx = bar_x(a) + 8
                msgs.append(("self", bx, bx, y, label))
                touch(a, right=bx + 38 + text_w(label))
                y += 58
            elif kind == "done":
                while stacks[a]:
                    d = depth(a)
                    bars.append((xs[idx[a]] + max(0, d - 1) * 8, stacks[a].pop(), y - 16))
                y += 6
            elif kind == "note":
                msgs.append(("note", bar_x(a) + 10, 0, y - 18, label))
                touch(a, right=bar_x(a) + 10 + text_w(label, 13))
                y += 24
            elif kind == "frame":
                snapshots.append({n: list(v) for n, v in stacks.items()})
                frame_stack.append({"kind": a, "cond": label, "y": y - 8, "elses": [], "xs": [], "depth": len(frame_stack)})
                y += 46
            elif kind == "else":
                for n, v in snapshots[-1].items():
                    while len(stacks[n]) > len(v) or stacks[n] != v[:len(stacks[n])]:
                        if not stacks[n]:
                            break
                        d = depth(n)
                        bars.append((xs[idx[n]] + max(0, d - 1) * 8, stacks[n].pop(), y - 10))
                    stacks[n] = list(v)
                frame_stack[-1]["elses"].append((y - 4, label))
                y += 44
            elif kind == "end":
                snapshots.pop()
                f = frame_stack.pop()
                f["y2"] = y - 6
                frames.append(f)
                for g in frame_stack:
                    g["xs"] += f["xs"]
                y += 18
        for n in names:
            while stacks[n] and n != "*":
                d = depth(n)
                bars.append((xs[idx[n]] + max(0, d - 1) * 8, stacks[n].pop(), y - 10))
        height = y + 40

        # frames (the rectangles now; tags and conditions on top at the end)
        tags = []
        for f in sorted(frames, key=lambda f: f["depth"]):
            pad = 46 - f["depth"] * 10
            x1 = min(f["xs"]) - pad
            x2 = max(f["xs"]) + pad
            x2 = max(x2, x1 + text_w(f["cond"], 13) + 120)
            body.append(f'<rect x="{x1:.1f}" y="{f["y"]:.1f}" width="{x2 - x1:.1f}" height="{f["y2"] - f["y"]:.1f}" '
                        f'fill="none" stroke="{TEAL}" stroke-width="1.6"/>')
            tw = text_w(f["kind"], 13) + 22
            tags.append(f'<polygon points="{x1:.1f},{f["y"]:.1f} {x1 + tw:.1f},{f["y"]:.1f} {x1 + tw:.1f},{f["y"] + 18:.1f} '
                        f'{x1 + tw - 8:.1f},{f["y"] + 26:.1f} {x1:.1f},{f["y"] + 26:.1f}" fill="{TEAL_FILL}" stroke="{TEAL}" stroke-width="1.4"/>')
            tags.append(t(x1 + 10, f["y"] + 18, f["kind"], 13, "bold"))
            tags.append(t(x1 + tw + 12, f["y"] + 18, f["cond"], 13, family=SANS))
            for ey, cond in f["elses"]:
                body.append(f'<line x1="{x1:.1f}" y1="{ey:.1f}" x2="{x2:.1f}" y2="{ey:.1f}" stroke="{TEAL}" stroke-width="1.4" stroke-dasharray="7 5"/>')
                tags.append(t(x1 + tw + 12, ey + 18, cond, 13, family=SANS))
            width = max(width, x2 + 20)

        # lifelines and heads
        for n, x in zip(names, xs):
            if n == "*":
                continue
            body.append(f'<line x1="{x}" y1="{90 if n != "User" else 140}" x2="{x}" y2="{height - 20}" stroke="{MUTED}" stroke-width="1.4" stroke-dasharray="6 5"/>')
            if n == "User":
                body.append(f'<circle cx="{x}" cy="40" r="14" fill="none" stroke="{INK}" stroke-width="1.8"/>'
                            f'<line x1="{x}" y1="54" x2="{x}" y2="92" stroke="{INK}" stroke-width="1.8"/>'
                            f'<line x1="{x - 22}" y1="66" x2="{x + 22}" y2="66" stroke="{INK}" stroke-width="1.8"/>'
                            f'<polyline points="{x - 18},{120} {x},{92} {x + 18},{120}" fill="none" stroke="{INK}" stroke-width="1.8"/>')
                body.append(t(x, 140, "User", 15, "bold", anchor="middle"))
            else:
                w = bw[idx[n]]
                body.append(f'<rect x="{x - w / 2:.1f}" y="60" width="{w:.1f}" height="38" fill="#ffffff" stroke="{INK}" stroke-width="1.6"/>')
                body.append(t(x, 85, n, 15, "bold", anchor="middle"))
        for bx, y1, y2 in sorted(bars, key=lambda b: b[0]):
            body.append(f'<rect x="{bx - 7:.1f}" y="{y1:.1f}" width="14" height="{max(y2 - y1, 12):.1f}" fill="{ACT}" stroke="{INK}" stroke-width="1.3"/>')
        for kind, xa, xb, y0, label in msgs:
            if kind == "self":
                body.append(f'<polyline points="{xa - 1:.1f},{y0} {xa + 28:.1f},{y0} {xa + 28:.1f},{y0 + 22} {xa + 2:.1f},{y0 + 22}" '
                            f'fill="none" stroke="{INK}" stroke-width="1.6"/>')
                body.append(_head("filled", xa, y0 + 22, -1, 0))
                body.append(t(xa + 38, y0 + 4, label))
            elif kind == "note":
                body.append(t(xa, y0 + 8, label, 13, fill=MUTED, italic=True))
            else:
                dash = ' stroke-dasharray="7 5"' if kind == "dash" else ""
                body.append(f'<line x1="{xa:.1f}" y1="{y0}" x2="{xb:.1f}" y2="{y0}" stroke="{INK}" stroke-width="1.6"{dash}/>')
                body.append(_head("filled" if kind == "solid" else "open", xb, y0, xb - xa, 0))
                if kind == "solid" and xa == xs[0] and names[0] == "*":
                    body.append(f'<circle cx="{xa}" cy="{y0}" r="5" fill="{INK}"/>')
                if label:
                    body.append(t((xa + xb) / 2, y0 - 9, label, 14, anchor="middle"))
        return svg_doc(width, height, body + tags)
