package com.davinci.medtraveler.data;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AuthManager {

    public interface AuthCallback {
        void onResult(boolean success, String uid);
    }

    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    public String currentUid() {
        FirebaseUser u = auth.getCurrentUser();
        return u == null ? null : u.getUid();
    }

    public void signInAnonymously(@NonNull AuthCallback cb) {
        if (currentUid() != null) {
            cb.onResult(true, currentUid());
            return;
        }
        auth.signInAnonymously().addOnCompleteListener(task ->
                cb.onResult(task.isSuccessful(), currentUid()));
    }
}
