package fpt.swp391.GlucoTrackAlert.service;

import fpt.swp391.GlucoTrackAlert.dto.healthlog.DailyHealthLogResponse;
import fpt.swp391.GlucoTrackAlert.model.meallog.Duy_Meal_Logs;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
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
public class MlAnalysisService {

    private final RestTemplate restTemplate;
    private static final String ML_SERVICE_URL = "http://localhost:5000/predict";

    public String analyzePatient(Patient patient,
            List<DailyHealthLogResponse> logs,
            Map<LocalDate, List<Duy_Meal_Logs>> mealsByDate) {
        DailyHealthLogResponse latest = logs.get(0);

        OptionalDouble avgBloodSugar = logs.stream()
                .filter(l -> l.getBloodSugar() != null)
                .mapToDouble(l -> l.getBloodSugar().doubleValue())
                .average();

        double bloodSugar = avgBloodSugar.orElse(
                latest.getBloodSugar() != null ? latest.getBloodSugar().doubleValue() : 5.5
        );

        List<String> missingFields = new ArrayList<>();
        if (patient.getBmi() == null) {
            missingFields.add("BMI");
        }
        if (patient.getAge() == null) {
            missingFields.add("tuổi");
        }
        if (patient.getGender() == null || patient.getGender().isBlank()) {
            missingFields.add("giới tính");
        }
        if (latest.getSystolic() == null) {
            missingFields.add("huyết áp tâm thu (hôm nay)");
        }
        if (latest.getDiastolic() == null) {
            missingFields.add("huyết áp tâm trương (hôm nay)");
        }

        if (!missingFields.isEmpty()) {
            return "⚠️ Không thể phân tích AI vì thiếu dữ liệu: " + String.join(", ", missingFields) + ". "
                    + "Vui lòng cập nhật hồ sơ bệnh nhân hoặc bổ sung nhật ký sức khỏe đầy đủ trước khi phân tích.";
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("bloodSugar", bloodSugar);
        payload.put("systolic", latest.getSystolic());
        payload.put("diastolic", latest.getDiastolic());
        payload.put("bmi", patient.getBmi().doubleValue());
        payload.put("age", patient.getAge());
        payload.put("gender", patient.getGender());
        payload.put("isPregnant", patient.getIsPregnant() != null && patient.getIsPregnant());
        payload.put("smokingStatus", patient.getSmokingStatus());

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
                // ✅ truyền mealsByDate vào đây
                return buildAnalysisText(patient, logs, bloodSugar, response.getBody(), mealsByDate);
            }

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // Flask trả 4xx (ví dụ 400 do thiếu field bắt buộc) — hiện đúng lý do,
            // không gộp chung với lỗi "không kết nối được service".
            log.error("ML service từ chối request {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            String reason = e.getResponseBodyAsString();
            try {
                Map<?, ?> body = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(reason, Map.class);
                if (body.get("error") != null) {
                    reason = String.valueOf(body.get("error"));
                }
            } catch (Exception ignored) {
                // body không phải JSON hợp lệ -> giữ nguyên chuỗi thô
            }
            return "❌ ML Service từ chối yêu cầu: " + reason;
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
            Map<String, Object> mlResult,
            Map<LocalDate, List<Duy_Meal_Logs>> mealsByDate) {

        String riskLabel = (String) mlResult.get("riskLabel");
        String riskColor = (String) mlResult.get("riskColor");
        Boolean ruleApplied = (Boolean) mlResult.getOrDefault("ruleApplied", false);
        List<String> advice = (List<String>) mlResult.getOrDefault("advice", List.of());

        String riskEmoji = switch (riskColor != null ? riskColor : "GREEN") {
            case "RED" ->
                "🔴";
            case "YELLOW" ->
                "🟡";
            default ->
                "🟢";
        };

        DailyHealthLogResponse latest = logs.get(0);

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
        sb.append("   Phương pháp: ").append(ruleApplied ? "Rule ADA Guideline" : "Ensemble AI (V4+V5)").append("\n\n");

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
        sb.append(buildTrendLine(logs));
        sb.append("\n");

        // ✅ Section bữa ăn — chỉ hiện những ngày đường huyết > 7.8
        sb.append("🍽️ BỮA ĂN CÁC NGÀY BẤT THƯỜNG (đường huyết > 7.8)\n");
        sb.append("─────────────────────────────────────\n");
        List<DailyHealthLogResponse> abnormalDays = logs.stream()
                .filter(l -> l.getBloodSugar() != null && l.getBloodSugar().doubleValue() > 7.8)
                .toList();

        if (abnormalDays.isEmpty()) {
            sb.append("   ✅ Không có ngày nào đường huyết vượt ngưỡng.\n");
        } else {
            for (DailyHealthLogResponse l : abnormalDays) {
                sb.append("📅 ").append(l.getLogDate())
                        .append(" — Đường huyết: ").append(l.getBloodSugar()).append(" mmol/L\n");
                List<Duy_Meal_Logs> dayMeals = mealsByDate.getOrDefault(l.getLogDate(), List.of());
                if (dayMeals.isEmpty()) {
                    sb.append("   (Không có dữ liệu bữa ăn ngày này)\n");
                } else {
                    for (Duy_Meal_Logs m : dayMeals) {
                        sb.append("   • ");
                        if (m.getMealType() != null) {
                            sb.append(m.getMealType()).append(": ");
                        }
                        sb.append(m.getFoodName());
                        if (m.getSugarEstimation() != null) {
                            sb.append(" — đường: ").append(m.getSugarEstimation());
                        }
                        sb.append("\n");
                    }
                }
            }
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

        return sb.toString(); // ✅ chỉ có 1 return duy nhất ở cuối
    }

    private String buildTrendLine(List<DailyHealthLogResponse> logs) {
        if (logs.size() < 4) {
            return "";
        }

        int mid = logs.size() / 2;
        OptionalDouble recentAvg = logs.subList(0, mid).stream()
                .filter(l -> l.getBloodSugar() != null)
                .mapToDouble(l -> l.getBloodSugar().doubleValue())
                .average();
        OptionalDouble olderAvg = logs.subList(mid, logs.size()).stream()
                .filter(l -> l.getBloodSugar() != null)
                .mapToDouble(l -> l.getBloodSugar().doubleValue())
                .average();

        if (recentAvg.isEmpty() || olderAvg.isEmpty()) {
            return "   Xu hướng:       ⚠️ Không đủ dữ liệu đường huyết để tính xu hướng\n";
        }

        double diff = recentAvg.getAsDouble() - olderAvg.getAsDouble();
        String arrow, desc;
        if (diff > 0.3) {
            arrow = "📈";
            desc = "tăng";
        } else if (diff < -0.3) {
            arrow = "📉";
            desc = "giảm";
        } else {
            arrow = "➡️";
            desc = "ổn định";
        }

        return String.format("   Xu hướng:       %s Đường huyết %s %.1f mmol/L so với kỳ trước (%.1f → %.1f)\n",
                arrow, desc, Math.abs(diff), olderAvg.getAsDouble(), recentAvg.getAsDouble());
    }
}