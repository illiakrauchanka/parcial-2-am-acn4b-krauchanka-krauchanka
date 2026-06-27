package com.davinci.medtraveler.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.davinci.medtraveler.R;
import com.davinci.medtraveler.model.CountryCatalog;

import java.util.List;

public class CountrySpinnerAdapter extends ArrayAdapter<CountryCatalog.Country> {
    public CountrySpinnerAdapter(Context ctx, List<CountryCatalog.Country> data) { super(ctx, 0, data); }

    @NonNull @Override public View getView(int p, View cv, @NonNull ViewGroup parent) { return bind(p, cv, parent); }
    @Override public View getDropDownView(int p, View cv, @NonNull ViewGroup parent) { return bind(p, cv, parent); }

    private View bind(int pos, View cv, ViewGroup parent) {
        View v = cv != null ? cv
                : LayoutInflater.from(getContext()).inflate(R.layout.spinner_country_row, parent, false);
        CountryCatalog.Country c = getItem(pos);
        if (c != null) {
            ((ImageView) v.findViewById(R.id.img_flag)).setImageResource(c.flagRes);
            ((TextView) v.findViewById(R.id.txt_country)).setText(c.name);
        }
        return v;
    }
}
