package fpt.swp391.GlucoTrackAlert.service;

import fpt.swp391.GlucoTrackAlert.dto.healthlog.DailyHealthLogResponse;
import fpt.swp391.GlucoTrackAlert.model.HealthThreshold;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.repository.HealthThresholdRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String GEMINI_URL
            = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=";

    private final RestTemplate restTemplate;
    private final HealthThresholdRepository healthThresholdRepository;

    private String promptTemplate;

    @PostConstruct
    public void loadPromptTemplate() throws IOException {
        ClassPathResource resource = new ClassPathResource("gemini-prompt.txt");
        promptTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    public String analyzePatient(Patient patient, List<DailyHealthLogResponse> logs) {
        String prompt = buildPrompt(patient, logs);

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    GEMINI_URL + apiKey, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                var candidates = (List<?>) response.getBody().get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    var content = (Map<?, ?>) ((Map<?, ?>) candidates.get(0)).get("content");
                    var parts = (List<?>) content.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        return (String) ((Map<?, ?>) parts.get(0)).get("text");
                    }
                }
            }
        } catch (Exception e) {
            return "❌ Không thể kết nối Gemini API: " + e.getMessage();
        }

        return "❌ Không nhận được phản hồi từ AI.";
    }

    private String buildPrompt(Patient patient, List<DailyHealthLogResponse> logs) {
        String patientType = patient.getPatientType() != null ? patient.getPatientType() : "adult";

        // Build logs string
        StringBuilder logsSb = new StringBuilder();
        for (DailyHealthLogResponse log : logs) {
            logsSb.append("Ngày ").append(log.getLogDate()).append(": ");
            if (log.getBloodSugar() != null) {
                logsSb.append("ĐH=").append(log.getBloodSugar()).append("mmol/L ");
            }
            if (log.getSystolic() != null) {
                logsSb.append("HA=").append(log.getSystolic()).append("/").append(log.getDiastolic()).append("mmHg ");
            }
            if (log.getSleepHours() != null) {
                logsSb.append("Ngủ=").append(log.getSleepHours()).append("h ");
            }
            if (log.getWaterMl() != null) {
                logsSb.append("Nước=").append(log.getWaterMl()).append("ml ");
            }
            if (log.getSugarConsumptionLevel() != null) {
                logsSb.append("Đường=").append(log.getSugarConsumptionLevel()).append(" ");
            }
            if (log.getSymptoms() != null && !log.getSymptoms().isEmpty()) {
                logsSb.append("TC=").append(log.getSymptoms());
            }
            logsSb.append("\n");
        }

        // Build thresholds string từ DB
        String thresholds = buildThresholdsFromDb(patient.getId(), patientType);

        return promptTemplate
                .replace("{fullName}", patient.getFullName())
                .replace("{age}", patient.getAge() != null ? patient.getAge().toString() : "Không rõ")
                .replace("{gender}", patient.getGender() != null ? patient.getGender() : "Không rõ")
                .replace("{patientType}", patientType)
                .replace("{bmi}", patient.getBmi() != null ? patient.getBmi().toString() : "Không rõ")
                .replace("{logCount}", String.valueOf(logs.size()))
                .replace("{logs}", logsSb.toString())
                .replace("{thresholds}", thresholds);
    }

    private String buildThresholdsFromDb(Long patientId, String patientType) {
        StringBuilder sb = new StringBuilder();

        // Ưu tiên ngưỡng riêng của bệnh nhân trước
        List<HealthThreshold> thresholds = healthThresholdRepository.findAllByPatientId(patientId);

        // Nếu không có ngưỡng riêng thì lấy ngưỡng mặc định theo patientType
        if (thresholds.isEmpty()) {
            thresholds = healthThresholdRepository.findAllByPatientType(patientType);
        }

        // Nếu vẫn không có thì lấy tất cả ngưỡng mặc định (patient = null)
        if (thresholds.isEmpty()) {
            thresholds = healthThresholdRepository.findAllByPatientIsNull();
        }

        if (thresholds.isEmpty()) {
            return "Không có dữ liệu ngưỡng trong hệ thống.";
        }

        for (HealthThreshold t : thresholds) {
            sb.append("- ").append(t.getMetricType()).append(": ")
                    .append("Bình thường [").append(t.getNormalMin()).append(" - ").append(t.getNormalMax()).append("], ")
                    .append("Cảnh báo [").append(t.getWarningMin()).append(" - ").append(t.getWarningMax()).append("]");
            if (t.getDescription() != null && !t.getDescription().isEmpty()) {
                sb.append(" (").append(t.getDescription()).append(")");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
