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

public final class CatalogJson {
    private CatalogJson() {}

    public static final class CountryPayload {
        public int version;
        public String updatedAt;
        public List<MedEntity> items = new ArrayList<>();
    }

    public static CountryPayload parseCountry(String code, String json) throws JSONException {
        return parseNode(code, new JSONObject(json));
    }

    public static Map<String, CountryPayload> parseSeed(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        Map<String, CountryPayload> out = new LinkedHashMap<>();
        Iterator<String> codes = root.keys();
        while (codes.hasNext()) {
            String code = codes.next();
            out.put(code, parseNode(code, root.getJSONObject(code)));
        }
        return out;
    }

    private static CountryPayload parseNode(String code, JSONObject node) throws JSONException {
        CountryPayload p = new CountryPayload();
        p.version = node.optInt("version", 0);
        p.updatedAt = node.optString("updatedAt", "");
        JSONArray items = node.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                p.items.add(toEntity(code, items.getJSONObject(i)));
            }
        }
        return p;
    }

    private static MedEntity toEntity(String code, JSONObject o) {
        MedEntity m = new MedEntity();
        m.id = o.optString("id");
        m.name = o.optString("name");
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
        return m;
    }
}
