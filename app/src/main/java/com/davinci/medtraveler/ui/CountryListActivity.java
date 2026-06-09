package com.davinci.medtraveler.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.davinci.medtraveler.R;
import com.davinci.medtraveler.data.FirestoreRepo;
import com.davinci.medtraveler.model.Country;

public class CountryListActivity extends AppCompatActivity {

    public static final String EXTRA_COUNTRY_CODE = "country_code";
    public static final String EXTRA_COUNTRY_NAME = "country_name";

    private final FirestoreRepo repo = new FirestoreRepo();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_country_list);

        findViewById(R.id.btn_my_list).setOnClickListener(v ->
                startActivity(new Intent(this, TripListActivity.class)));

        LinearLayout container = findViewById(R.id.countries_container);
        repo.loadCountries(countries -> {
            container.removeAllViews();
            for (Country c : countries) addCountryRow(container, c);
        });
    }

    private void addCountryRow(LinearLayout container, Country c) {
        View row = getLayoutInflater().inflate(R.layout.row_country, container, false);
        ((TextView) row.findViewById(R.id.txt_country_name)).setText(c.name);
        ImageView flag = row.findViewById(R.id.img_flag);
        Glide.with(this).load(c.flagUrl).into(flag);
        row.setOnClickListener(v -> {
            Intent i = new Intent(this, MedicineListActivity.class);
            i.putExtra(EXTRA_COUNTRY_CODE, c.code);
            i.putExtra(EXTRA_COUNTRY_NAME, c.name);
            startActivity(i);
        });
        container.addView(row);
    }
}
