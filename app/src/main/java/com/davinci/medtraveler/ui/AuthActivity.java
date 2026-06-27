package com.davinci.medtraveler.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.davinci.medtraveler.R;
import com.davinci.medtraveler.data.AuthManager;

public class AuthActivity extends AppCompatActivity {
    private final AuthManager auth = new AuthManager();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        EditText email = findViewById(R.id.edit_email);
        EditText pass = findViewById(R.id.edit_password);
        ((Button) findViewById(R.id.btn_login)).setOnClickListener(v ->
                auth.login(email.getText().toString().trim(), pass.getText().toString(), this::onResult));
        ((Button) findViewById(R.id.btn_register)).setOnClickListener(v ->
                auth.register(email.getText().toString().trim(), pass.getText().toString(), this::onResult));
    }

    private void onResult(boolean ok) {
        Toast.makeText(this, ok ? R.string.auth_logged_in : R.string.auth_error, Toast.LENGTH_SHORT).show();
        if (ok) finish();
    }
}
