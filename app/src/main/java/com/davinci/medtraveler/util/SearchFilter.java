package com.davinci.medtraveler.util;

import com.davinci.medtraveler.model.Medicine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SearchFilter {
    private SearchFilter() {}

    public static List<Medicine> filter(List<Medicine> source, String query) {
        if (source == null) return new ArrayList<>();          // defensive: never NPE on a null list
        if (query == null || query.trim().isEmpty()) return new ArrayList<>(source);
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Medicine> out = new ArrayList<>();
        for (Medicine m : source) {
            String name = m.name == null ? "" : m.name.toLowerCase(Locale.ROOT);
            String sub = m.activeSubstance == null ? "" : m.activeSubstance.toLowerCase(Locale.ROOT);
            if (name.contains(q) || sub.contains(q)) out.add(m);
        }
        return out;
    }
}
