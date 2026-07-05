package com.davinci.medtraveler.data;

import com.davinci.medtraveler.data.local.MedEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for the medicine catalog, used both for the bundled {@code meds_seed.json} asset
 * and for the live Realtime Database nodes fetched by {@link CatalogUpdater}.
 *
 * <h3>Localisation model</h3>
 * Each medicine item may carry, in addition to the legacy single-language {@code name}:
 * <ul>
 *   <li>{@code nameLatin} — the INN (Latin), language-independent, always shown alongside
 *       the localized name so the substance is identifiable across UI languages.</li>
 *   <li>{@code names} — an object mapping a language code (es / en / uk / be / zh) to its
 *       localized name. The caller passes the desired {@code lang} into {@link #parseCountry}
 *       / {@link #parseSeed}; the chosen localized name is placed in {@code m.name} (so
 *       Room stores exactly one localized name per row, picked for the active UI
 *       language). {@code nameLatin} is stored separately and never depends on language.</li>
 * </ul>
 * If {@code names} is absent or has no entry for the requested language, the parser falls
 * back to the legacy {@code name} field, then to {@code nameLatin}. This keeps older
 * seed/RTDB payloads working without changes.
 */
public final class CatalogJson {
    private CatalogJson() {}

    /** Supported catalog languages, in priority order for the legacy fallback. The RTDB
     *  path scheme is {@code /meds/<country>/<lang>.json}. {@code es} is the bundled-seed
     *  default. */
    public static final String[] LANGS = {"es", "en", "uk", "be", "zh"};
    public static final String DEFAULT_LANG = "es";

    public static boolean isSupportedLang(String lang) {
        if (lang == null) return false;
        for (String l : LANGS) if (l.equals(lang)) return true;
        return false;
    }

    /** Coerce an arbitrary device locale string into one of the supported catalog langs,
     *  falling back to {@link #DEFAULT_LANG}. */
    public static String coerceLang(String lang) {
        return isSupportedLang(lang) ? lang : DEFAULT_LANG;
    }

    public static final class CountryPayload {
        public int version;
        public String updatedAt;
        public List<MedEntity> items = new ArrayList<>();
    }

    public static CountryPayload parseCountry(String code, String json) throws JSONException {
        return parseCountry(code, json, DEFAULT_LANG);
    }

    public static CountryPayload parseCountry(String code, String json, String lang) throws JSONException {
        return parseNode(code, new JSONObject(json), coerceLang(lang));
    }

    public static Map<String, CountryPayload> parseSeed(String json) throws JSONException {
        return parseSeed(json, DEFAULT_LANG);
    }

    public static Map<String, CountryPayload> parseSeed(String json, String lang) throws JSONException {
        JSONObject root = new JSONObject(json);
        String l = coerceLang(lang);
        Map<String, CountryPayload> out = new LinkedHashMap<>();
        Iterator<String> codes = root.keys();
        while (codes.hasNext()) {
            String code = codes.next();
            out.put(code, parseNode(code, root.getJSONObject(code), l));
        }
        return out;
    }

    private static CountryPayload parseNode(String code, JSONObject node, String lang) throws JSONException {
        CountryPayload p = new CountryPayload();
        p.version = node.optInt("version", 0);
        p.updatedAt = node.optString("updatedAt", "");
        JSONArray items = node.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                p.items.add(toEntity(code, items.getJSONObject(i), lang));
            }
        }
        return p;
    }

    private static MedEntity toEntity(String code, JSONObject o, String lang) {
        MedEntity m = new MedEntity();
        m.id = o.optString("id");
        m.nameLatin = o.optString("nameLatin", "");
        // Prefer the requested localized name from the `names` map; fall back to the
        // legacy single-language `name` field, then to the Latin INN.
        m.name = pickLocalizedName(o, lang);
        if ((m.name == null || m.name.isEmpty()) && !m.nameLatin.isEmpty()) {
            m.name = m.nameLatin;
        }
        m.countryCode = code;                       // single source: argument, not JSON
        m.status = o.optString("status", "RESTRICTED");
        m.activeSubstance = o.optString("activeSubstance");
        m.group = o.optString("group");
        m.prescription = o.optString("prescription");
        m.brand = o.optString("brand");
        m.description = o.optString("description");
        m.lawExcerpt = o.optString("lawExcerpt");
        m.penalty = o.optString("penalty");
        m.sourceUrl = o.optString("sourceUrl");
        m.imageUrl = o.optString("imageUrl");
        // If the payload didn't carry nameLatin, surface the localized name there too so
        // UI code that reads nameLatin never shows blank.
        if (m.nameLatin.isEmpty()) m.nameLatin = m.name == null ? "" : m.name;
        return m;
    }

    /** Resolve the localized name for {@code lang} from {@code o}: try {@code names[lang]},
     *  then the legacy {@code name}, then "". */
    private static String pickLocalizedName(JSONObject o, String lang) {
        JSONObject names = o.optJSONObject("names");
        if (names != null) {
            String v = names.optString(lang, null);
            if (v != null && !v.isEmpty()) return v;
        }
        // Legacy single-language payload OR the requested lang is absent in the map:
        // use the bare `name` (typical for an old Spanish-only RTDB node).
        String legacy = o.optString("name", null);
        return legacy == null ? "" : legacy;
    }
}