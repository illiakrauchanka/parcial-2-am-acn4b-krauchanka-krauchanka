package com.davinci.medtraveler.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.davinci.medtraveler.R;
import com.davinci.medtraveler.data.AuthManager;
import com.davinci.medtraveler.data.FirestoreRepo;
import com.davinci.medtraveler.model.Status;
import com.davinci.medtraveler.model.TripItem;

public class TripListActivity extends AppCompatActivity {

    private final FirestoreRepo repo = new FirestoreRepo();
    private final AuthManager auth = new AuthManager();
    private LinearLayout container;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_list);
        container = findViewById(R.id.trip_container);
        empty = findViewById(R.id.txt_empty);
        reload();
    }

    private void reload() {
        String uid = auth.currentUid();
        if (uid == null) { empty.setVisibility(View.VISIBLE); return; }
        repo.loadTrip(uid, items -> {
            container.removeAllViews();
            empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            for (TripItem it : items) addRow(uid, it);
        });
    }

    private void addRow(String uid, TripItem it) {
        View row = getLayoutInflater().inflate(R.layout.row_trip_item, container, false);
        ((TextView) row.findViewById(R.id.txt_name)).setText(it.name);
        ((TextView) row.findViewById(R.id.txt_country)).setText(it.countryName);
        View dot = row.findViewById(R.id.status_dot);
        dot.setBackgroundTintList(ContextCompat.getColorStateList(this, colorFor(it.status)));
        ImageButton remove = row.findViewById(R.id.btn_remove);
        remove.setOnClickListener(v -> repo.removeFromTrip(uid, it.medId, ok -> {
            if (ok) { container.removeView(row); checkEmpty(); }
            else Toast.makeText(this, R.string.trip_remove_fail, Toast.LENGTH_SHORT).show();
        }));
        container.addView(row);
    }

    private void checkEmpty() {
        empty.setVisibility(container.getChildCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private int colorFor(Status status) {
        switch (status) {
            case ALLOWED: return R.color.status_allowed;
            case PENAL: return R.color.status_penal;
            case RESTRICTED:
            default: return R.color.status_restricted;
        }
    }
}
