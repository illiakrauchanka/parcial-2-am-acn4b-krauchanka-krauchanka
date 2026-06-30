package com.davinci.medtraveler.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.davinci.medtraveler.R;
import com.davinci.medtraveler.data.CatalogRepo;
import com.davinci.medtraveler.model.Medicine;
import com.davinci.medtraveler.util.SearchFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class SearchActivity extends BaseActivity {
    private final List<Medicine> all = new ArrayList<>();
    private LinearLayout container;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentWithChrome(R.layout.activity_search);
        container = findViewById(R.id.results_container);

        ((EditText) findViewById(R.id.edit_search)).addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { render(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });

        Executors.newSingleThreadExecutor().execute(() -> {
            List<Medicine> loaded = new CatalogRepo(this).all();
            runOnUiThread(() -> { all.clear(); all.addAll(loaded); render(""); });
        });
    }

    private void render(String query) {
        container.removeAllViews();
        List<Medicine> results = SearchFilter.filter(all, query);
        findViewById(R.id.txt_empty).setVisibility(
                results.isEmpty() && !all.isEmpty() ? View.VISIBLE : View.GONE);
        for (Medicine m : results) {
            View row = getLayoutInflater().inflate(R.layout.row_search, container, false);
            ((TextView) row.findViewById(R.id.txt_result))
                    .setText(getString(R.string.search_line, m.name, m.countryName));
            row.setOnClickListener(v -> {
                Intent i = new Intent(this, MedicineDetailActivity.class);
                i.putExtra(MedicineListActivity.EXTRA_MED_ID, m.id);
                startActivity(i);
            });
            container.addView(row);
        }
    }
}
