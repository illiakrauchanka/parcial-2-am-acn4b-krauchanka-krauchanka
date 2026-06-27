package com.davinci.medtraveler.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.davinci.medtraveler.R;
import com.davinci.medtraveler.data.AuthManager;
import com.davinci.medtraveler.data.UserMedsRepo;
import com.davinci.medtraveler.model.CountryCatalog;

import java.util.List;

public class ProfileActivity extends BaseActivity {
    private final AuthManager auth = new AuthManager();
    private final UserMedsRepo repo = new UserMedsRepo();
    private LinearLayout container;
    private TextView empty;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentWithChrome(R.layout.activity_profile);
        container = findViewById(R.id.my_meds_container);
        empty = findViewById(R.id.txt_empty);
        findViewById(R.id.btn_logout).setOnClickListener(v -> { auth.logout(); finish(); });

        String uid = auth.currentUid();
        if (uid == null) { finish(); return; }
        load(uid);
    }

    private void load(String uid) { repo.loadMyMeds(uid, items -> render(uid, items)); }

    private void render(String uid, List<UserMedsRepo.MyMed> items) {
        container.removeAllViews();
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        for (UserMedsRepo.MyMed it : items) {
            View row = getLayoutInflater().inflate(R.layout.row_my_med, container, false);
            String country = it.countryCode == null ? "" : CountryCatalog.nameOf(it.countryCode);
            ((TextView) row.findViewById(R.id.txt_name)).setText(
                    getString(R.string.my_med_line, it.name, country));
            ((Button) row.findViewById(R.id.btn_remove)).setOnClickListener(v ->
                    repo.removeMyMed(uid, it.medId, ok -> {
                        if (ok) load(uid);
                        else Toast.makeText(this, R.string.trip_remove_fail, Toast.LENGTH_SHORT).show();
                    }));
            container.addView(row);
        }
    }
}
