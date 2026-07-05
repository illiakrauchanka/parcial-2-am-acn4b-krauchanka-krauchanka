package com.davinci.medtraveler.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.davinci.medtraveler.model.Medicine;
import com.davinci.medtraveler.model.Status;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Edge-case coverage for {@link SearchFilter}. The existing SearchFilterTest covers
 * the basic name/substance match; here we cover null safety, empty source lists,
 * diacritics (Spanish-language search real users type), and the immutability of the
 * input list.
 */
public class SearchFilterEdgeTest {

    private Medicine med(String name, String substance) {
        return new Medicine("id", name, "Latrum", "AR", "Argentina", Status.PENAL,
                substance, "g", "p", "b", "d", "l", "pen", "u", "img");
    }

    @Test public void nullQuery_returnsAll() {
        // filter must treat null like an empty query (no NPE).
        List<Medicine> all = Arrays.asList(med("Tramadol", "x"), med("Diazepam", "y"));
        assertEquals(2, SearchFilter.filter(all, null).size());
    }

    @Test public void emptySource_returnsEmpty() {
        assertTrue(SearchFilter.filter(Collections.emptyList(), "tram").isEmpty());
    }

    @Test public void nullSource_returnsEmpty_noNpe() {
        // After the defensive fix in SearchFilter.filter, a null source list must return
        // an empty list (not NPE). This guards a regression that previously crashed on
        // `new ArrayList<>(null)` — see the corresponding comment in SearchFilter.java.
        assertTrue(SearchFilter.filter(null, null).isEmpty());
        assertTrue(SearchFilter.filter(null, "tram").isEmpty());
    }

    @Test public void nullFields_doNotCauseNpeAndDoNotMatch() {
        Medicine m = new Medicine("id", null, null, "AR", "Argentina", Status.PENAL,
                null, "g", "p", "b", "d", "l", "pen", "u", "img");
        // Should match nothing even if name+substance are null.
        assertEquals(0, SearchFilter.filter(Collections.singletonList(m), "anything").size());
        // ..but still return the item when the query is empty.
        assertEquals(1, SearchFilter.filter(Collections.singletonList(m), "").size());
    }

    @Test public void caseInsensitive_asciiMatch_doesNotNormalizeAccents() {
        // Real Spanish content: "diazepam" matches "Diazepam" (pure ASCII lowercasing
        // with Locale.ROOT). However, the filter does NOT fold accents, so "CODEINA"
        // does NOT match "Codeína" (í is a different codepoint than i). We lock that
        // current behaviour down so a future change (e.g. adding java.text.Normalizer
        // accent-folding, or switching to Locale.getDefault() which breaks on the
        // Turkish-i edge case) is a conscious decision, not a silent drift.
        List<Medicine> all = Arrays.asList(med("Diazepam", "Diazepam"), med("Codeína", "Fosfato de codeína"));
        assertEquals(1, SearchFilter.filter(all, "diazepam").size());
        assertEquals(0, SearchFilter.filter(all, "CODEINA").size());
        assertEquals(1, SearchFilter.filter(all, "codeína").size());
    }

    @Test public void doesNotMutateInputList() {
        // The contract is to return a NEW list, leaving the input untouched. Some
        // callers reuse the source list across searches.
        List<Medicine> all = Arrays.asList(med("Tramadol", "x"), med("Diazepam", "y"));
        int before = all.size();
        SearchFilter.filter(all, "tram");
        assertEquals(before, all.size());
    }

    @Test public void multipleMatches_allReturned_inSourceOrder() {
        Medicine a = med("Paracetamol", "x");
        Medicine b = med("Parafina", "y");
        List<Medicine> r = SearchFilter.filter(Arrays.asList(a, b), "para");
        assertEquals(2, r.size());
        assertEquals("Paracetamol", r.get(0).name);
        assertEquals("Parafina", r.get(1).name);
    }
}