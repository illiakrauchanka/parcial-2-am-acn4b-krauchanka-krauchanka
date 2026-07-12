#!/usr/bin/env python3
"""MedTraveler catalog updater.

Scrapes the official controlled-substance lists of the app's five countries
(AR ANMAT, BR ANVISA, JP MHLW, SG HSA, AE MOHAP), diffs them against the app
catalog (app/src/main/assets/meds_seed.json) and writes a review report plus
ready-to-fill JSON stubs for substances the catalog does not know yet.

The catalog file is NEVER modified.

Dependencies:
    pip install requests beautifulsoup4 pdfplumber openpyxl

Usage:
    python3 tools/update_catalog.py                       # all countries
    python3 tools/update_catalog.py --countries AR,BR
    python3 tools/update_catalog.py --offline             # parse from tools/cache/
    python3 tools/update_catalog.py --catalog PATH --out DIR

Outputs (default tools/out/):
    report.md   NEW / MISSING-FROM-SOURCE / STATUS-CHANGED / SOURCE-UNAVAILABLE
    stubs.json  catalog-schema items for NEW substances (TODO fields to fill by hand)

Exit code: 0 if at least one source succeeded, 1 when every source failed.
"""

import argparse
import collections
import json
import pathlib
import re
import sys
import unicodedata

TOOLS_DIR = pathlib.Path(__file__).resolve().parent
CACHE_DIR = TOOLS_DIR / "cache"
DEFAULT_OUT = TOOLS_DIR / "out"
DEFAULT_CATALOG = TOOLS_DIR.parent / "app/src/main/assets/meds_seed.json"

Substance = collections.namedtuple("Substance", "name status source_url")
AdapterResult = collections.namedtuple("AdapterResult", "cc substances error")


class ParserFailure(RuntimeError):
    """A source answered but the parser extracted nothing usable — treat as
    a broken parser (markup changed), never as 'no restrictions exist'."""


# Salt/ester suffixes and connectors stripped from substance names so that
# 'Tramadol clorhidrato' and 'Tramadol hydrochloride' compare equal to 'Tramadol'.
_SALT_WORDS = {
    "clorhidrato",
    "hidrocloruro",
    "hydrochloride",
    "hcl",
    "fosfato",
    "phosphate",
    "sulfato",
    "sulfate",
    "sulphate",
    "tartrato",
    "tartrate",
    "citrato",
    "citrate",
    "maleato",
    "maleate",
    "bromhidrato",
    "hydrobromide",
    "acetato",
    "acetate",
    "nitrato",
    "nitrate",
    "de",
    "of",
}


def normalize_name(s):
    """Lowercase, accent-strip, drop salt words, collapse non-word chars."""
    if not s:
        return ""
    s = unicodedata.normalize("NFKD", s)
    s = "".join(c for c in s if not unicodedata.combining(c))
    s = s.lower()
    s = re.sub(r"[^\w]+", " ", s, flags=re.UNICODE)
    words = [w for w in s.split() if w not in _SALT_WORDS]
    return " ".join(words)


def require_nonempty(substances, message):
    if not substances:
        raise ParserFailure(message)
    return substances


def _catalog_keys(item):
    """All normalized names a catalog item is known by."""
    keys = set()
    for field in ("name", "activeSubstance", "nameLatin"):
        v = item.get(field)
        if isinstance(v, str) and v.strip():
            keys.add(normalize_name(v))
    names = item.get("names")
    if isinstance(names, dict):
        for v in names.values():
            if isinstance(v, str) and v.strip():
                keys.add(normalize_name(v))
    keys.discard("")
    return keys


def diff_catalog(catalog_items, scraped):
    """Compare catalog items with scraped substances (matched by normalized name)."""
    scraped_by_key = {
        normalize_name(s.name): s for s in scraped if normalize_name(s.name)
    }
    matched_keys = set()
    missing, status_changed = [], []
    for item in catalog_items:
        keys = _catalog_keys(item)
        hits = [scraped_by_key[k] for k in keys if k in scraped_by_key]
        if not hits:
            missing.append(item)
            continue
        matched_keys.update(k for k in keys if k in scraped_by_key)
        sub = hits[0]
        if sub.status != item.get("status"):
            status_changed.append((item, sub))
    new = [s for k, s in scraped_by_key.items() if k not in matched_keys]
    return {"new": new, "missing": missing, "status_changed": status_changed}


def make_stub(cc, sub):
    slug = re.sub(r"\s+", "_", normalize_name(sub.name))
    return {
        "id": f"{cc.lower()}-auto-{slug}",
        "name": sub.name,
        "status": sub.status,
        "activeSubstance": sub.name,
        "group": "TODO",
        "prescription": "TODO",
        "brand": "TODO",
        "description": "TODO",
        "lawExcerpt": "TODO",
        "penalty": "TODO",
        "sourceUrl": sub.source_url,
        "imageUrl": "TODO",
    }


def render_report(results):
    """results: {cc: diff-dict} or {cc: 'error string'} -> markdown."""
    lines = ["# Catalog update report", ""]
    for cc in sorted(results):
        r = results[cc]
        lines.append(f"## {cc}")
        lines.append("")
        if isinstance(r, str):
            lines.append(f"**SOURCE-UNAVAILABLE**: {r}")
            lines.append("")
            continue
        lines.append(f"### NEW ({len(r['new'])})")
        for s in r["new"]:
            lines.append(f"- {s.name} — {s.status} — {s.source_url}")
        lines.append(f"### MISSING-FROM-SOURCE ({len(r['missing'])})")
        for item in r["missing"]:
            lines.append(f"- {item.get('id')} ({item.get('name')})")
        lines.append(f"### STATUS-CHANGED ({len(r['status_changed'])})")
        for item, sub in r["status_changed"]:
            lines.append(
                f"- {item.get('id')} ({item.get('name')}): "
                f"{item.get('status')} -> {sub.status}"
            )
        lines.append("")
    return "\n".join(lines) + "\n"


def fetch_cached(url, cache_name, offline):
    """GET with on-disk cache. Offline mode reads the cache or fails loudly."""
    CACHE_DIR.mkdir(exist_ok=True)
    path = CACHE_DIR / cache_name
    if offline:
        if not path.exists():
            raise RuntimeError(f"offline: no cache for {cache_name}")
        return path.read_bytes()
    import requests  # imported lazily so offline tests never need it

    resp = requests.get(
        url,
        timeout=30,
        headers={"User-Agent": "MedTraveler-catalog-updater/1.0 (+education project)"},
    )
    resp.raise_for_status()
    path.write_bytes(resp.content)
    return resp.content


# Filled by the per-country adapters (fetch_ar, fetch_br, ...) defined below.
ADAPTERS = {}


def main(argv=None):
    p = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    p.add_argument(
        "--countries",
        default="AR,BR,JP,SG,AE",
        help="comma-separated subset of AR,BR,JP,SG,AE",
    )
    p.add_argument(
        "--offline",
        action="store_true",
        help="parse from tools/cache/ only, no network",
    )
    p.add_argument("--catalog", type=pathlib.Path, default=DEFAULT_CATALOG)
    p.add_argument("--out", type=pathlib.Path, default=DEFAULT_OUT)
    args = p.parse_args(argv)

    wanted = [c.strip().upper() for c in args.countries.split(",") if c.strip()]
    unknown = [c for c in wanted if c not in ADAPTERS]
    if unknown:
        p.error(f"unknown countries: {unknown}; known: {sorted(ADAPTERS)}")

    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))

    results, stubs, ok = {}, [], 0
    for cc in wanted:
        try:
            subs = ADAPTERS[cc](args.offline)
            diff = diff_catalog(catalog.get(cc, {}).get("items", []), subs)
            results[cc] = diff
            stubs.extend(make_stub(cc, s) for s in diff["new"])
            ok += 1
        except Exception as e:  # one broken source must not stop the others
            results[cc] = f"{type(e).__name__}: {e}"

    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "report.md").write_text(render_report(results), encoding="utf-8")
    (args.out / "stubs.json").write_text(
        json.dumps(stubs, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(f"report: {args.out / 'report.md'}")
    print(f"stubs:  {args.out / 'stubs.json'} ({len(stubs)} new)")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
