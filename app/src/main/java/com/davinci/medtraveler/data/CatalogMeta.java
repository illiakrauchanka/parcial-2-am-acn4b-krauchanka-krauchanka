package com.davinci.medtraveler.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class CatalogMeta {
    public static final long DEFAULT_TTL_MS = 24L * 60 * 60 * 1000;
    private static final String PREFS = "catalog_meta";
    private static final String CODES = "codes";

    private final SharedPreferences prefs;

    public CatalogMeta(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isFresh(long savedAt, long now, long ttl) {
        return savedAt > 0 && (now - savedAt) < ttl;
    }

    public static String maxIso(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        return a.compareTo(b) >= 0 ? a : b;
    }

    public int getVersion(String code) { return prefs.getInt(code + ".version", 0); }
    public String getUpdatedAt(String code) { return prefs.getString(code + ".updatedAt", ""); }
    public long getSavedAt(String code) { return prefs.getLong(code + ".savedAt", 0); }

    public void save(String code, int version, String updatedAt, long savedAt) {
        Set<String> codes = new HashSet<>(prefs.getStringSet(CODES, new HashSet<>()));
        codes.add(code);
        prefs.edit()
                .putInt(code + ".version", version)
                .putString(code + ".updatedAt", updatedAt == null ? "" : updatedAt)
                .putLong(code + ".savedAt", savedAt)
                .putStringSet(CODES, codes)
                .apply();
    }

    public boolean freshFor(String code, long now) {
        return isFresh(getSavedAt(code), now, DEFAULT_TTL_MS);
    }

    public String lastUpdatedGlobal() {
        String max = "";
        for (String code : prefs.getStringSet(CODES, new HashSet<>())) {
            max = maxIso(max, getUpdatedAt(code));
        }
        return max;
    }

    public boolean isSeeded() { return prefs.getBoolean("seeded", false); }
    public void markSeeded() { prefs.edit().putBoolean("seeded", true).apply(); }

    /** Language the catalog baked into Room was last seeded/fetched for (es/en/uk/be/zh).
     *  CatalogRepo re-seeds from the bundled asset when this no longer matches the UI
     *  language, so localized names stay in sync with the user's chosen locale. */
    public String getSeededLang() { return prefs.getString("seededLang", ""); }
    public void setSeededLang(String lang) {
        prefs.edit().putString("seededLang", lang == null ? "" : lang).apply();
    }
    public void markSeeded(String lang) {
        prefs.edit().putBoolean("seeded", true).putString("seededLang", lang == null ? "" : lang).apply();
    }
}
