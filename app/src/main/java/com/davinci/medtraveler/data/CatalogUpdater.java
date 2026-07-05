package com.davinci.medtraveler.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches per-country medicine catalog nodes from Realtime Database and replaces the
 * on-device Room cache. Each request targets a localized node:
 * {@code <RTDB_BASE>/meds/<countryCode>/<lang>.json} — the RTDB stores one full node per
 * language with localized {@code name}, {@code names} and the language-independent
 * {@code nameLatin} (INN). When the UI language changes, callers should force-refresh
 * ({@code force=true}) so cached rows written for the previous language get overwritten.
 *
 * <p>The old single-language RTDB path {@code /meds/<countryCode>.json} is still honoured
 * when {@code lang == null}: the falling-back behaviour lets an un-upgraded RTDB keep
 * serving requests without breaking the client.
 */
public class CatalogUpdater {

    public interface Callback { void onAllDone(int updated, int total); }

    private final CatalogRepo repo;
    private final CatalogMeta meta;
    private final OkHttpClient http = new OkHttpClient();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String rtdbBase;   // overridable so instrumented tests can point at MockWebServer

    public CatalogUpdater(Context ctx) { this(ctx, Catalog.RTDB_BASE, CatalogJson.DEFAULT_LANG); }

    /** Test seam: lets {@code CatalogUpdaterTest} point at a MockWebServer URL. Production
     *  callers should use the single-arg constructor (defaults to {@link Catalog#RTDB_BASE}). */
    CatalogUpdater(Context ctx, String rtdbBase) { this(ctx, rtdbBase, CatalogJson.DEFAULT_LANG); }

    /** Full control constructor: lets a caller pick both the base URL and the catalog
     *  language. Used by the app's refresh path so cached localized rows match the UI. */
    public CatalogUpdater(Context ctx, String rtdbBase, String lang) {
        this.repo = new CatalogRepo(ctx);
        this.meta = new CatalogMeta(ctx);
        this.rtdbBase = rtdbBase;
        this.lang = CatalogJson.coerceLang(lang);
    }

    private final String lang;

    /** Refresh the listed countries for the configured language. With {@code force=false}
     *  a country is skipped when its cache is still fresh (see {@link CatalogMeta}); with
     *  {@code force=true} every country is re-fetched, which is what callers should use
     *  after the UI language changes so localized names are rewritten in Room. */
    public void refreshCountries(List<String> codes, boolean force, Callback cb) {
        io.execute(() -> {
            long now = System.currentTimeMillis();
            int updated = 0;
            for (String code : codes) {
                if (!force && meta.freshFor(code, now)) continue;   // cache fresh: skip
                if (downloadOne(code, now)) updated++;
            }
            final int done = updated;
            final int total = codes.size();
            main.post(() -> cb.onAllDone(done, total));
        });
    }

    private boolean downloadOne(String code, long now) {
        // Localized node first; fall back to the legacy single-language node when the
        // localized path 404s, so an un-upgraded RTDB still serves the app.
        String url = rtdbBase + "/meds/" + code + "/" + lang + ".json";
        boolean ok = downloadOneFrom(url, code, now);
        if (!ok && !CatalogJson.DEFAULT_LANG.equals(lang)) {
            ok = downloadOneFrom(rtdbBase + "/meds/" + code + ".json", code, now);
        }
        return ok;
    }

    private boolean downloadOneFrom(String url, String code, long now) {
        Request req = new Request.Builder().url(url).get().build();
        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful() || res.body() == null) return false;
            String json = res.body().string();
            if (json == null || json.equals("null") || json.trim().isEmpty()) return false;
            CatalogJson.CountryPayload p = CatalogJson.parseCountry(code, json, lang);
            repo.replaceCountry(code, p.items);
            meta.save(code, p.version, p.updatedAt, now);
            meta.setSeededLang(lang);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void shutdown() { io.shutdown(); }
}