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
        // bloodSugar/bmi/age/gender quyết định trực tiếp việc phân loại case (resolve_case)
        // trong medication_service.py -> KHÔNG được bịa default (10.0/22.0/40/MALE cũ),
        // vì sẽ ra sai nhóm phác đồ thuốc. Thiếu thì báo lỗi để bác sĩ bổ sung trước.
        Double bloodSugar = parseDoubleStrict(patientData.get("bloodSugar"));
        Double bmi        = parseDoubleStrict(patientData.get("patientBmi"));
        Integer age       = parseIntStrict(patientData.get("patientAge"));
        Object genderRaw  = patientData.get("patientGender");

        java.util.List<String> missing = new java.util.ArrayList<>();
        if (bloodSugar == null) missing.add("bloodSugar");
        if (bmi == null) missing.add("patientBmi");
        if (age == null) missing.add("patientAge");
        if (genderRaw == null || genderRaw.toString().isBlank()) missing.add("patientGender");

        if (!missing.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Thiếu dữ liệu bắt buộc để gợi ý thuốc: " + String.join(", ", missing)
            ));
        }

        String gender = genderRaw.toString().toUpperCase();
        Integer systolic  = parseIntStrict(patientData.get("systolic"));
        Integer diastolic = parseIntStrict(patientData.get("diastolic"));

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

    private Double parseDoubleStrict(Object value) {
        if (value == null) return null;
        try { return Double.parseDouble(value.toString()); } catch (Exception e) { return null; }
    }
    private Integer parseIntStrict(Object value) {
        if (value == null) return null;
        try { return Integer.parseInt(value.toString()); } catch (Exception e) { return null; }
    }
}