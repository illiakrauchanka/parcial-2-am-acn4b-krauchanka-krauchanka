import unittest

import update_catalog as uc


class NormalizeNameTest(unittest.TestCase):
    def test_lowercase_and_strip(self):
        self.assertEqual(uc.normalize_name("  Tramadol  "), "tramadol")

    def test_strips_accents(self):
        self.assertEqual(uc.normalize_name("Codeína"), "codeina")
        self.assertEqual(uc.normalize_name("Metanfetamína"), "metanfetamina")

    def test_strips_salt_suffixes(self):
        self.assertEqual(uc.normalize_name("Tramadol clorhidrato"), "tramadol")
        self.assertEqual(
            uc.normalize_name("Diphenhydramine hydrochloride"), "diphenhydramine"
        )
        self.assertEqual(uc.normalize_name("Fosfato de codeína"), "codeina")
        self.assertEqual(uc.normalize_name("Morphine sulfate"), "morphine")

    def test_collapses_whitespace_and_punct(self):
        self.assertEqual(
            uc.normalize_name("gamma - hydroxybutyric   acid"),
            "gamma hydroxybutyric acid",
        )

    def test_cyrillic_safe(self):
        self.assertEqual(uc.normalize_name("Анальгин"), "анальгин")

    def test_empty_and_none(self):
        self.assertEqual(uc.normalize_name(""), "")
        self.assertEqual(uc.normalize_name(None), "")


class DiffCatalogTest(unittest.TestCase):
    CATALOG = [
        {
            "id": "ar-01",
            "name": "Tramadol",
            "activeSubstance": "Tramadol clorhidrato",
            "status": "PENAL",
        },
        {
            "id": "ar-02",
            "name": "Codeína",
            "activeSubstance": "Fosfato de codeína",
            "status": "RESTRICTED",
        },
    ]

    def test_unchanged_matches_by_substance(self):
        scraped = [
            uc.Substance("tramadol", "PENAL", "http://x"),
            uc.Substance("Codeina", "RESTRICTED", "http://x"),
        ]
        d = uc.diff_catalog(self.CATALOG, scraped)
        self.assertEqual(d["new"], [])
        self.assertEqual(d["missing"], [])
        self.assertEqual(d["status_changed"], [])

    def test_new_substance(self):
        scraped = [
            uc.Substance("Tramadol", "PENAL", "http://x"),
            uc.Substance("Codeína", "RESTRICTED", "http://x"),
            uc.Substance("Fentanilo", "PENAL", "http://x"),
        ]
        d = uc.diff_catalog(self.CATALOG, scraped)
        self.assertEqual([s.name for s in d["new"]], ["Fentanilo"])

    def test_missing_from_source(self):
        scraped = [uc.Substance("Tramadol", "PENAL", "http://x")]
        d = uc.diff_catalog(self.CATALOG, scraped)
        self.assertEqual([m["id"] for m in d["missing"]], ["ar-02"])

    def test_status_changed(self):
        scraped = [
            uc.Substance("Tramadol", "RESTRICTED", "http://x"),
            uc.Substance("Codeína", "RESTRICTED", "http://x"),
        ]
        d = uc.diff_catalog(self.CATALOG, scraped)
        self.assertEqual(len(d["status_changed"]), 1)
        item, sub = d["status_changed"][0]
        self.assertEqual(item["id"], "ar-01")
        self.assertEqual(sub.status, "RESTRICTED")

    def test_matches_by_name_when_substance_differs(self):
        # 'name' matches even though activeSubstance is a different salt wording
        catalog = [
            {
                "id": "x",
                "name": "Diazepam",
                "activeSubstance": "Diazepamum",
                "status": "RESTRICTED",
            }
        ]
        scraped = [uc.Substance("diazepam", "RESTRICTED", "u")]
        d = uc.diff_catalog(catalog, scraped)
        self.assertEqual(d["new"], [])
        self.assertEqual(d["missing"], [])


class MakeStubTest(unittest.TestCase):
    def test_stub_schema_complete(self):
        stub = uc.make_stub("AR", uc.Substance("Fentanilo", "PENAL", "https://anmat"))
        self.assertEqual(stub["id"], "ar-auto-fentanilo")
        self.assertEqual(stub["name"], "Fentanilo")
        self.assertEqual(stub["status"], "PENAL")
        self.assertEqual(stub["activeSubstance"], "Fentanilo")
        self.assertEqual(stub["sourceUrl"], "https://anmat")
        for f in (
            "group",
            "prescription",
            "brand",
            "description",
            "lawExcerpt",
            "penalty",
            "imageUrl",
        ):
            self.assertEqual(stub[f], "TODO")

    def test_stub_id_normalized(self):
        stub = uc.make_stub("BR", uc.Substance("Ácido lisérgico (LSD)", "PENAL", "u"))
        self.assertEqual(stub["id"], "br-auto-acido_lisergico_lsd")


class RenderReportTest(unittest.TestCase):
    def test_sections_and_errors(self):
        results = {
            "AR": {
                "new": [uc.Substance("Fentanilo", "PENAL", "u")],
                "missing": [{"id": "ar-02", "name": "Codeína"}],
                "status_changed": [],
            },
            "BR": "timeout fetching ANVISA",
        }
        md = uc.render_report(results)
        self.assertIn("## AR", md)
        self.assertIn("Fentanilo", md)
        self.assertIn("ar-02", md)
        self.assertIn("## BR", md)
        self.assertIn("SOURCE-UNAVAILABLE", md)
        self.assertIn("timeout fetching ANVISA", md)


class ZeroParseIsFailureTest(unittest.TestCase):
    def test_guard_zero_substances(self):
        # every adapter must funnel through require_nonempty()
        with self.assertRaises(uc.ParserFailure):
            uc.require_nonempty([], "HSA page yielded no rows")
        subs = [uc.Substance("x", "PENAL", "u")]
        self.assertIs(uc.require_nonempty(subs, "msg"), subs)


if __name__ == "__main__":
    unittest.main()
