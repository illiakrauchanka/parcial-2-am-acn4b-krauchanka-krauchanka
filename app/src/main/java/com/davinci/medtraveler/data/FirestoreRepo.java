package com.davinci.medtraveler.data;

import com.davinci.medtraveler.model.Country;
import com.davinci.medtraveler.model.Medicine;
import com.davinci.medtraveler.model.Status;
import com.davinci.medtraveler.model.TripItem;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestoreRepo {

    public interface ListCallback<T> { void onResult(List<T> items); }
    public interface ItemCallback<T> { void onResult(T item); }
    public interface DoneCallback { void onDone(boolean success); }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void loadCountries(ListCallback<Country> cb) {
        db.collection("countries").orderBy("order")
            .get().addOnSuccessListener(snap -> {
                List<Country> out = new ArrayList<>();
                for (DocumentSnapshot d : snap.getDocuments()) {
                    out.add(new Country(
                            d.getId(),
                            d.getString("name"),
                            d.getString("flagUrl"),
                            d.getLong("order") == null ? 0 : d.getLong("order").intValue()));
                }
                cb.onResult(out);
            })
            .addOnFailureListener(e -> cb.onResult(new ArrayList<>()));
    }

    public void loadMedicines(String countryCode, ListCallback<Medicine> cb) {
        db.collection("medicines").whereEqualTo("countryCode", countryCode)
            .get().addOnSuccessListener(snap -> {
                List<Medicine> out = new ArrayList<>();
                for (DocumentSnapshot d : snap.getDocuments()) out.add(toMedicine(d));
                cb.onResult(out);
            })
            .addOnFailureListener(e -> cb.onResult(new ArrayList<>()));
    }

    public void loadMedicine(String medId, ItemCallback<Medicine> cb) {
        db.collection("medicines").document(medId)
            .get().addOnSuccessListener(d ->
                cb.onResult(d.exists() ? toMedicine(d) : null))
            .addOnFailureListener(e -> cb.onResult(null));
    }

    public void addToTrip(String uid, Medicine m, DoneCallback cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", m.name);
        data.put("countryName", m.countryName);
        data.put("status", m.status.name());
        data.put("addedAt", System.currentTimeMillis());
        db.collection("users").document(uid).collection("tripList").document(m.id)
            .set(data)
            .addOnSuccessListener(x -> cb.onDone(true))
            .addOnFailureListener(e -> cb.onDone(false));
    }

    public void loadTrip(String uid, ListCallback<TripItem> cb) {
        db.collection("users").document(uid).collection("tripList")
            .orderBy("addedAt", Query.Direction.DESCENDING)
            .get().addOnSuccessListener(snap -> {
                List<TripItem> out = new ArrayList<>();
                for (DocumentSnapshot d : snap.getDocuments()) {
                    out.add(new TripItem(
                            d.getId(),
                            d.getString("name"),
                            d.getString("countryName"),
                            Status.fromString(d.getString("status")),
                            d.getLong("addedAt") == null ? 0 : d.getLong("addedAt")));
                }
                cb.onResult(out);
            })
            .addOnFailureListener(e -> cb.onResult(new ArrayList<>()));
    }

    public void removeFromTrip(String uid, String medId, DoneCallback cb) {
        db.collection("users").document(uid).collection("tripList").document(medId)
            .delete()
            .addOnSuccessListener(x -> cb.onDone(true))
            .addOnFailureListener(e -> cb.onDone(false));
    }

    private Medicine toMedicine(DocumentSnapshot d) {
        return new Medicine(
                d.getId(),
                d.getString("name"),
                d.getString("countryCode"),
                d.getString("countryName"),
                Status.fromString(d.getString("status")),
                d.getString("activeSubstance"),
                d.getString("group"),
                d.getString("prescription"),
                d.getString("brand"),
                d.getString("description"),
                d.getString("lawExcerpt"),
                d.getString("penalty"),
                d.getString("sourceUrl"),
                d.getString("imageUrl"));
    }
}
