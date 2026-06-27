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

public class CatalogUpdater {

    public interface Callback { void onAllDone(int updated, int total); }

    private final CatalogRepo repo;
    private final CatalogMeta meta;
    private final OkHttpClient http = new OkHttpClient();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public CatalogUpdater(Context ctx) {
        this.repo = new CatalogRepo(ctx);
        this.meta = new CatalogMeta(ctx);
    }

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
        String url = Catalog.RTDB_BASE + "/meds/" + code + ".json";
        Request req = new Request.Builder().url(url).get().build();
        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful() || res.body() == null) return false;
            String json = res.body().string();
            if (json == null || json.equals("null") || json.trim().isEmpty()) return false;
            CatalogJson.CountryPayload p = CatalogJson.parseCountry(code, json);
            repo.replaceCountry(code, p.items);
            meta.save(code, p.version, p.updatedAt, now);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void shutdown() { io.shutdown(); }
}
