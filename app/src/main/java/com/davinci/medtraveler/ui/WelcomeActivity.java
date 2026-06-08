package com.davinci.medtraveler.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.davinci.medtraveler.R;
import com.davinci.medtraveler.data.AuthManager;

public class WelcomeActivity extends AppCompatActivity {

    private final AuthManager auth = new AuthManager();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        Button btnEnter = findViewById(R.id.btn_enter);
        ProgressBar progress = findViewById(R.id.progress);

        btnEnter.setOnClickListener(v -> {
            btnEnter.setEnabled(false);
            progress.setVisibility(View.VISIBLE);
            auth.signInAnonymously((success, uid) -> {
                progress.setVisibility(View.GONE);
                if (success) {
                    startActivity(new Intent(this, CountryListActivity.class));
                } else {
                    btnEnter.setEnabled(true);
                    Toast.makeText(this, R.string.welcome_auth_error, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}
