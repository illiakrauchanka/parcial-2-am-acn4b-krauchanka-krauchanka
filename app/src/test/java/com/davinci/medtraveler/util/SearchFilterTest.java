package com.davinci.medtraveler.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.davinci.medtraveler.model.Medicine;
import com.davinci.medtraveler.model.Status;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;

public class SearchFilterTest {

    private Medicine med(String name, String substance) {
        return new Medicine("id", name, "Latrum", "AR", "Argentina", Status.PENAL,
                substance, "g", "p", "b", "d", "l", "pen", "u", "img");
    }

    private final List<Medicine> all = Arrays.asList(
            med("Tramadol", "Tramadol clorhidrato"),
            med("Codeína", "Fosfato de codeína"),
            med("Diazepam", "Diazepam"));

    @Test public void emptyQueryReturnsAll() {
        assertEquals(3, SearchFilter.filter(all, "").size());
        assertEquals(3, SearchFilter.filter(all, "   ").size());
    }
    @Test public void matchesByName() {
        List<Medicine> r = SearchFilter.filter(all, "tram");
        assertEquals(1, r.size());
        assertEquals("Tramadol", r.get(0).name);
    }
    @Test public void matchesBySubstanceCaseInsensitive() {
        List<Medicine> r = SearchFilter.filter(all, "CODE");
        assertEquals(1, r.size());
        assertTrue(r.get(0).name.startsWith("Code"));
    }
    @Test public void noMatchReturnsEmpty() {
        assertEquals(0, SearchFilter.filter(all, "zzz").size());
    }
}
