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

        // Kiểm tra các field BẮT BUỘC phải có dữ liệu thật, không cho phép AI tự bịa số
        // (trước đây hardcode mặc định 120/80/22.0/40/MALE khi null -> sai lệch kết quả phân tích)
        List<String> missingFields = new ArrayList<>();
        if (patient.getBmi() == null) missingFields.add("BMI");
        if (patient.getAge() == null) missingFields.add("tuổi");
        if (patient.getGender() == null || patient.getGender().isBlank()) missingFields.add("giới tính");
        if (latest.getSystolic() == null) missingFields.add("huyết áp tâm thu (hôm nay)");
        if (latest.getDiastolic() == null) missingFields.add("huyết áp tâm trương (hôm nay)");

        if (!missingFields.isEmpty()) {
            return "⚠️ Không thể phân tích AI vì thiếu dữ liệu: " + String.join(", ", missingFields) + ". "
                    + "Vui lòng cập nhật hồ sơ bệnh nhân hoặc bổ sung nhật ký sức khỏe đầy đủ trước khi phân tích.";
        }

        // Build payload gửi sang Flask
        Map<String, Object> payload = new HashMap<>();
        payload.put("bloodSugar", bloodSugar);
        payload.put("systolic", latest.getSystolic());
        payload.put("diastolic", latest.getDiastolic());
        payload.put("bmi", patient.getBmi().doubleValue());
        payload.put("age", patient.getAge());
        payload.put("gender", patient.getGender());
        payload.put("isPregnant", patient.getIsPregnant() != null && patient.getIsPregnant());
        // smokingStatus: dùng đúng dữ liệu thật của patient (DB đã có cột này),
        // không còn để Python tự gán giá trị trung tính giả định
        payload.put("smokingStatus", patient.getSmokingStatus());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

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

    /**
     * So sánh đường huyết trung bình của nửa kỳ GẦN ĐÂY vs nửa kỳ TRƯỚC ĐÓ
     * (logs đã được sort DESC theo logDate - index 0 là mới nhất).
     * Không cần thêm cột DB mới, chỉ dùng lại dữ liệu logDate + bloodSugar đã có.
     * Cần tối thiểu 4 log để chia 2 nửa có ý nghĩa, tránh trend bị nhiễu vì quá ít data.
     */
    private String buildTrendLine(List<DailyHealthLogResponse> logs) {
        if (logs.size() < 4) {
            return "";
        }

        int mid = logs.size() / 2;
        // logs[0..mid-1] = nửa gần đây (mới hơn), logs[mid..end] = nửa trước đó (cũ hơn)
        OptionalDouble recentAvg = logs.subList(0, mid).stream()
                .filter(l -> l.getBloodSugar() != null)
                .mapToDouble(l -> l.getBloodSugar().doubleValue())
                .average();
        OptionalDouble olderAvg = logs.subList(mid, logs.size()).stream()
                .filter(l -> l.getBloodSugar() != null)
                .mapToDouble(l -> l.getBloodSugar().doubleValue())
                .average();

        if (recentAvg.isEmpty() || olderAvg.isEmpty()) {
            // Một trong hai nửa không có đủ dữ liệu đường huyết -> không tính trend
            // Thông báo rõ để bác sĩ biết lý do, thay vì âm thầm bỏ qua
            return "   Xu hướng:       ⚠️ Không đủ dữ liệu đường huyết để tính xu hướng\n";
        }

        double diff = recentAvg.getAsDouble() - olderAvg.getAsDouble();
        // Ngưỡng 0.3 mmol/L để tránh báo "tăng/giảm" với sai số đo nhỏ không đáng kể
        String arrow;
        String desc;
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