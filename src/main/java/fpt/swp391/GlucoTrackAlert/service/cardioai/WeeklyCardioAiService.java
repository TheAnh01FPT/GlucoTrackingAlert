package fpt.swp391.GlucoTrackAlert.service.cardioai;

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
public class WeeklyCardioAiService {

    private final RestTemplate restTemplate;
    private final DailyHealthLogRepository dailyHealthLogRepository;

    private static final String ML_HEART_URL = "http://localhost:5000/predict-cardio";

    /**
     * Hàm tính toán trung bình cộng chỉ số trong tuần của bệnh nhân và gọi AI dự đoán bệnh tim
     */
    public Map<String, Object> calculateWeeklyHeartRisk(Patient patient, LocalDate weekStart, LocalDate weekEnd) {
        // 1. Lấy danh sách nhật ký sức khỏe (daily logs) trong tuần của bệnh nhân
        List<DailyHealthLog> logs = dailyHealthLogRepository.findByPatientIdAndLogDateBetween(
                patient.getId(), weekStart, weekEnd
        );

        if (logs.isEmpty()) {
            log.warn("Bệnh nhân {} không có dữ liệu daily log trong tuần từ {} đến {}", patient.getId(), weekStart, weekEnd);
            return Collections.emptyMap();
        }

        // 2. Tính toán trung bình cộng (Huyết áp tâm thu, huyết áp tâm trương, đường huyết)
        // Đã lọc thêm điều kiện > 0 để loại bỏ dữ liệu trống/lỗi tránh làm tụt chỉ số TB gửi sang AI
        OptionalDouble avgSystolic = logs.stream()
                .filter(l -> l.getSystolic() != null && l.getSystolic() > 0)
                .mapToDouble(DailyHealthLog::getSystolic)
                .average();

        OptionalDouble avgDiastolic = logs.stream()
                .filter(l -> l.getDiastolic() != null && l.getDiastolic() > 0)
                .mapToDouble(DailyHealthLog::getDiastolic)
                .average();

        OptionalDouble avgBloodSugar = logs.stream()
                .filter(l -> l.getBloodSugar() != null && l.getBloodSugar().doubleValue() > 0)
                .mapToDouble(l -> l.getBloodSugar().doubleValue())
                .average();

        // 3. Chuẩn bị Payload gửi đi (BẮT BUỘC KHỚP TÊN BIẾN VỚI PYTHON)
        Map<String, Object> payload = new HashMap<>();

        double finalSystolic = avgSystolic.isPresent() ? avgSystolic.getAsDouble() : 120.0;
        double finalDiastolic = avgDiastolic.isPresent() ? avgDiastolic.getAsDouble() : 80.0;

        // Quy đổi ngầm đường huyết từ mmol/L sang mg/dL phục vụ riêng cho Dataset của AI
        double finalGlucMgDl = avgBloodSugar.isPresent() ? (avgBloodSugar.getAsDouble() * 18.0) : 100.0;

        payload.put("systolic", finalSystolic);
        payload.put("diastolic", finalDiastolic);
        payload.put("blood_sugar", finalGlucMgDl);

        // Chỉ số cố định lấy từ Profile bệnh nhân
        int ageDays = (patient.getAge() != null) ? patient.getAge() * 365 : 18250;
        payload.put("age_days", ageDays);

        // Giới tính: Khớp logic Python (Nam -> 2, Nữ -> 1)
        int genderCode = "FEMALE".equalsIgnoreCase(patient.getGender()) || "Nữ".equalsIgnoreCase(patient.getGender()) ? 1 : 2;
        payload.put("gender", genderCode);

        payload.put("height", patient.getHeightCm() != null ? patient.getHeightCm().doubleValue() : 170.0);
        payload.put("weight", patient.getWeightKg() != null ? patient.getWeightKg().doubleValue() : 70.0);
        payload.put("cholesterol", patient.getCholesterol() != null ? patient.getCholesterol() : 1);

        // Thói quen sinh hoạt tĩnh
        payload.put("smoke", patient.getSmoke() != null ? patient.getSmoke() : 0);
        payload.put("alco", patient.getAlco() != null ? patient.getAlco() : 0);

        // --- ĐOẠN SỬA ĐỔI: TÍNH TOÁN VẬN ĐỘNG THEO CHỈ SỐ LOG TUẦN THỰC TẾ ---
        // Đếm số ngày bệnh nhân tích chọn vận động (physical_activity == 1) trong tuần
        long activeDaysCount = logs.stream()
                .filter(l -> l.getPhysicalActivity() != null && l.getPhysicalActivity() == 1)
                .count();

        // Tính số ngày không vận động trong tuần
        long totalDaysCount = logs.size();
        long inactiveDaysCount = totalDaysCount - activeDaysCount;

        // Logic nhóm trưởng: nếu số ngày vận động lớn hơn số ngày không vận động thì
        // tuần đó được xem là "vận động" (active = 1), ngược lại là "không vận động" (active = 0).
        int calculatedActive = (activeDaysCount > inactiveDaysCount) ? 1 : 0;
        payload.put("active", calculatedActive);
        // --- KẾT THÚC ĐOẠN SỬA ĐỔI ---

        // 4. Gửi Request sang Python AI Service
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    ML_HEART_URL,
                    new HttpEntity<>(payload, headers),
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                normalizeAiResponseKeys(body);
                log.info("Dự đoán tim mạch thành công cho bệnh nhân {}: {}", patient.getId(), body);
                return body;
            }
        } catch (Exception e) {
            log.error("Lỗi khi kết nối tới ML Service dự đoán tim mạch tuần: {}", e.getMessage());
        }

        return Collections.emptyMap();
    }

    private void normalizeAiResponseKeys(Map<String, Object> body) {
        if (body == null) {
            return;
        }
        if (body.containsKey("riskPercentage") && !body.containsKey("risk_percentage")) {
            body.put("risk_percentage", body.get("riskPercentage"));
        }
        if (body.containsKey("riskLevel") && !body.containsKey("risk_level")) {
            body.put("risk_level", body.get("riskLevel"));
        }
        if (body.containsKey("risk_percentage") && !body.containsKey("riskPercentage")) {
            body.put("riskPercentage", body.get("risk_percentage"));
        }
        if (body.containsKey("risk_level") && !body.containsKey("riskLevel")) {
            body.put("riskLevel", body.get("risk_level"));
        }
    }
}