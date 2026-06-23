package fpt.swp391.GlucoTrackAlert.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Service gọi sang con AI gợi ý thuốc (medication_service.py - port 5001).
 * TÁCH RIÊNG hoàn toàn với MlAnalysisService (port 5000 - phân tích nhật ký).
 *
 * Luồng: Java gửi (age, gender, bmi, bloodSugar, systolic, diastolic)
 *        -> Flask trả về case + danh sách thuốc + chống chỉ định + cảnh báo
 *        -> Java KHÔNG tự tạo Prescription ngay, chỉ trả gợi ý cho bác sĩ xem
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationMlService {

    private final RestTemplate restTemplate;
    private static final String MEDICATION_SERVICE_URL = "http://localhost:5001/suggest-medication";

    @SuppressWarnings("unchecked")
    public Map<String, Object> suggestMedication(int age, String gender, double bmi,
                                                   double bloodSugar, Integer systolic, Integer diastolic) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("age", age);
        payload.put("gender", gender);
        payload.put("bmi", bmi);
        payload.put("bloodSugar", bloodSugar);
        payload.put("systolic", systolic != null ? systolic : 120);
        payload.put("diastolic", diastolic != null ? diastolic : 80);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    MEDICATION_SERVICE_URL, request, Map.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Lỗi gọi medication_service (port 5001): {}", e.getMessage());
            throw new RuntimeException("Không thể kết nối AI gợi ý thuốc. Kiểm tra medication_service.py đã chạy chưa (port 5001).", e);
        }
    }
}
