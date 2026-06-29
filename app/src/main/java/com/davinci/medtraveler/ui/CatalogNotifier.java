package com.davinci.medtraveler.ui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.davinci.medtraveler.R;

public final class CatalogNotifier {
    private static final String CHANNEL_ID = "catalog_updates";
    private static final int NOTIF_ID = 1001;

    private CatalogNotifier() {}

    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    ctx.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription(ctx.getString(R.string.notif_channel_desc));
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    public static void notifyUpdated(Context ctx, int countries, String date) {
        ensureChannel(ctx);

        Intent open = new Intent(ctx, WelcomeActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open, piFlags);

        String title = ctx.getString(R.string.notif_updated_title);
        String text = ctx.getResources().getQuantityString(
                R.plurals.notif_countries, countries, countries);
        String pretty = prettyDate(date);
        if (!pretty.isEmpty()) text += ctx.getString(R.string.notif_date_suffix, pretty);

        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.notif_catalog);
        rv.setTextViewText(R.id.notif_title, title);
        rv.setTextViewText(R.id.notif_text, text);

        Notification n = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_refresh)
                .setColor(ContextCompat.getColor(ctx, R.color.color_primary))
                .setContentTitle(title)
                .setContentText(text)
                .setCustomContentView(rv)
                .setCustomBigContentView(rv)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();

        try {
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n);
        } catch (SecurityException ignored) {
        }
    }

    private static String prettyDate(String iso) {
        if (iso == null) return "";
        int t = iso.indexOf('T');
        return t > 0 ? iso.substring(0, t) : iso;
    }
}
