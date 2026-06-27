package com.davinci.medtraveler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.davinci.medtraveler.data.CatalogMeta;

import org.junit.Test;

public class CatalogMetaTest {
    private static final long TTL = 24L * 60 * 60 * 1000;

    @Test public void notFresh_whenNeverSaved() { assertFalse(CatalogMeta.isFresh(0, 1_000_000, TTL)); }
    @Test public void fresh_withinTtl() { assertTrue(CatalogMeta.isFresh(1000, 1000 + TTL - 1, TTL)); }
    @Test public void notFresh_pastTtl() { assertFalse(CatalogMeta.isFresh(1000, 1000 + TTL + 1, TTL)); }

    @Test public void maxIso_picksGreater() {
        assertEquals("2026-06-27", CatalogMeta.maxIso("2026-06-20", "2026-06-27"));
        assertEquals("2026-06-27", CatalogMeta.maxIso("2026-06-27", ""));
        assertEquals("", CatalogMeta.maxIso("", ""));
    }
}
