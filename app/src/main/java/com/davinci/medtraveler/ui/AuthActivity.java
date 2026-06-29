package com.davinci.medtraveler.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.davinci.medtraveler.R;
import com.davinci.medtraveler.data.AuthManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;

public class AuthActivity extends AppCompatActivity {
    private static final int MIN_PASSWORD = 6;

    private final AuthManager auth = new AuthManager();
    private GoogleSignInClient googleClient;

    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                try {
                    GoogleSignInAccount acct = GoogleSignIn
                            .getSignedInAccountFromIntent(result.getData())
                            .getResult(ApiException.class);
                    auth.loginWithGoogle(acct.getIdToken(), ok -> {
                        Toast.makeText(this, ok ? R.string.auth_logged_in : R.string.auth_error,
                                Toast.LENGTH_SHORT).show();
                        if (ok) finish();
                    });
                } catch (ApiException e) {
                    Toast.makeText(this, R.string.auth_error, Toast.LENGTH_SHORT).show();
                }
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        EditText email = findViewById(R.id.edit_email);
        EditText pass = findViewById(R.id.edit_password);
        Button login = findViewById(R.id.btn_login);
        Button register = findViewById(R.id.btn_register);
        Button google = findViewById(R.id.btn_google);

        login.setOnClickListener(v -> submit(email, pass, false, login, register));
        register.setOnClickListener(v -> submit(email, pass, true, login, register));

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(this, gso);
        google.setOnClickListener(v -> {
            googleClient.signOut();
            googleLauncher.launch(googleClient.getSignInIntent());
        });
    }

    /** Validates inputs, then calls login/register. Disables both buttons while in flight. */
    private void submit(EditText email, EditText pass, boolean isRegister, Button a, Button b) {
        String e = email.getText().toString().trim();
        String p = pass.getText().toString();

        if (e.isEmpty()) {
            email.setError(getString(R.string.auth_err_email_required));
            email.requestFocus();
            return;
        }
        if (p.length() < MIN_PASSWORD) {
            pass.setError(getString(R.string.auth_err_password_short, MIN_PASSWORD));
            pass.requestFocus();
            return;
        }

        a.setEnabled(false);
        b.setEnabled(false);
        AuthManager.AuthCallback cb = ok -> {
            runOnUiThread(() -> { a.setEnabled(true); b.setEnabled(true); });
            Toast.makeText(this, ok ? R.string.auth_logged_in : R.string.auth_error, Toast.LENGTH_SHORT).show();
            if (ok) finish();
        };
        if (isRegister) auth.register(e, p, cb);
        else auth.login(e, p, cb);
    }
}
