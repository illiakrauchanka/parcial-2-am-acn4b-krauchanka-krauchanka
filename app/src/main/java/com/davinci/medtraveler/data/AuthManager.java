package com.davinci.medtraveler.data;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class AuthManager {
    public interface AuthCallback { void onResult(boolean ok); }

    /** Outcome of {@link #deleteAccount}. Distinguishes "needs re-authentication" from a
     *  plain failure because Google Play policy accounts-deletion MUST actually delete the
     *  Firebase user — but Firebase throws {@link FirebaseAuthRecentLoginRequiredException}
     *  when the last sign-in is stale, in which case we have to send the user through the
     *  login flow again BEFORE retrying the delete. */
    public enum DeleteResult { DELETED, NEEDS_REAUTH, FAILED }

    public interface DeleteCallback { void onResult(DeleteResult r); }

    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    public void register(String email, String password, AuthCallback cb) {
        try {
            auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(t -> cb.onResult(t.isSuccessful()));
        } catch (IllegalArgumentException ex) {
            // empty/null email/password would throw synchronously; report as failure
            cb.onResult(false);
        }
    }

    public void login(String email, String password, AuthCallback cb) {
        try {
            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(t -> cb.onResult(t.isSuccessful()));
        } catch (IllegalArgumentException ex) {
            cb.onResult(false);
        }
    }

    public void loginWithGoogle(String idToken, AuthCallback cb) {
        AuthCredential cred = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(cred).addOnCompleteListener(t -> cb.onResult(t.isSuccessful()));
    }

    /** Permanently deletes the signed-in Firebase user. Caller should also wipe the
     *  associated Firestore {@code users/{uid}} subtree (UserMedsRepo.deleteUserData) BEFORE
     *  calling this, because once the Auth user is gone we can no longer trust {@code uid}
     *  alone under the user's security rules. Fires {@link DeleteCallback#onResult} on the
     *  main thread. */
    public void deleteAccount(DeleteCallback cb) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) { cb.onResult(DeleteResult.FAILED); return; }
        u.delete()
                .addOnSuccessListener(x -> cb.onResult(DeleteResult.DELETED))
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthRecentLoginRequiredException) {
                        cb.onResult(DeleteResult.NEEDS_REAUTH);
                    } else {
                        cb.onResult(DeleteResult.FAILED);
                    }
                });
    }

    public void logout() { auth.signOut(); }

    public String currentUid() {
        FirebaseUser u = auth.getCurrentUser();
        return u == null ? null : u.getUid();
    }

    public boolean isLoggedIn() { return auth.getCurrentUser() != null; }
}