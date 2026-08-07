#!/usr/bin/env python3
"""Produce the shipped SMuFL glyph-name map from the upstream spec metadata.

The full upstream glyphnames.json is ~296KB for 2940 glyphs; this app draws around
forty of them. Shipping the whole file would put 290KB of dead weight in the APK, so
the shipped asset is trimmed to exactly the glyphs listed in glyphs.txt.

Bravura.json (the font's own metrics) is NOT trimmed. It has to stay authoritative
because metrics change with the font version, and trimming it would create a second
place where glyph advance widths and bounding boxes live.

Usage:
    tools/smufl/trim-metadata.py <upstream-glyphnames.json> [--check]

    --check  verify the shipped asset matches what this script would produce, and
             exit non-zero if not. Suitable for CI.

Upstream source:
    https://raw.githubusercontent.com/w3c/smufl/gh-pages/metadata/glyphnames.json
"""
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parent.parent
MANIFEST = HERE / "glyphs.txt"
SHIPPED = REPO / "app/src/main/assets/smufl/glyphnames.json"


def wanted_glyphs() -> list[str]:
    names = []
    for line in MANIFEST.read_text().splitlines():
        line = line.split("#", 1)[0].strip()
        if line:
            names.append(line)
    duplicates = {n for n in names if names.count(n) > 1}
    if duplicates:
        sys.exit(f"glyphs.txt lists these more than once: {sorted(duplicates)}")
    return names


def trim(upstream_path: pathlib.Path) -> dict:
    upstream = json.loads(upstream_path.read_text())
    names = wanted_glyphs()
    missing = [n for n in names if n not in upstream]
    if missing:
        sys.exit(f"not SMuFL glyph names: {missing}")
    return {n: {"codepoint": upstream[n]["codepoint"]} for n in sorted(names)}


def main() -> None:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) != 1:
        sys.exit(__doc__)
    trimmed = trim(pathlib.Path(args[0]))
    rendered = json.dumps(trimmed, indent=2, sort_keys=True) + "\n"

    if "--check" in sys.argv:
        if not SHIPPED.exists():
            sys.exit(f"{SHIPPED} does not exist")
        if SHIPPED.read_text() != rendered:
            sys.exit(f"{SHIPPED} is stale — re-run without --check")
        print(f"ok — {len(trimmed)} glyphs, shipped asset is current")
        return

    SHIPPED.parent.mkdir(parents=True, exist_ok=True)
    SHIPPED.write_text(rendered)
    print(f"wrote {SHIPPED} — {len(trimmed)} glyphs")


if __name__ == "__main__":
    main()
