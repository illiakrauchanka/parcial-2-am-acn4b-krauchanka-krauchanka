package com.davinci.medtraveler.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.davinci.medtraveler.R;
import com.davinci.medtraveler.data.CatalogMeta;
import com.davinci.medtraveler.data.CatalogRepo;
import com.davinci.medtraveler.data.CatalogUpdater;
import com.davinci.medtraveler.model.CountryCatalog;
import com.davinci.medtraveler.model.Medicine;
import com.davinci.medtraveler.model.Status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MedicineListActivity extends BaseActivity {

    public static final String EXTRA_COUNTRY_CODES = "country_codes";
    public static final String EXTRA_MED_ID = "med_id";

    protected CatalogRepo repo;
    protected CatalogMeta meta;
    protected CatalogUpdater updater;
    protected List<String> codes;
    private LinearLayout container;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final com.davinci.medtraveler.data.AuthManager auth =
            new com.davinci.medtraveler.data.AuthManager();
    private java.util.Set<String> myMedIds = new java.util.HashSet<>();

    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentWithChrome(R.layout.activity_medicine_list);
        bindViews();

        repo = new CatalogRepo(this);
        meta = new CatalogMeta(this);
        updater = new CatalogUpdater(this);

        String[] arr = getIntent().getStringArrayExtra(EXTRA_COUNTRY_CODES);
        codes = arr == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(arr));

        renderAll();
        updater.refreshCountries(codes, false, (updated, total) -> {
            if (updated > 0) { renderAll(); refreshDbStatus(); }
        });

        loadMyMeds();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMyMeds();
    }

    private void loadMyMeds() {
        String uid = auth.currentUid();
        if (uid == null) return;
        new com.davinci.medtraveler.data.UserMedsRepo().loadMyMedIds(uid, ids -> {
            myMedIds = ids;
            renderAll();
        });
    }

    /** Hook for subclasses to bind their own views before {@link #setContentWithChrome(int)}. */
    protected void bindViews() {
        container = findViewById(R.id.sections_container);
    }

    protected void renderAll() {
        io.execute(() -> {
            Map<String, List<Medicine>> byCountry = new LinkedHashMap<>();
            for (String code : codes) byCountry.put(code, repo.byCountry(code));
            runOnUiThread(() -> draw(byCountry));
        });
    }

    private void draw(Map<String, List<Medicine>> byCountry) {
        if (container == null) container = findViewById(R.id.sections_container);
        container.removeAllViews();
        for (String code : codes) {
            addHeader(code);
            for (Medicine m : byCountry.get(code)) addRow(m);
        }
    }

    private void addHeader(String code) {
        CountryCatalog.Country c = CountryCatalog.byCode(code);
        View h = getLayoutInflater().inflate(R.layout.row_country_section_header, container, false);
        if (c != null) {
            ((ImageView) h.findViewById(R.id.img_flag)).setImageResource(c.flagRes);
            ((TextView) h.findViewById(R.id.txt_country)).setText(c.name);
        }
        ((TextView) h.findViewById(R.id.txt_meta)).setText(
                getString(R.string.section_meta_format, meta.getVersion(code), meta.getUpdatedAt(code)));
        container.addView(h);
    }

    protected void addRow(Medicine m) {
        View row = getLayoutInflater().inflate(R.layout.row_medicine, container, false);
        ((TextView) row.findViewById(R.id.txt_medicine_name)).setText(m.name);
        ((TextView) row.findViewById(R.id.txt_medicine_subtitle)).setText(m.description);
        applyBadge(row.findViewById(R.id.txt_status_badge), m.status);
        Glide.with(this).load(m.imageUrl).into((ImageView) row.findViewById(R.id.img_medicine));
        decorateMine(row, m); // highlights meds saved by the logged-in user
        row.setOnClickListener(v -> {
            Intent i = new Intent(this, MedicineDetailActivity.class);
            i.putExtra(EXTRA_MED_ID, m.id);
            startActivity(i);
        });
        container.addView(row);
    }

    @Override protected void onUpdateDbRequested() {
        ensureNotifPermission();
        showProgress(true);
        android.widget.Toast.makeText(this, R.string.updating_db, android.widget.Toast.LENGTH_SHORT).show();
        updater.refreshCountries(codes, true, (updated, total) -> {
            showProgress(false);
            renderAll();
            refreshDbStatus();
            if (updated > 0) {
                CatalogNotifier.notifyUpdated(this, total, meta.lastUpdatedGlobal());
            } else {
                android.widget.Toast.makeText(this, R.string.db_no_changes,
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    protected void decorateMine(View row, Medicine m) {
        boolean mine = myMedIds.contains(m.id);
        row.findViewById(R.id.txt_mine_badge).setVisibility(mine ? View.VISIBLE : View.GONE);
        row.findViewById(R.id.row_root).setBackgroundResource(
                mine ? R.drawable.bg_mine_highlight : R.drawable.bg_card_row);
    }

    private void applyBadge(TextView badge, Status status) {
        int colorRes, textRes;
        switch (status) {
            case ALLOWED: colorRes = R.color.status_allowed; textRes = R.string.status_allowed; break;
            case PENAL: colorRes = R.color.status_penal; textRes = R.string.status_penal; break;
            case RESTRICTED:
            default: colorRes = R.color.status_restricted; textRes = R.string.status_restricted; break;
        }
        badge.setText(textRes);
        badge.setBackgroundTintList(ContextCompat.getColorStateList(this, colorRes));
    }

    @Override protected Intent scanIntent() {
        Intent i = super.scanIntent();
        i.putExtra(EXTRA_COUNTRY_CODES, codes.toArray(new String[0]));
        return i;
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
        updater.shutdown();
    }
}
