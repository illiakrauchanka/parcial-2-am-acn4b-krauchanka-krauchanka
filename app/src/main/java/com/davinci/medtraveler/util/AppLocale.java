package com.davinci.medtraveler.util;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import androidx.annotation.NonNull;

import com.davinci.medtraveler.data.CatalogJson;

import java.util.Locale;

/**
 * Resolves the active UI language to one of the supported catalog langs
 * ({@code es / en / uk / be / zh}). Used so the catalog cache and the seed are
 * re-fetched in the language the user actually sees the app in, not a random one.
 *
 * For Android 13+ we read the user's selected app locales via {@code AppCompatDelegate}
 * is the right call, but to keep this dependency-light and avoid pulling AppCompat into
 * the data layer, we read the resource configuration's locales: this reflects the
 * in-effect application locale (set by the system or, eventually, by an in-app language
 * switcher that calls {@code LocaleManager.applyChangesToActivies}).
 */
public final class AppLocale {
    private AppLocale() {}

    @NonNull public static String current(Context ctx) {
        Locale best = primaryLocale(ctx);
        String lang = best == null ? null : best.getLanguage();
        return CatalogJson.coerceLang(lang);
    }

    private static Locale primaryLocale(Context ctx) {
        Configuration cfg = ctx.getResources().getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!cfg.getLocales().isEmpty()) return cfg.getLocales().get(0);
            return null;
        }
        //noinspection deprecation
        return cfg.locale;
    }
}