package com.davinci.medtraveler.model;

public enum Status {
    ALLOWED,
    RESTRICTED,
    PENAL;

    public static Status fromString(String raw) {
        if (raw == null) return RESTRICTED;
        switch (raw.trim().toUpperCase()) {
            case "ALLOWED": return ALLOWED;
            case "PENAL": return PENAL;
            case "RESTRICTED": return RESTRICTED;
            default: return RESTRICTED;
        }
    }
}
