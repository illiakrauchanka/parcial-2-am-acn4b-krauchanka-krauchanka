package com.davinci.medtraveler.model;

import com.davinci.medtraveler.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CountryCatalog {
    private CountryCatalog() {}

    public static final class Country {
        public final String code;
        public final String name;
        public final int flagRes;
        public Country(String code, String name, int flagRes) {
            this.code = code; this.name = name; this.flagRes = flagRes;
        }
    }

    public static final List<Country> ALL = Collections.unmodifiableList(Arrays.asList(
            new Country("AR", "Argentina", R.drawable.flag_argentina),
            new Country("BR", "Brasil", R.drawable.flag_brasil),
            new Country("JP", "Japón", R.drawable.flag_japan),
            new Country("AE", "Emiratos Árabes Unidos", R.drawable.flag_uae),
            new Country("SG", "Singapur", R.drawable.flag_singapore)
    ));

    public static List<Country> available(List<String> alreadySelected) {
        List<Country> out = new ArrayList<>();
        for (Country c : ALL) {
            if (alreadySelected == null || !alreadySelected.contains(c.code)) out.add(c);
        }
        return out;
    }

    public static Country byCode(String code) {
        for (Country c : ALL) if (c.code.equals(code)) return c;
        return null;
    }

    public static String nameOf(String code) {
        Country c = byCode(code);
        return c == null ? code : c.name;
    }
}
