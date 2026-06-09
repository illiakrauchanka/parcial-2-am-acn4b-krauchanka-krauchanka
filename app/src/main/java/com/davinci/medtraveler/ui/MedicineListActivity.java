package com.davinci.medtraveler.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.davinci.medtraveler.R;
import com.davinci.medtraveler.data.FirestoreRepo;
import com.davinci.medtraveler.model.Medicine;
import com.davinci.medtraveler.model.Status;
import com.davinci.medtraveler.util.SearchFilter;

import java.util.ArrayList;
import java.util.List;

public class MedicineListActivity extends AppCompatActivity {

    public static final String EXTRA_MED_ID = "med_id";

    private final FirestoreRepo repo = new FirestoreRepo();
    private final List<Medicine> all = new ArrayList<>();
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medicine_list);

        String code = getIntent().getStringExtra(CountryListActivity.EXTRA_COUNTRY_CODE);
        String name = getIntent().getStringExtra(CountryListActivity.EXTRA_COUNTRY_NAME);
        ((TextView) findViewById(R.id.txt_country_title)).setText(name);
        container = findViewById(R.id.medicines_container);

        EditText search = findViewById(R.id.edit_search);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { render(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });

        repo.loadMedicines(code, meds -> {
            all.clear();
            all.addAll(meds);
            render("");
        });
    }

    private void render(String query) {
        container.removeAllViews();
        for (Medicine m : SearchFilter.filter(all, query)) addRow(m);
    }

    private void addRow(Medicine m) {
        View row = getLayoutInflater().inflate(R.layout.row_medicine, container, false);
        ((TextView) row.findViewById(R.id.txt_medicine_name)).setText(m.name);
        ((TextView) row.findViewById(R.id.txt_medicine_substance)).setText(m.activeSubstance);
        TextView badge = row.findViewById(R.id.txt_status_badge);
        applyBadge(badge, m.status);
        Glide.with(this).load(m.imageUrl).into((ImageView) row.findViewById(R.id.img_medicine));
        row.setOnClickListener(v -> {
            Intent i = new Intent(this, MedicineDetailActivity.class);
            i.putExtra(EXTRA_MED_ID, m.id);
            startActivity(i);
        });
        container.addView(row);
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
}
