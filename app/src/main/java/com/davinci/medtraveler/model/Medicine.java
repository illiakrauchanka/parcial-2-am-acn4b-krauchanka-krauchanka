package com.davinci.medtraveler.model;

public class Medicine {
    public final String id;
    /** Localized name in the UI/catalog language. Null-safe accessor helpers should be
     *  used by callers; fall back to {@link #nameLatin} when this is blank. */
    public final String name;
    /** INN (Latin), language-independent — always present, used as a fallback when the
     *  localized {@link #name} is unavailable and shown alongside the localized name in
     *  the UI so the user can identify the substance across languages. */
    public final String nameLatin;
    public final String countryCode;
    public final String countryName;
    public final Status status;
    public final String activeSubstance;
    public final String group;
    public final String prescription;
    public final String brand;
    public final String description;
    public final String lawExcerpt;
    public final String penalty;
    public final String sourceUrl;
    public final String imageUrl;

    public Medicine(String id, String name, String nameLatin, String countryCode, String countryName,
                    Status status, String activeSubstance, String group, String prescription,
                    String brand, String description, String lawExcerpt, String penalty,
                    String sourceUrl, String imageUrl) {
        this.id = id;
        this.name = name;
        this.nameLatin = nameLatin;
        this.countryCode = countryCode;
        this.countryName = countryName;
        this.status = status;
        this.activeSubstance = activeSubstance;
        this.group = group;
        this.prescription = prescription;
        this.brand = brand;
        this.description = description;
        this.lawExcerpt = lawExcerpt;
        this.penalty = penalty;
        this.sourceUrl = sourceUrl;
        this.imageUrl = imageUrl;
    }

    /** Best-effort display name: the localized name, or — if it's missing/blank — the
     *  Latin INN, or — as a last resort — the id. Callers that show BOTH (localized +
     *  Latin) should read {@link #name} and {@link #nameLatin} directly. */
    public String displayName() {
        if (name != null && !name.trim().isEmpty()) return name;
        if (nameLatin != null && !nameLatin.trim().isEmpty()) return nameLatin;
        return id;
    }
}
