package com.davinci.medtraveler.model;

public class Medicine {
    public final String id;
    public final String name;
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

    public Medicine(String id, String name, String countryCode, String countryName,
                    Status status, String activeSubstance, String group, String prescription,
                    String brand, String description, String lawExcerpt, String penalty,
                    String sourceUrl, String imageUrl) {
        this.id = id;
        this.name = name;
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
}
