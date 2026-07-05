package com.davinci.medtraveler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.davinci.medtraveler.model.CountryCatalog;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Edge-case coverage for {@link CountryCatalog}. The existing CountrySelectionTest
 * covers the happy path; here we lock down null-safety of {@code available(null)},
 * the multi-select / all-selected cases, and the {@code nameOf} fallback for codes
 * that are not in the catalog.
 */
public class CountryCatalogEdgeTest {

    private static List<String> codes(List<CountryCatalog.Country> cs) {
        List<String> out = new ArrayList<>();
        for (CountryCatalog.Country c : cs) out.add(c.code);
        return out;
    }

    @Test public void available_nullTreatedAsNoneSelected() {
        // available(null) must not NPE — the public API contract treats it like an empty
        // selection (everything is available).
        assertEquals(CountryCatalog.ALL.size(), CountryCatalog.available(null).size());
    }

    @Test public void available_multipleSelected_excludedTogether() {
        List<CountryCatalog.Country> a = CountryCatalog.available(Arrays.asList("AR", "JP"));
        assertFalse(codes(a).contains("AR"));
        assertFalse(codes(a).contains("JP"));
        assertEquals(CountryCatalog.ALL.size() - 2, a.size());
    }

    @Test public void available_allSelected_returnsEmpty() {
        List<String> all = new ArrayList<>();
        for (CountryCatalog.Country c : CountryCatalog.ALL) all.add(c.code);
        assertTrue(CountryCatalog.available(all).isEmpty());
    }

    @Test public void available_unknownCodeIgnored() {
        // A bogus selected code (typo / deleted country) must not blow up — it's just
        // ignored, and every real country stays available.
        List<CountryCatalog.Country> a = CountryCatalog.available(Arrays.asList("ZZ", "XX"));
        assertEquals(CountryCatalog.ALL.size(), a.size());
    }

    @Test public void byCode_knownReturnsCountry_unknownReturnsNull() {
        assertNotNull(CountryCatalog.byCode("AR"));
        assertEquals("AR", CountryCatalog.byCode("AR").code);
        assertNull(CountryCatalog.byCode("ZZ"));
        assertNull(CountryCatalog.byCode(null));
    }

    @Test public void nameOf_unknownCodeEchoedBack() {
        // nameOf is used by CatalogRepo.toModel to render the country name for ANY row in
        // the DB, including rows whose code might have been deleted from the catalog in
        // a future release. It must echo the code back rather than crash.
        assertEquals("ZZ", CountryCatalog.nameOf("ZZ"));
        assertEquals("Argentina", CountryCatalog.nameOf("AR"));
    }

    @Test public void all_isImmutable() {
        // ALL is exposed as a public constant; the UI iterates and reads from it. It must
        // be unmodifiable so a stray add/remove doesn't silently corrupt global state.
        try {
            CountryCatalog.ALL.add(new CountryCatalog.Country("XX", "X", 0));
            // If we reach here, the list is mutable — fail the test.
            throw new AssertionError("CountryCatalog.ALL should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }
}