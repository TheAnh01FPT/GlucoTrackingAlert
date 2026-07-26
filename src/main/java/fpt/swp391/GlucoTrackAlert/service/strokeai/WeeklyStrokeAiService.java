package fpt.swp391.GlucoTrackAlert.service.strokeai;

import fpt.swp391.GlucoTrackAlert.model.healthlog.DailyHealthLog;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.repository.healthlog.DailyHealthLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyStrokeAiService {

    private final RestTemplate restTemplate;
    private final DailyHealthLogRepository dailyHealthLogRepository;

    private static final String ML_STROKE_URL = "http://127.0.0.1:8000/predict";

    /**
     * Hàm tính toán trung bình cộng chỉ số trong tuần của bệnh nhân và gọi AI dự đoán nguy cơ đột quỵ
     */
    public Map<String, Object> calculateWeeklyStrokeRisk(Patient patient, LocalDate weekStart, LocalDate weekEnd) {
        // 1. Lấy danh sách nhật ký sức khỏe (daily logs) trong tuần của bệnh nhân
        List<DailyHealthLog> logs = dailyHealthLogRepository.findByPatientIdAndLogDateBetween(
                patient.getId(), weekStart, weekEnd
        );

        if (logs.isEmpty()) {
            log.warn("Bệnh nhân {} không có dữ liệu daily log trong tuần từ {} đến {}", patient.getId(), weekStart, weekEnd);
            return Collections.emptyMap();
        }

        // 2. Tính toán trung bình cộng đường huyết (glucose)
        OptionalDouble avgBloodSugar = logs.stream()
                .filter(l -> l.getBloodSugar() != null && l.getBloodSugar().doubleValue() > 0)
                .mapToDouble(l -> l.getBloodSugar().doubleValue())
                .average();

        double finalGlucMgDl = avgBloodSugar.isPresent() ? (avgBloodSugar.getAsDouble() * 18.0) : 120.0;

        // 3. Chuẩn bị Payload gửi đi khớp cấu trúc của API Python
        Map<String, Object> payload = new HashMap<>();

        // Giới tính: Nam -> 0, Nữ -> 1, Khác -> -1
        int genderVal = 0;
        if (patient.getGender() != null) {
            String g = patient.getGender().toLowerCase();
            if (g.contains("fem") || g.contains("nữ")) {
                genderVal = 1;
            } else if (g.contains("oth") || g.contains("khác")) {
                genderVal = -1;
            }
        }

        double ageVal = patient.getAge() != null ? patient.getAge().doubleValue() : 50.0;
        int hyperVal = Boolean.TRUE.equals(patient.getHypertension()) ? 1 : 0;
        int heartVal = Boolean.TRUE.equals(patient.getHeartDisease()) ? 1 : 0;

        int workVal = 0; // Default Private
        if (patient.getWorkType() != null) {
            String w = patient.getWorkType();
            if (w.equalsIgnoreCase("Self-employed")) {
                workVal = 1;
            } else if (w.equalsIgnoreCase("Govt_job")) {
                workVal = 2;
            } else if (w.equalsIgnoreCase("children")) {
                workVal = -1;
            } else if (w.equalsIgnoreCase("Never_worked")) {
                workVal = -2;
            }
        }

        int resVal = 1; // Default Urban
        if (patient.getResidenceType() != null && patient.getResidenceType().equalsIgnoreCase("Rural")) {
            resVal = 0;
        }

        double bmiVal = patient.getBmi() != null ? patient.getBmi().doubleValue() : 25.0;

        int smokeVal = -1; // Default Unknown
        if (patient.getSmokingStatus() != null) {
            String s = patient.getSmokingStatus();
            if (s.equalsIgnoreCase("never smoked")) {
                smokeVal = 0;
            } else if (s.equalsIgnoreCase("formerly smoked")) {
                smokeVal = 1;
            } else if (s.equalsIgnoreCase("smokes")) {
                smokeVal = 2;
            }
        }

        payload.put("gender", genderVal);
        payload.put("age", ageVal);
        payload.put("hypertension", hyperVal);
        payload.put("heart_disease", heartVal);
        payload.put("work_type", workVal);
        payload.put("Residence_type", resVal);
        payload.put("avg_glucose_level", finalGlucMgDl);
        payload.put("bmi", bmiVal);
        payload.put("smoking_status", smokeVal);

        // 4. Gửi Request sang Python AI Service
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    ML_STROKE_URL,
                    new HttpEntity<>(payload, headers),
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("Dự đoán nguy cơ đột quỵ tuần thành công cho bệnh nhân {}: {}", patient.getId(), response.getBody());
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Lỗi khi kết nối tới ML Service dự đoán đột quỵ tuần: {}", e.getMessage());
        }

        return Collections.emptyMap();
    }
}
