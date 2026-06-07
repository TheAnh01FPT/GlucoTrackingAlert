package fpt.swp391.GlucoTrackAlert.util;

import java.math.BigDecimal;

public class BloodSugarThreshold {

    public static String evaluate(BigDecimal bloodSugar, String patientType) {
        if (bloodSugar == null) return "unknown";
        double value = bloodSugar.doubleValue();
        return switch (patientType != null ? patientType : "adult") {
            case "elderly" -> value < 6.0 ? "normal" : value <= 8.0 ? "warning" : "danger";
            case "pregnant" -> value < 5.1 ? "normal" : value <= 6.7 ? "warning" : "danger";
            case "child" -> value < 5.6 ? "normal" : value <= 7.8 ? "warning" : "danger";
            default -> value < 5.6 ? "normal" : value <= 7.0 ? "warning" : "danger";
        };
    }

}
