package com.davinci.medtraveler.model;

public class TripItem {
    public final String medId;
    public final String name;
    public final String countryName;
    public final Status status;
    public final long addedAt;

    public TripItem(String medId, String name, String countryName, Status status, long addedAt) {
        this.medId = medId;
        this.name = name;
        this.countryName = countryName;
        this.status = status;
        this.addedAt = addedAt;
    }
}
