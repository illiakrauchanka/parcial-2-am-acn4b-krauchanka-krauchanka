package com.davinci.medtraveler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.davinci.medtraveler.data.CatalogJson;
import com.davinci.medtraveler.data.local.MedEntity;

import org.junit.Test;

import java.util.Map;

public class CatalogJsonTest {

    private static final String AR_NODE =
            "{ \"version\": 2, \"updatedAt\": \"2026-06-27T00:00:00Z\", \"items\": ["
          + "  { \"id\": \"ar-tramadol\", \"name\": \"Tramadol\", \"status\": \"PENAL\","
          + "    \"activeSubstance\": \"Tramadol clorhidrato\", \"group\": \"Opioide\","
          + "    \"prescription\": \"Receta archivada\", \"brand\": \"Tramal\","
          + "    \"description\": \"d\", \"lawExcerpt\": \"l\", \"penalty\": \"p\","
          + "    \"sourceUrl\": \"http://x\", \"imageUrl\": \"http://y\" }"
          + "] }";

    @Test public void parseCountry_readsMetaAndItems() throws Exception {
        CatalogJson.CountryPayload p = CatalogJson.parseCountry("AR", AR_NODE);
        assertEquals(2, p.version);
        assertEquals("2026-06-27T00:00:00Z", p.updatedAt);
        assertEquals(1, p.items.size());
        MedEntity m = p.items.get(0);
        assertEquals("ar-tramadol", m.id);
        assertEquals("Tramadol", m.name);
        assertEquals("PENAL", m.status);
        assertEquals("Opioide", m.group);
        assertEquals("AR", m.countryCode);
    }

    @Test public void parseSeed_keysByCountryCode() throws Exception {
        Map<String, CatalogJson.CountryPayload> map =
                CatalogJson.parseSeed("{ \"AR\": " + AR_NODE + " }");
        assertTrue(map.containsKey("AR"));
        assertEquals(1, map.get("AR").items.size());
        assertEquals("AR", map.get("AR").items.get(0).countryCode);
    }
}
