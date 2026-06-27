package com.davinci.medtraveler.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import com.davinci.medtraveler.R;
import com.davinci.medtraveler.data.CatalogRepo;
import com.davinci.medtraveler.model.CountryCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class WelcomeActivity extends AppCompatActivity {

    private static final int MAX_COUNTRIES = 3;

    private LinearLayout spinnerContainer;
    private CheckBox eula;
    private Button addBtn, checkBtn;
    private final List<Spinner> spinners = new ArrayList<>();
    private boolean rebuilding = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        Executors.newSingleThreadExecutor().execute(() -> new CatalogRepo(this).seedIfEmpty());

        spinnerContainer = findViewById(R.id.spinner_container);
        eula = findViewById(R.id.chk_eula);
        addBtn = findViewById(R.id.btn_add_country);
        checkBtn = findViewById(R.id.btn_check);

        eula.setOnCheckedChangeListener((b, c) -> updateCheckEnabled());
        addBtn.setOnClickListener(v -> { if (spinners.size() < MAX_COUNTRIES) addSpinner(); });
        checkBtn.setOnClickListener(v -> {
            List<String> codes = selectedCodes(-1);
            Intent i = new Intent(this, MedicineListActivity.class);
            i.putExtra(MedicineListActivity.EXTRA_COUNTRY_CODES, codes.toArray(new String[0]));
            startActivity(i);
        });

        addSpinner(); // first spinner
    }

    private void addSpinner() {
        // Row: spinner + remove button (remove hidden on the first row).
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Spinner sp = new Spinner(this);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sp.setLayoutParams(spLp);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!rebuilding) rebuild();
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });

        Button remove = new Button(this);
        remove.setText(R.string.welcome_remove_country);
        remove.setOnClickListener(v -> {
            spinners.remove(sp);
            spinnerContainer.removeView(row);
            rebuild();
        });

        row.addView(sp);
        row.addView(remove);
        spinners.add(sp);
        spinnerContainer.addView(row);
        remove.setVisibility(spinners.size() == 1 ? View.GONE : View.VISIBLE);

        rebuild();
    }

    /** Recompute every spinner's options excluding others' selections; refresh button states. */
    private void rebuild() {
        rebuilding = true;
        for (int i = 0; i < spinners.size(); i++) {
            Spinner sp = spinners.get(i);
            String current = codeOf(sp);
            List<String> others = selectedCodes(i);
            List<CountryCatalog.Country> options = CountryCatalog.available(others);
            CountrySpinnerAdapter adapter = new CountrySpinnerAdapter(this, options);
            sp.setAdapter(adapter);
            if (current != null) {
                for (int k = 0; k < options.size(); k++) {
                    if (options.get(k).code.equals(current)) { sp.setSelection(k); break; }
                }
            }
            View parent = (View) sp.getParent();
            if (parent instanceof LinearLayout && ((LinearLayout) parent).getChildCount() > 1) {
                ((LinearLayout) parent).getChildAt(1)
                        .setVisibility(spinners.size() == 1 ? View.GONE : View.VISIBLE);
            }
        }
        addBtn.setVisibility(spinners.size() < MAX_COUNTRIES ? View.VISIBLE : View.GONE);
        rebuilding = false;
        updateCheckEnabled();
    }

    private void updateCheckEnabled() {
        checkBtn.setEnabled(eula.isChecked() && !selectedCodes(-1).isEmpty());
    }

    private String codeOf(Spinner sp) {
        Object sel = sp.getSelectedItem();
        return sel instanceof CountryCatalog.Country ? ((CountryCatalog.Country) sel).code : null;
    }

    /** Codes selected in all spinners except index {@code exclude} (use -1 for none). */
    private List<String> selectedCodes(int exclude) {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < spinners.size(); i++) {
            if (i == exclude) continue;
            String code = codeOf(spinners.get(i));
            if (code != null && !codes.contains(code)) codes.add(code);
        }
        return codes;
    }
}
