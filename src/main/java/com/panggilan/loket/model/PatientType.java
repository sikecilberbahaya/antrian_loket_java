package com.panggilan.loket.model;

public enum PatientType {
    LAMA("L", "Pasien Lama"),
    BARU("B", "Pasien Baru");

    private final String prefix;
    private final String displayName;

    PatientType(String prefix, String displayName) {
        this.prefix = prefix;
        this.displayName = displayName;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PatientType fromString(String value) {
        if (value == null || value.isBlank()) {
            return LAMA;
        }
        String upper = value.toUpperCase().trim();
        for (PatientType type : values()) {
            if (type.name().equals(upper) || type.prefix.equals(upper)) {
                return type;
            }
        }
        return LAMA;
    }
}
