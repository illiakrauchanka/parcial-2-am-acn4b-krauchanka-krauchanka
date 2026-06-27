package com.davinci.medtraveler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.davinci.medtraveler.model.CountryCatalog;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CountrySelectionTest {
    private static List<String> codes(List<CountryCatalog.Country> cs) {
        List<String> out = new ArrayList<>();
        for (CountryCatalog.Country c : cs) out.add(c.code);
        return out;
    }

    @Test public void available_excludesSelected() {
        List<CountryCatalog.Country> a = CountryCatalog.available(Arrays.asList("AR"));
        assertFalse(codes(a).contains("AR"));
        assertEquals(CountryCatalog.ALL.size() - 1, a.size());
    }

    @Test public void available_noneSelected_returnsAll() {
        assertEquals(CountryCatalog.ALL.size(), CountryCatalog.available(new ArrayList<>()).size());
    }

    @Test public void byCode_and_nameOf() {
        assertNotNull(CountryCatalog.byCode("AR"));
        assertEquals("Argentina", CountryCatalog.nameOf("AR"));
        assertEquals("ZZ", CountryCatalog.nameOf("ZZ"));
    }
}
