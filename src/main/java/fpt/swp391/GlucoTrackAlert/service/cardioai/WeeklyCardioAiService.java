package fpt.swp391.GlucoTrackAlert.service.cardioai;

import fpt.swp391.GlucoTrackAlert.model.DailyHealthLog;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.repository.DailyHealthLogRepository;
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
        OptionalDouble avgSystolic = logs.stream()
                .filter(l -> l.getSystolic() != null)
                .mapToDouble(DailyHealthLog::getSystolic)
                .average();

        OptionalDouble avgDiastolic = logs.stream()
                .filter(l -> l.getDiastolic() != null)
                .mapToDouble(DailyHealthLog::getDiastolic)
                .average();

        OptionalDouble avgBloodSugar = logs.stream()
                .filter(l -> l.getBloodSugar() != null)
                .mapToDouble(l -> l.getBloodSugar().doubleValue())
                .average();

        // Giữ nguyên mmol/L vì trong code Python của bạn không thấy có bước chia đổi đơn vị,
        // hoặc nếu model huấn luyện bằng mmol/L thì giữ nguyên.
        // Nếu cần mg/dL thì bạn dùng biến avgBloodSugar.getAsDouble() * 18.0
        double finalGluc = avgBloodSugar.isPresent() ? avgBloodSugar.getAsDouble() : 5.5;

        // 3. Chuẩn bị Payload gửi đi (BẮT BUỘC KHỚP TÊN BIẾN VỚI PYTHON)
        Map<String, Object> payload = new HashMap<>();

        // Lấy giá trị Huyết áp trung bình cộng, ép kiểu double để khớp hoàn toàn với API cũ
        double finalSystolic = avgSystolic.isPresent() ? avgSystolic.getAsDouble() : 120.0;
        double finalDiastolic = avgDiastolic.isPresent() ? avgDiastolic.getAsDouble() : 80.0;

        // [QUAN TRỌNG] Quy đổi ngầm đường huyết sang mg/dL phục vụ riêng cho Dataset của AI
        double finalGlucMgDl = avgBloodSugar.isPresent() ? (avgBloodSugar.getAsDouble() * 18.0) : 100.0;

        // ĐỔI LẠI TÊN KEY: Khớp 100% với cấu hình Flask AI đang đòi hỏi
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

        // Thói quen sinh hoạt
        payload.put("smoke", patient.getSmoke() != null ? patient.getSmoke() : 0);
        payload.put("alco", patient.getAlco() != null ? patient.getAlco() : 0);
        payload.put("active", patient.getActive() != null ? patient.getActive() : 1);

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
                log.info("Dự đoán tim mạch thành công cho bệnh nhân {}: {}", patient.getId(), response.getBody());
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Lỗi khi kết nối tới ML Service dự đoán tim mạch tuần: {}", e.getMessage());
        }

        return Collections.emptyMap();
    }
}