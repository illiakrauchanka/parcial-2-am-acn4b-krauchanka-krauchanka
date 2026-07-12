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
        hits = [scraped_by_key[k] for k in sorted(keys) if k in scraped_by_key]
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


ADAPTERS = {}

# ---------------------------------------------------------------- AR (ANMAT)
# ANMAT's own domain (anmat.gob.ar/ssce/*.pdf) serves the raw files directly
# but the current *canonical, versioned* mirror is the listing page
# https://www.argentina.gob.ar/anmat/regulados/controlespecial/listados which
# links this exact file. The live source turned out to be an .xlsx spreadsheet
# ("Listado de Sustancias Controladas - Psicotrópicos"), not HTML or PDF.
AR_URL = "https://www.argentina.gob.ar/sites/default/files/psicotropicos_2016.xlsx"

# ANMAT's "SITUACIÓN REGULATORIA" column tags every row with "LISTA I/II/III/IV"
# (Ley 19.303, based on the 1971 UN Convention on Psychotropic Substances
# schedules). LISTA I substances (e.g. 4-metilaminorex, LSD-type hallucinogens)
# have no accepted medical use and are treated as criminal offenses to possess
# under Ley 23.737 -> PENAL. LISTA II-IV are legitimate prescription medicines
# (stimulants, barbiturates, benzodiazepines) under pharmacy control -> RESTRICTED.
AR_STATUS_MAP = {
    "IV": "RESTRICTED",
    "III": "RESTRICTED",
    "II": "RESTRICTED",
    "I": "PENAL",
}
_AR_LISTA_RE = re.compile(r"LISTA\s+(IV|III|II|I)\b")


def parse_ar(raw):
    import io

    import openpyxl  # lazy: offline unit tests import module w/o openpyxl

    try:
        wb = openpyxl.load_workbook(io.BytesIO(raw), data_only=True)
        ws = wb.active
        rows = list(ws.iter_rows(min_row=2, values_only=True))
    except Exception as e:  # noqa: BLE001 - any malformed workbook is a parser failure
        raise ParserFailure(
            f"ANMAT psicotrópicos workbook could not be read (format changed?): {e}"
        ) from e

    subs = []
    for row in rows:
        if not row or len(row) < 7:
            continue
        name, situacion = row[1], row[6]
        if not name or not situacion:
            continue
        name = str(name).strip()
        if not name or " ver " in f" {name.lower()} ":
            continue  # synonym/redirect rows ("Amfetamina Ver ANFETAMINA")
        m = _AR_LISTA_RE.search(str(situacion).upper())
        if not m:
            continue
        subs.append(Substance(name, AR_STATUS_MAP[m.group(1)], AR_URL))
    return require_nonempty(
        subs, "ANMAT psicotrópicos sheet yielded no substances (layout changed?)"
    )


# ANMAT publishes narcotics ("estupefacientes", Ley 17818/68) as a SEPARATE
# document from psychotropics: cocaine, LSD (as "LISÉRGIDA"), methadone,
# codeine, morphine etc. only appear here, not in AR_URL above. The table
# tags every row "LISTA I/II/III/IV. LEY 17818/68" too, but unlike
# psicotrópicos, ALL narcotic schedules are criminally prosecuted for simple
# possession under Ley 23.737 in Argentina -> every row here maps to PENAL
# regardless of its internal LISTA number.
ESTUP_URL = "https://www.argentina.gob.ar/sites/default/files/estupefacientes_2016.pdf"
_AR_ESTUP_LISTA_RE = re.compile(r"LISTA\s+(IV|III|II|I)\b")


def parse_ar_estupefacientes(raw):
    import io

    import pdfplumber  # lazy: offline unit tests import module w/o pdfplumber

    try:
        with pdfplumber.open(io.BytesIO(raw)) as pdf:
            rows = []
            for page in pdf.pages:
                for table in page.extract_tables():
                    rows.extend(table)
    except Exception as e:  # noqa: BLE001 - any malformed PDF is a parser failure
        raise ParserFailure(
            f"ANMAT estupefacientes PDF could not be read (format changed?): {e}"
        ) from e

    subs = []
    for row in rows:
        if not row or len(row) < 6:
            continue
        name, situacion = row[0], row[5]
        if not name or not situacion:
            continue
        name = name.replace("\n", " ").strip()
        if not name or " ver " in f" {name.lower()} ":
            continue  # synonym/redirect rows ("LSD Ver LISÉRGIDA")
        if not _AR_ESTUP_LISTA_RE.search(str(situacion).upper()):
            continue
        subs.append(Substance(name, "PENAL", ESTUP_URL))
    return require_nonempty(
        subs, "ANMAT estupefacientes table yielded no substances (layout changed?)"
    )


def fetch_ar(offline):
    psico = parse_ar(fetch_cached(AR_URL, "ar_anmat.xlsx", offline))
    estup = parse_ar_estupefacientes(
        fetch_cached(ESTUP_URL, "ar_anmat_estupefacientes.pdf", offline)
    )
    return psico + estup


ADAPTERS["AR"] = fetch_ar


# --------------------------------------------------------------- BR (ANVISA)
# The consolidated ("_COMP" = compilada) text of Portaria SVS/MS 344/98,
# reproducing every update through 2016, is mirrored on the legacy ANVISA
# portal. The current gov.br controlled-substances page only links individual
# amendment RDCs (not a consolidated annex), so this compiled PDF -- hosting
# the same legally-in-force annex text -- is the best parseable official
# source; noted here per the "no faking" rule.
BR_URL = (
    "https://antigo.anvisa.gov.br/documents/10181/2718376/PRT_SVS_344_1998_COMP.pdf"
)

# Anexo I lists: A1/A2 (entorpecentes - narcotics) and A3 (psicotrópicas de
# Notificação de Receita "A", the strictest control tier) match substances
# criminally prosecuted for simple possession -> PENAL. B1/B2 (psicotrópicas,
# Notificação "B") and C1.. (other special-control substances, e.g.
# anticonvulsants/antidepressants) are prescription-only medicines -> RESTRICTED.
BR_STATUS_MAP = {
    "A1": "PENAL",
    "A2": "PENAL",
    "A3": "PENAL",
    "B1": "RESTRICTED",
    "B2": "RESTRICTED",
    "C1": "RESTRICTED",
    "C2": "RESTRICTED",
    "C3": "RESTRICTED",
    "C4": "RESTRICTED",
    "C5": "RESTRICTED",
}
_BR_HEADER_RE = re.compile(r"^LISTA\s*-\s*([A-Z]\d?)\s*-", re.MULTILINE)
_BR_ITEM_RE = re.compile(r"^\d+[.\)]\s*(.+)$")


def _br_section_items(section_text):
    """Numbered entries may wrap to a following line; merge continuations
    (lines not starting with 'N.') onto the previous numbered line, stop at
    the ADENDO footnotes block that follows every list."""
    body = section_text.split("ADENDO")[0]
    merged = []
    for raw_line in body.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        if re.match(r"^\d+[.\)]\s*\S", line) or re.match(r"^\d+[.\)]$", line):
            merged.append(line)
        elif merged:
            merged[-1] += " " + line
    for line in merged:
        m = _BR_ITEM_RE.match(line)
        if m:
            name = m.group(1).strip().rstrip(".")
            if name:
                yield name


def parse_br(raw):
    import io

    import pdfplumber  # lazy: offline unit tests import module w/o pdfplumber

    try:
        with pdfplumber.open(io.BytesIO(raw)) as pdf:
            text = "\n".join(page.extract_text() or "" for page in pdf.pages)
    except Exception as e:  # noqa: BLE001 - any malformed PDF is a parser failure
        raise ParserFailure(
            f"ANVISA Anexo I PDF could not be read (format changed?): {e}"
        ) from e

    headers = list(_BR_HEADER_RE.finditer(text))
    subs = []
    for i, m in enumerate(headers):
        status = BR_STATUS_MAP.get(m.group(1))
        if not status:
            continue
        end = headers[i + 1].start() if i + 1 < len(headers) else len(text)
        for name in _br_section_items(text[m.end() : end]):
            subs.append(Substance(name, status, BR_URL))
    return require_nonempty(
        subs, "ANVISA Anexo I yielded no substances (layout changed?)"
    )


def fetch_br(offline):
    return parse_br(fetch_cached(BR_URL, "br_anvisa.pdf", offline))


ADAPTERS["BR"] = fetch_br


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
