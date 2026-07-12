package com.davinci.medtraveler.ui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

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
        // Accounts-deletion entry point required by Google Play policy since 2022 — MUST
        // actually delete (logout alone is not enough). See confirmDeleteAccount().
        findViewById(R.id.btn_delete_account).setOnClickListener(v -> confirmDeleteAccount());

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
            // Catalog meds show "name — country"; scanned meds have no country, show the
            // active substance instead (or just the name when substance is unknown).
            String secondary = it.countryCode != null ? CountryCatalog.nameOf(it.countryCode)
                    : (it.activeSubstance != null ? it.activeSubstance : "");
            ((TextView) row.findViewById(R.id.txt_name)).setText(secondary.isEmpty()
                    ? it.name : getString(R.string.name_value_line, it.name, secondary));
            ((Button) row.findViewById(R.id.btn_remove)).setOnClickListener(v ->
                    repo.removeMyMed(uid, it.medId, ok -> {
                        if (ok) load(uid);
                        else Toast.makeText(this, R.string.trip_remove_fail, Toast.LENGTH_SHORT).show();
                    }));
            container.addView(row);
        }
    }

    /** Google Play requires an in-app path to delete the user account and their data
     *  (Account Deletion policy). This confirmation dialog wipes the Firestore
     *  {@code users/{uid}} subtree first (so security rules still accept the writes while
     *  the Auth user exists), then deletes the Firebase Auth user. If the Auth user's
     *  last sign-in is stale, Firebase rejects the delete with
     *  {@code FirebaseAuthRecentLoginRequiredException}; we then sign the user out and
     *  ask them to sign in again and retry — which is Firebase's required re-auth flow. */
    private void confirmDeleteAccount() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.account_delete_confirm_title)
                .setMessage(R.string.account_delete_confirm_body)
                .setPositiveButton(R.string.account_delete_yes,
                        (DialogInterface d, int w) -> doDelete())
                .setNegativeButton(R.string.account_delete_no, null)
                .show();
    }

    private void doDelete() {
        String uid = auth.currentUid();
        if (uid == null) { finish(); return; }
        // 1) wipe Firestore user data while the Auth user still exists (rules require auth.uid == uid)
        repo.deleteUserData(uid, firestoreOk -> {
            // 2) delete the Firebase Auth user — this is the real account deletion that
            //    Play policy cares about; we attempt it even if the Firestore wipe failed
            //    (a transient network error must not trap the user in the app forever).
            auth.deleteAccount(result -> runOnUiThread(() -> {
                switch (result) {
                    case DELETED:
                        Toast.makeText(this, R.string.account_deleted, Toast.LENGTH_SHORT).show();
                        // Sign out locally and bounce back to the start so the next session
                        // is a fresh, signed-out state.
                        auth.logout();
                        finish();
                        break;
                    case NEEDS_REAUTH:
                        // Firebase requires a recent sign-in. Log the user out and ask them
                        // to sign in again before retrying — this is the documented re-auth
                        // flow. We sign out so the next visit to AuthActivity is a clean login.
                        Toast.makeText(this, R.string.account_delete_reauth, Toast.LENGTH_LONG).show();
                        auth.logout();
                        finish();
                        break;
                    case FAILED:
                    default:
                        Toast.makeText(this, R.string.account_delete_failed, Toast.LENGTH_LONG).show();
                        break;
                }
            }));
        });
    }
}