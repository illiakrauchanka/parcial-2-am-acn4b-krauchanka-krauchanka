package com.davinci.medtraveler.data;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AuthManager {
    public interface AuthCallback { void onResult(boolean ok); }

    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    public void register(String email, String password, AuthCallback cb) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(t -> cb.onResult(t.isSuccessful()));
    }

    public void login(String email, String password, AuthCallback cb) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(t -> cb.onResult(t.isSuccessful()));
    }

    public void logout() { auth.signOut(); }

    public String currentUid() {
        FirebaseUser u = auth.getCurrentUser();
        return u == null ? null : u.getUid();
    }

    public boolean isLoggedIn() { return auth.getCurrentUser() != null; }
}
