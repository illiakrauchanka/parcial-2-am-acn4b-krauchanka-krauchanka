package com.davinci.medtraveler.data;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Resolves a medicine brand name to its active ingredient via the public OpenFDA
 *  Drug Label API (https://api.fda.gov/drug/label.json — no API key for low volume).
 *  Network work runs on a private executor; callbacks fire on the main thread, matching
 *  the CatalogUpdater pattern. JSON parsing is a pure static method for JVM tests. */
public class DrugInfoRepo {

    public interface Callback {
        void onFound(String activeIngredient);
        void onNotFound();
        void onError(Exception e);
    }

    /** Visible for tests: production code always uses the real OpenFDA base. */
    static String baseUrl = "https://api.fda.gov/drug/label.json";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public void lookup(String brandName, Callback cb) {
        if (brandName == null || brandName.trim().isEmpty()) {
            main.post(cb::onNotFound);
            return;
        }
        String q = brandName.trim();
        io.execute(() -> {
            try {
                HttpUrl url = HttpUrl.parse(baseUrl).newBuilder()
                        .addQueryParameter("search",
                                "openfda.brand_name:\"" + q + "\"")
                        .addQueryParameter("limit", "1")
                        .build();
                try (Response r = client.newCall(
                        new Request.Builder().url(url).build()).execute()) {
                    // OpenFDA answers 404 with an error JSON body when nothing matches —
                    // that is "not found", not a transport failure.
                    String body = r.body() == null ? null : r.body().string();
                    String ingredient = parseActiveIngredient(body);
                    if (ingredient != null) main.post(() -> cb.onFound(ingredient));
                    else main.post(cb::onNotFound);
                }
            } catch (Exception e) {
                main.post(() -> cb.onError(e));
            }
        });
    }

    public void shutdown() { io.shutdown(); }

    /** Extracts the active ingredient from an OpenFDA drug/label response.
     *  Prefers openfda.generic_name[0]; falls back to active_ingredient[0].
     *  Returns a lowercase string, or null when absent/malformed. */
    public static String parseActiveIngredient(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray results = root.optJSONArray("results");
            if (results == null || results.length() == 0) return null;
            JSONObject first = results.getJSONObject(0);
            JSONObject openfda = first.optJSONObject("openfda");
            if (openfda != null) {
                JSONArray generic = openfda.optJSONArray("generic_name");
                if (generic != null && generic.length() > 0) {
                    String s = generic.optString(0, "").trim();
                    if (!s.isEmpty()) return s.toLowerCase(Locale.ROOT);
                }
            }
            JSONArray active = first.optJSONArray("active_ingredient");
            if (active != null && active.length() > 0) {
                String s = active.optString(0, "").trim();
                if (!s.isEmpty()) return s.toLowerCase(Locale.ROOT);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
