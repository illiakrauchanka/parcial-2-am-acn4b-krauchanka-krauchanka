package com.davinci.medtraveler.model;

public class Country {
    public final String code;
    public final String name;
    public final String flagUrl;
    public final int order;

    public Country(String code, String name, String flagUrl, int order) {
        this.code = code;
        this.name = name;
        this.flagUrl = flagUrl;
        this.order = order;
    }
}
