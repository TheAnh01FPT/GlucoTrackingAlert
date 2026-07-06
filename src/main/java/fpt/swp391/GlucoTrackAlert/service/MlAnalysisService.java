package fpt.swp391.GlucoTrackAlert.service;

import fpt.swp391.GlucoTrackAlert.dto.healthlog.DailyHealthLogResponse;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MlAnalysisService {

    private final RestTemplate restTemplate;
    private static final String ML_SERVICE_URL = "http://localhost:5000/predict";

    public String analyzePatient(Patient patient, List<DailyHealthLogResponse> logs) {
        // Lấy log gần nhất để predict
        DailyHealthLogResponse latest = logs.get(0);

        // Tính trung bình đường huyết 14 ngày
        OptionalDouble avgBloodSugar = logs.stream()
                .filter(l -> l.getBloodSugar() != null)
                .mapToDouble(l -> l.getBloodSugar().doubleValue())
                .average();

        double bloodSugar = avgBloodSugar.orElse(
                latest.getBloodSugar() != null ? latest.getBloodSugar().doubleValue() : 5.5
        );

        // Build payload gửi sang Flask - dùng ObjectMapper để serialize chuẩn JSON
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bloodSugar", bloodSugar);
        payload.put("systolic", latest.getSystolic() != null ? latest.getSystolic().intValue() : 120);
        payload.put("diastolic", latest.getDiastolic() != null ? latest.getDiastolic().intValue() : 80);
        payload.put("bmi", patient.getBmi() != null ? patient.getBmi().doubleValue() : 22.0);
        payload.put("age", patient.getAge() != null ? patient.getAge().intValue() : 40);
        payload.put("gender", patient.getGender() != null ? patient.getGender().toString() : "MALE");
        payload.put("isPregnant", patient.getIsPregnant() != null && patient.getIsPregnant());
        payload.put("physActivity", true);
        payload.put("smoker", false);
        payload.put("genHealth", 3);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    ML_SERVICE_URL,
                    new HttpEntity<>(payload, headers),
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return buildAnalysisText(patient, logs, bloodSugar, response.getBody());
            }

        } catch (Exception e) {
            log.error("Không thể kết nối ML service: {}", e.getMessage());
            return "❌ Không thể kết nối ML Service. Vui lòng đảm bảo service đang chạy tại localhost:5000";
        }

        return "❌ Không nhận được phản hồi từ ML Service.";
    }

    @SuppressWarnings("unchecked")
    private String buildAnalysisText(Patient patient,
                                     List<DailyHealthLogResponse> logs,
                                     double avgBloodSugar,
                                     Map<String, Object> mlResult) {

        String riskLabel  = (String) mlResult.get("riskLabel");
        String riskColor  = (String) mlResult.get("riskColor");
        Boolean ruleApplied = (Boolean) mlResult.getOrDefault("ruleApplied", false);
        List<String> advice = (List<String>) mlResult.getOrDefault("advice", List.of());

        // Emoji theo mức nguy cơ
        String riskEmoji = switch (riskColor != null ? riskColor : "GREEN") {
            case "RED"    -> "🔴";
            case "YELLOW" -> "🟡";
            default       -> "🟢";
        };

        // Lấy log gần nhất
        DailyHealthLogResponse latest = logs.get(0);

        // Tính avg huyết áp
        OptionalDouble avgSystolic = logs.stream()
                .filter(l -> l.getSystolic() != null)
                .mapToDouble(DailyHealthLogResponse::getSystolic)
                .average();

        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("═══════════════════════════════════════\n");
        sb.append("📋 PHÂN TÍCH SỨC KHỎE AI\n");
        sb.append("═══════════════════════════════════════\n\n");

        // Thông tin bệnh nhân
        sb.append("👤 Bệnh nhân: ").append(patient.getFullName()).append("\n");
        sb.append("   Tuổi: ").append(patient.getAge()).append(" | ");
        sb.append("Giới tính: ").append(patient.getGender()).append(" | ");
        sb.append("BMI: ").append(patient.getBmi()).append("\n\n");

        // Kết quả AI
        sb.append("🤖 KẾT QUẢ PHÂN TÍCH (").append(logs.size()).append(" ngày gần nhất)\n");
        sb.append("─────────────────────────────────────\n");
        sb.append(riskEmoji).append(" Mức nguy cơ: ").append(riskLabel).append("\n");
        sb.append("   Phương pháp: ").append(ruleApplied ? "Rule ADA Guideline" : "Ensemble AI (CDC + Pima)").append("\n\n");

        // Chỉ số trung bình
        sb.append("📊 CHỈ SỐ TRUNG BÌNH\n");
        sb.append("─────────────────────────────────────\n");
        sb.append(String.format("   Đường huyết TB: %.1f mmol/L\n", avgBloodSugar));
        if (avgSystolic.isPresent()) {
            sb.append(String.format("   Huyết áp TB:    %.0f mmHg\n", avgSystolic.getAsDouble()));
        }
        if (latest.getSleepHours() != null) {
            sb.append(String.format("   Giấc ngủ:       %.1f giờ/ngày\n", latest.getSleepHours().doubleValue()));
        }
        if (latest.getWaterMl() != null) {
            sb.append(String.format("   Nước uống:      %d ml/ngày\n", latest.getWaterMl()));
        }
        sb.append("\n");

        // Khuyến nghị
        sb.append("⚕️ KHUYẾN NGHỊ CHO BÁC SĨ\n");
        sb.append("─────────────────────────────────────\n");
        for (String a : advice) {
            sb.append("• ").append(a).append("\n");
        }
        sb.append("\n");

        // Disclaimer
        sb.append("─────────────────────────────────────\n");
        sb.append("⚠️ Kết quả AI chỉ mang tính hỗ trợ tham khảo.\n");
        sb.append("   Quyết định lâm sàng thuộc về bác sĩ điều trị.\n");
        sb.append("═══════════════════════════════════════");

        return sb.toString();
    }
}