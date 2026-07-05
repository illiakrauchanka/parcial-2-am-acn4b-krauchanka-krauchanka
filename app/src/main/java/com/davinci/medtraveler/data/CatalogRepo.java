package com.davinci.medtraveler.data;

import android.content.Context;

import com.davinci.medtraveler.data.local.AppDatabase;
import com.davinci.medtraveler.data.local.MedDao;
import com.davinci.medtraveler.data.local.MedEntity;
import com.davinci.medtraveler.model.CountryCatalog;
import com.davinci.medtraveler.model.Medicine;
import com.davinci.medtraveler.model.Status;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CatalogRepo {
    private final Context appCtx;
    private final MedDao dao;
    private final CatalogMeta meta;

    public CatalogRepo(Context ctx) {
        this.appCtx = ctx.getApplicationContext();
        this.dao = AppDatabase.get(appCtx).medDao();
        this.meta = new CatalogMeta(appCtx);
    }

    /** Seeds Room from assets once, for the given UI language. If the seeded language
     *  changes (user switched the app/UI language), the local cache is re-seeded from
     *  the bundled asset so localized names match the active language. Network refresh
     *  via {@link CatalogUpdater} still supersedes this with live RTDB data per lang. */
    public void seedIfEmpty(String lang) {
        String want = CatalogJson.coerceLang(lang);
        if (meta.isSeeded() && want.equals(meta.getSeededLang())) {
            // Already seeded for this language — nothing to do unless the live updater
            // has since fetched fresher data for this lang.
            return;
        }
        try {
            Map<String, CatalogJson.CountryPayload> seed =
                    CatalogJson.parseSeed(readAsset("meds_seed.json"), want);
            long now = System.currentTimeMillis();
            for (Map.Entry<String, CatalogJson.CountryPayload> e : seed.entrySet()) {
                CatalogJson.CountryPayload p = e.getValue();
                dao.replaceCountry(e.getKey(), p.items);
                meta.save(e.getKey(), p.version, p.updatedAt, now);
            }
            meta.markSeeded(want);   // only on success → a failed seed retries next launch
        } catch (Exception ignored) {
            // seed missing/malformed: not marked seeded; CHECK/Update DB can still fill via network
        }
    }

    public List<Medicine> byCountry(String code) { return mapAll(dao.byCountry(code)); }

    public Medicine byId(String id) {
        MedEntity e = dao.byId(id);
        return e == null ? null : toModel(e);
    }

    public List<Medicine> all() { return mapAll(dao.all()); }

    public void replaceCountry(String code, List<MedEntity> items) {
        dao.replaceCountry(code, items);   // atomic (single Room transaction)
    }

    private List<Medicine> mapAll(List<MedEntity> list) {
        List<Medicine> out = new ArrayList<>();
        for (MedEntity e : list) out.add(toModel(e));
        return out;
    }

    public static Medicine toModel(MedEntity e) {
        return new Medicine(e.id, e.name, e.nameLatin, e.countryCode,
                CountryCatalog.nameOf(e.countryCode),   // single source of country name
                Status.fromString(e.status), e.activeSubstance, e.group, e.prescription,
                e.brand, e.description, e.lawExcerpt, e.penalty, e.sourceUrl, e.imageUrl);
    }

    private String readAsset(String name) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = appCtx.getAssets().open(name);
             BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}