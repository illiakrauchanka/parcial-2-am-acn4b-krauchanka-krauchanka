package com.davinci.medtraveler.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.davinci.medtraveler.R;
import com.davinci.medtraveler.data.AuthManager;
import com.davinci.medtraveler.data.CatalogRepo;
import com.davinci.medtraveler.data.UserMedsRepo;
import com.davinci.medtraveler.model.Medicine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MedicineDetailActivity extends AppCompatActivity {

    private static final int LAW_COLLAPSED_MAX_LINES = 3;

    private final AuthManager auth = new AuthManager();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Medicine medicine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medicine_detail);

        wireBackButton();

        String medId = getIntent().getStringExtra(MedicineListActivity.EXTRA_MED_ID);
        io.execute(() -> {
            Medicine m = new CatalogRepo(this).byId(medId);
            runOnUiThread(() -> {
                if (m == null) {
                    Toast.makeText(this, R.string.detail_load_error, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                medicine = m;
                bindAll();
            });
        });
    }

    private void bindAll() {
        bindHeader();
        bindStatusBadge(findViewById(R.id.txt_status_badge));
        bindBodyTexts();
        renderKeyFacts();
        wireOpenSourceButton();
        wireLawExcerptToggle();
        wireTakeMedButton();
    }

    @SuppressLint("SetTextI18n")
    private void bindHeader() {
        TextView txtCountry = findViewById(R.id.txt_country);
        TextView txtMedicineName = findViewById(R.id.txt_medicine_name);
        TextView txtActiveSubstance = findViewById(R.id.txt_active_substance);
        ImageView imgMedicine = findViewById(R.id.img_medicine);

        txtCountry.setText(medicine.countryName);
        // Show the localized name, with the Latin INN in parentheses when it is present
        // and different — so the substance is identifiable across UI languages.
        String shown = medicine.displayName();
        if (medicine.nameLatin != null && !medicine.nameLatin.isEmpty()
                && !medicine.nameLatin.equals(medicine.name)) {
            shown = shown + " (" + medicine.nameLatin + ")";
        }
        txtMedicineName.setText(shown);
        txtActiveSubstance.setText(
                getString(R.string.fact_label_substance) + ": " + medicine.activeSubstance);
        Glide.with(this).load(medicine.imageUrl).into(imgMedicine);
        findViewById(R.id.img_flag).setVisibility(View.GONE);
    }

    private void bindStatusBadge(TextView badge) {
        int colorRes, textRes, descRes;
        switch (medicine.status) {
            case ALLOWED:
                colorRes = R.color.status_allowed; textRes = R.string.status_allowed;
                descRes = R.string.status_desc_allowed; break;
            case RESTRICTED:
                colorRes = R.color.status_restricted; textRes = R.string.status_restricted;
                descRes = R.string.status_desc_restricted; break;
            case PENAL:
            default:
                colorRes = R.color.status_penal; textRes = R.string.status_penal;
                descRes = R.string.status_desc_penal; break;
        }
        badge.setText(textRes);
        badge.setBackgroundTintList(ContextCompat.getColorStateList(this, colorRes));
        final int finalDescRes = descRes;
        badge.setOnClickListener(v ->
                Toast.makeText(this, finalDescRes, Toast.LENGTH_LONG).show());
    }

    private void bindBodyTexts() {
        ((TextView) findViewById(R.id.txt_description)).setText(medicine.description);
        ((TextView) findViewById(R.id.txt_law_excerpt)).setText(medicine.lawExcerpt);
        ((TextView) findViewById(R.id.txt_penalty)).setText(medicine.penalty);
    }

    private void renderKeyFacts() {
        LinearLayout container = findViewById(R.id.key_facts_container);
        container.removeAllViews();
        addFact(container, getString(R.string.fact_label_substance), medicine.activeSubstance);
        addFact(container, getString(R.string.fact_label_group), medicine.group);
        addFact(container, getString(R.string.fact_label_prescription), medicine.prescription);
        addFact(container, getString(R.string.fact_label_brand), medicine.brand);
    }

    private void addFact(LinearLayout container, String label, String value) {
        View row = getLayoutInflater().inflate(R.layout.row_key_fact, container, false);
        ((TextView) row.findViewById(R.id.fact_label)).setText(label);
        ((TextView) row.findViewById(R.id.fact_value)).setText(value);
        container.addView(row);
    }

    private void wireBackButton() {
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());
    }

    private void wireOpenSourceButton() {
        Button btnOpenSource = findViewById(R.id.btn_open_source);
        btnOpenSource.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(medicine.sourceUrl));
            if (i.resolveActivity(getPackageManager()) != null) startActivity(i);
            else Toast.makeText(this, R.string.contact_no_app, Toast.LENGTH_SHORT).show();
        });
    }

    private void wireLawExcerptToggle() {
        TextView txt = findViewById(R.id.txt_law_excerpt);
        txt.setMaxLines(LAW_COLLAPSED_MAX_LINES);
        txt.setEllipsize(TextUtils.TruncateAt.END);
        txt.setOnClickListener(v -> {
            if (txt.getMaxLines() == LAW_COLLAPSED_MAX_LINES) {
                txt.setMaxLines(Integer.MAX_VALUE);
                txt.setEllipsize(null);
            } else {
                txt.setMaxLines(LAW_COLLAPSED_MAX_LINES);
                txt.setEllipsize(TextUtils.TruncateAt.END);
            }
        });
    }

    private void wireTakeMedButton() {
        Button btn = findViewById(R.id.btn_add_trip);
        btn.setText(R.string.detail_take_med);
        btn.setOnClickListener(v -> {
            String uid = auth.currentUid();
            if (uid == null) {
                startActivity(new Intent(this, AuthActivity.class));
                return;
            }
            btn.setEnabled(false);
            new UserMedsRepo().addMyMed(uid, medicine, ok -> {
                Toast.makeText(this,
                        ok ? R.string.detail_taken_ok : R.string.detail_added_fail,
                        Toast.LENGTH_SHORT).show();
                btn.setEnabled(true);
            });
        });
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }
}
