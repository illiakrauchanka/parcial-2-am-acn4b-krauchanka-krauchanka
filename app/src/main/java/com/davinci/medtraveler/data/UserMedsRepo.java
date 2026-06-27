package com.davinci.medtraveler.data;

import com.davinci.medtraveler.model.Medicine;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

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
}
