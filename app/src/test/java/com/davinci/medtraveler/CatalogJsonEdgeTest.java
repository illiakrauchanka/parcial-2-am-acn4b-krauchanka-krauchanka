package com.davinci.medtraveler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.davinci.medtraveler.data.CatalogJson;
import com.davinci.medtraveler.data.local.MedEntity;

import org.junit.Test;

import java.util.Map;

/**
 * Edge-case coverage for {@link CatalogJson}. The existing CatalogJsonTest checks the
 * happy path; here we lock down the defensive behaviour the rest of the app depends on:
 * missing/empty items arrays, missing optional fields defaulting to empty strings,
 * status defaulting to RESTRICTED, countryCode always coming from the argument (never
 * the JSON body), and the seed parser tolerating an empty or multi-country object.
 */
public class CatalogJsonEdgeTest {

    @Test public void missingItemsArray_yieldsEmptyList() throws Exception {
        CatalogJson.CountryPayload p = CatalogJson.parseCountry("AR",
                "{ \"version\": 1, \"updatedAt\": \"2026-01-01T00:00:00Z\" }");
        assertEquals(0, p.items.size());
        assertEquals(1, p.version);
        assertEquals("2026-01-01T00:00:00Z", p.updatedAt);
    }

    @Test public void emptyItemsArray_yieldsEmptyList() throws Exception {
        CatalogJson.CountryPayload p = CatalogJson.parseCountry("AR",
                "{ \"version\": 3, \"updatedAt\": \"x\", \"items\": [] }");
        assertEquals(0, p.items.size());
    }

    @Test public void missingOptionalFields_defaultToEmptyStrings() throws Exception {
        // Only id + name are meaningful; everything else must fall back to "" / RESTRICTED.
        CatalogJson.CountryPayload p = CatalogJson.parseCountry("JP",
                "{ \"items\": [ { \"id\": \"jp-1\", \"name\": \"Ibuprofen\" } ] }");
        assertEquals(1, p.items.size());
        MedEntity m = p.items.get(0);
        assertEquals("jp-1", m.id);
        assertEquals("Ibuprofen", m.name);
        assertEquals("", m.activeSubstance);
        assertEquals("", m.group);
        assertEquals("", m.brand);
        assertEquals("", m.description);
        assertEquals("", m.lawExcerpt);
        assertEquals("", m.penalty);
        assertEquals("", m.sourceUrl);
        assertEquals("", m.imageUrl);
    }

    @Test public void missingStatus_defaultsToRestricted() throws Exception {
        CatalogJson.CountryPayload p = CatalogJson.parseCountry("AR",
                "{ \"items\": [ { \"id\": \"x\", \"name\": \"X\" } ] }");
        assertEquals("RESTRICTED", p.items.get(0).status);
    }

    @Test public void countryCode_alwaysFromArgument_notJsonBody() throws Exception {
        // The JSON body may contain a `countryCode` field; the app MUST ignore it and use
        // the one passed as the argument to parseCountry (single source of truth). If a
        // future change starts reading the body, this test guards against silent drift.
        CatalogJson.CountryPayload p = CatalogJson.parseCountry("AR",
                "{ \"items\": [ { \"id\": \"x\", \"name\": \"X\", \"countryCode\": \"JP\" } ] }");
        assertEquals("AR", p.items.get(0).countryCode);
    }

    @Test public void unknownStatusString_isKeptVerbatim_notCoerced() throws Exception {
        // CatalogJson stores the raw `status` string; only Status.fromString (tested in
        // StatusTest) coerces unknown values to RESTRICTED. The parser must not silently
        // rewrite the value — the DB should mirror what the server sent.
        CatalogJson.CountryPayload p = CatalogJson.parseCountry("AR",
                "{ \"items\": [ { \"id\": \"x\", \"name\": \"X\", \"status\": \"BANNED\" } ] }");
        assertEquals("BANNED", p.items.get(0).status);
    }

    @Test public void missingUpdatedAt_defaultsToEmpty() throws Exception {
        CatalogJson.CountryPayload p = CatalogJson.parseCountry("AR", "{ \"items\": [] }");
        assertEquals("", p.updatedAt);
        assertEquals(0, p.version);
    }

    @Test public void parseSeed_emptyObject_returnsEmptyMap() throws Exception {
        Map<String, CatalogJson.CountryPayload> map = CatalogJson.parseSeed("{}");
        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    @Test public void parseSeed_multipleCountries() throws Exception {
        String ar = "{ \"version\": 1, \"updatedAt\": \"a\", \"items\": [] }";
        String jp = "{ \"version\": 2, \"updatedAt\": \"b\", \"items\": [] }";
        Map<String, CatalogJson.CountryPayload> map = CatalogJson.parseSeed(
                "{ \"AR\": " + ar + ", \"JP\": " + jp + " }");
        assertEquals(2, map.size());
        assertEquals(1, map.get("AR").version);
        assertEquals(2, map.get("JP").version);
    }

    @Test public void parseSeed_multipleCountries_allKeysReachable() throws Exception {
        // The internal map preserves insertion order on Android's JSONObject (LinkedHashMap
        // backed), but the org.json:json unit-test stub uses a plain HashMap, so we only
        // assert the *set* of keys here — ordering is a platform-runtime guarantee
        // exercised by the existing happy-path test, not by this JUnit stub.
        Map<String, CatalogJson.CountryPayload> map = CatalogJson.parseSeed(
                "{ \"JP\": { \"items\": [] }, \"AR\": { \"items\": [] }, \"AE\": { \"items\": [] } }");
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("JP", "AR", "AE")),
                map.keySet());
    }
}