package fpt.swp391.GlucoTrackAlert.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum MetricType {
    BLOOD_SUGAR,
    SYSTOLIC,
    DIASTOLIC;

    @JsonCreator
    public static MetricType from(String value) {
        if (value == null) return null;
        try {
            String norm = value.trim().replace('-', '_').replace(' ', '_');
            norm = norm.replaceAll("([a-z])([A-Z])", "$1_$2");
            return MetricType.valueOf(norm.toUpperCase());
        } catch (Exception ex) {
            return null;
        }
    }
}