package com.davinci.medtraveler.data;

import com.davinci.medtraveler.model.Medicine;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UserMedsRepo {
    public interface DoneCallback { void onDone(boolean ok); }
    public interface IdsCallback { void onResult(Set<String> ids); }
    public interface ListCallback { void onResult(List<MyMed> items); }

    public static final class MyMed {
        public final String medId, name, countryCode;
        public MyMed(String medId, String name, String countryCode) {
            this.medId = medId; this.name = name; this.countryCode = countryCode;
        }
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void addMyMed(String uid, Medicine m, DoneCallback cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", m.name);
        data.put("countryCode", m.countryCode);
        data.put("addedAt", System.currentTimeMillis());
        db.collection("users").document(uid).collection("myMeds").document(m.id)
                .set(data)
                .addOnSuccessListener(x -> cb.onDone(true))
                .addOnFailureListener(e -> cb.onDone(false));
    }

    public void removeMyMed(String uid, String medId, DoneCallback cb) {
        db.collection("users").document(uid).collection("myMeds").document(medId)
                .delete()
                .addOnSuccessListener(x -> cb.onDone(true))
                .addOnFailureListener(e -> cb.onDone(false));
    }

    public void loadMyMedIds(String uid, IdsCallback cb) {
        db.collection("users").document(uid).collection("myMeds").get()
                .addOnSuccessListener(snap -> {
                    Set<String> ids = new HashSet<>();
                    for (DocumentSnapshot d : snap.getDocuments()) ids.add(d.getId());
                    cb.onResult(ids);
                })
                .addOnFailureListener(e -> cb.onResult(new HashSet<>()));
    }

    public void loadMyMeds(String uid, ListCallback cb) {
        db.collection("users").document(uid).collection("myMeds").get()
                .addOnSuccessListener(snap -> {
                    List<MyMed> out = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments())
                        out.add(new MyMed(d.getId(), d.getString("name"), d.getString("countryCode")));
                    cb.onResult(out);
                })
                .addOnFailureListener(e -> cb.onResult(new ArrayList<>()));
    }

    /** Deletes all of the user's Firestore data in a single WriteBatch so the operation
     *  is atomic per policy: the {@code myMeds} subcollection (Firestore does NOT cascade
     *  subcollection deletes, so we collect and delete every doc) and the parent
     *  {@code users/{uid}} document itself. Intended to be called as part of the Play
     *  policy accounts-deletion flow, BEFORE {@code AuthManager.deleteAccount} wipes the
     *  Firebase Auth user (after Auth deletion the uid can no longer be trusted under the
     *  security rules). No-op (reports success) when there is simply nothing to delete. */
    public void deleteUserData(String uid, DoneCallback cb) {
        db.collection("users").document(uid).collection("myMeds").get()
                .addOnSuccessListener(snap -> {
                    WriteBatch batch = db.batch();
                    // delete every saved med in the subcollection
                    for (DocumentSnapshot d : snap.getDocuments()) batch.delete(d.getReference());
                    // delete the parent user document last, inside the same atomic batch
                    batch.delete(db.collection("users").document(uid));
                    batch.commit()
                            .addOnSuccessListener(x -> cb.onDone(true))
                            .addOnFailureListener(e -> cb.onDone(false));
                })
                // If the subcollection read fails (e.g., it never existed), still attempt to
                // delete the parent doc so we don't leak the user document.
                .addOnFailureListener(e -> db.collection("users").document(uid).delete()
                        .addOnSuccessListener(x -> cb.onDone(true))
                        .addOnFailureListener(e2 -> cb.onDone(false)));
    }
}
