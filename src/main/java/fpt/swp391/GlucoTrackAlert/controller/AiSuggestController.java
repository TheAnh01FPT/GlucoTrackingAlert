package fpt.swp391.GlucoTrackAlert.controller;

import fpt.swp391.GlucoTrackAlert.service.MedicationMlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Gọi sang medication_service.py (port 5001) để lấy gợi ý đơn thuốc.
 * Frontend gọi POST /api/ai/suggest với thông tin bệnh nhân,
 * controller forward sang Flask service (rule-based 9 nhóm phác đồ),
 * trả JSON gợi ý thuốc về cho bác sĩ xem & duyệt.
 *
 * KHÔNG còn gọi Claude API. KHÔNG tự tạo Prescription - chỉ gợi ý.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiSuggestController {

    private final MedicationMlService medicationMlService;

    @PostMapping("/suggest")
    public ResponseEntity<?> suggest(@RequestBody Map<String, Object> patientData) {
        double bloodSugar = parseDouble(patientData.get("bloodSugar"), 10.0);
        double bmi        = parseDouble(patientData.get("patientBmi"), 22.0);
        int    age        = parseInt(patientData.get("patientAge"), 40);
        String gender     = String.valueOf(patientData.getOrDefault("patientGender", "MALE")).toUpperCase();
        Integer systolic  = patientData.get("systolic") != null ? parseInt(patientData.get("systolic"), 120) : null;
        Integer diastolic = patientData.get("diastolic") != null ? parseInt(patientData.get("diastolic"), 80) : null;

        try {
            Map<String, Object> flaskResult = medicationMlService.suggestMedication(
                    age, gender, bmi, bloodSugar, systolic, diastolic
            );

            // Map Flask response sang format frontend đang dùng:
            // Flask trả "medicines" → frontend đọc "items"
            // Flask trả "note" → frontend đọc "note"
            // Flask trả "warning" + "needsManualReview" → truyền thẳng qua
            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("case",              flaskResult.get("case"));
            response.put("confidence",        flaskResult.get("confidence"));
            response.put("items",             flaskResult.get("medicines")); // map medicines → items
            response.put("note",              flaskResult.get("note"));
            response.put("warning",           flaskResult.get("warning"));
            response.put("needsManualReview", flaskResult.get("needsManualReview"));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Không thể lấy gợi ý thuốc: " + e.getMessage()));
        }
    }

    private double parseDouble(Object value, double def) {
        if (value == null) return def;
        try { return Double.parseDouble(value.toString()); } catch (Exception e) { return def; }
    }
    private int parseInt(Object value, int def) {
        if (value == null) return def;
        try { return Integer.parseInt(value.toString()); } catch (Exception e) { return def; }
    }
}