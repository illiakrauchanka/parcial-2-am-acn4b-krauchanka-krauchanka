package com.davinci.medtraveler.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.davinci.medtraveler.R;

/** Launcher splash. First launch ever plays the stork intro video
 *  ({@code res/raw/splash_stork.mp4}); every later launch shows the static
 *  splash image briefly. A tap anywhere skips straight to {@link WelcomeActivity},
 *  and ANY playback error also falls through to Welcome — the splash must never
 *  be able to trap the user. Deliberately NOT a {@link BaseActivity}: no toolbar,
 *  no drawer, fullscreen black. */
public class SplashActivity extends AppCompatActivity {

    private static final String PREFS = "splash";
    private static final String KEY_VIDEO_SHOWN = "video_shown";
    /** How long the static (non-first-run) splash stays up. */
    private static final long STATIC_DELAY_MS = 1200L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean navigated = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        findViewById(R.id.splash_root).setOnClickListener(v -> goNext());

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean firstRun = !prefs.getBoolean(KEY_VIDEO_SHOWN, false);
        if (firstRun) {
            // Mark as shown immediately: if playback crashes we still never
            // replay the 7s video on every subsequent cold start.
            prefs.edit().putBoolean(KEY_VIDEO_SHOWN, true).apply();
            playVideo();
        } else {
            showStatic();
        }
    }

    private void playVideo() {
        VideoView video = findViewById(R.id.splash_video);
        video.setVisibility(View.VISIBLE);
        video.setVideoURI(Uri.parse(
                "android.resource://" + getPackageName() + "/" + R.raw.splash_stork));
        video.setOnCompletionListener(mp -> goNext());
        video.setOnErrorListener((mp, what, extra) -> { goNext(); return true; });
        video.start();
    }

    private void showStatic() {
        ImageView image = findViewById(R.id.splash_image);
        image.setVisibility(View.VISIBLE);
        handler.postDelayed(this::goNext, STATIC_DELAY_MS);
    }

    /** Idempotent hand-off to the real start screen. */
    private void goNext() {
        if (navigated) return;
        navigated = true;
        startActivity(new Intent(this, WelcomeActivity.class));
        finish();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
