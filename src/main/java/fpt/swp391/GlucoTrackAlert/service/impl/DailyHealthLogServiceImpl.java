package fpt.swp391.GlucoTrackAlert.service.impl;

import fpt.swp391.GlucoTrackAlert.dto.healthlog.DailyHealthLogRequest;
import fpt.swp391.GlucoTrackAlert.dto.healthlog.DailyHealthLogResponse;
import fpt.swp391.GlucoTrackAlert.enums.MetricType;
import fpt.swp391.GlucoTrackAlert.model.DailyHealthLog;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.repository.DailyHealthLogRepository;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.service.DailyHealthLogService;
import fpt.swp391.GlucoTrackAlert.service.HealthThresholdService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DailyHealthLogServiceImpl implements DailyHealthLogService {

    private final DailyHealthLogRepository dailyHealthLogRepository;
    private final PatientRepository patientRepository;
    private final HealthThresholdService healthThresholdService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public Page<DailyHealthLogResponse> getLogs(Long patientId, Pageable pageable) {
        Page<DailyHealthLog> page = dailyHealthLogRepository.findByPatientIdOrderByLogDateDesc(patientId, pageable);

        // Resolve thresholds grouped by (patientId, patientType, metricType) to avoid N+1
        Map<String, Optional<fpt.swp391.GlucoTrackAlert.model.HealthThreshold>> resolved = new HashMap<>();

        // Pre-resolve for blood sugar metric
        for (DailyHealthLog log : page.getContent()) {
            Long pId = log.getPatient() != null ? log.getPatient().getId() : null;
            String pType = log.getPatient() != null ? log.getPatient().getPatientType() : null;
            String key = (pId == null ? "null" : pId.toString()) + "|" + (pType == null ? "" : pType) + "|" + MetricType.BLOOD_SUGAR.name();
            if (!resolved.containsKey(key)) {
                resolved.put(key, healthThresholdService.resolveThreshold(pId, pType, MetricType.BLOOD_SUGAR));
            }
        }

        List<DailyHealthLogResponse> mapped = page.getContent().stream().map(log -> {
            Long pId = log.getPatient() != null ? log.getPatient().getId() : null;
            String pType = log.getPatient() != null ? log.getPatient().getPatientType() : null;
            String key = (pId == null ? "null" : pId.toString()) + "|" + (pType == null ? "" : pType) + "|" + MetricType.BLOOD_SUGAR.name();
            Optional<fpt.swp391.GlucoTrackAlert.model.HealthThreshold> opt = resolved.get(key);
            String status = "unknown";
            if (log.getBloodSugar() != null) {
                if (opt.isPresent()) {
                    fpt.swp391.GlucoTrackAlert.model.HealthThreshold t = opt.get();
                    double v = log.getBloodSugar().doubleValue();
                    double normalMin = t.getNormalMin().doubleValue();
                    double normalMax = t.getNormalMax().doubleValue();
                    double warningMin = t.getWarningMin().doubleValue();
                    double warningMax = t.getWarningMax().doubleValue();
                    if (v >= normalMin && v <= normalMax) status = "NORMAL";
                    else if (v < normalMin) status = (v >= warningMin) ? "LOW_WARNING" : "LOW_DANGER";
                    else status = (v <= warningMax) ? "HIGH_WARNING" : "HIGH_DANGER";
                } else {
                    status = "unknown";
                }
            }
            BigDecimal riskPercentageVal = null;
            String riskLevelVal = null;
            try {
                List<Map<String, Object>> list = jdbcTemplate.queryForList(
                    "SELECT risk_percentage, risk_level FROM risk_assessments WHERE daily_health_log_id = ? ORDER BY id DESC LIMIT 1",
                    log.getId()
                );
                if (!list.isEmpty()) {
                    Map<String, Object> map = list.get(0);
                    riskPercentageVal = (BigDecimal) map.get("risk_percentage");
                    riskLevelVal = (String) map.get("risk_level");
                }
            } catch (Exception e) {
                // ignore
            }

            return DailyHealthLogResponse.builder()
                    .id(log.getId())
                    .patientId(log.getPatient() != null ? log.getPatient().getId() : null)
                    .userId(log.getPatient() != null && log.getPatient().getUser() != null ? log.getPatient().getUser().getId() : null)
                    .patientName(log.getPatient() != null ? log.getPatient().getFullName() : null)
                    .logDate(log.getLogDate())
                    .bloodSugar(log.getBloodSugar())
                    .systolic(log.getSystolic())
                    .diastolic(log.getDiastolic())
                    .sleepHours(log.getSleepHours())
                    .waterMl(log.getWaterMl())
                    .sugarConsumptionLevel(log.getSugarConsumptionLevel())
                    .symptoms(log.getSymptoms())
                    .note(log.getNote())
                    .createdAt(log.getCreatedAt())
                    .updatedAt(log.getUpdatedAt())
                    .patientType(log.getPatient() != null ? log.getPatient().getPatientType() : null)
                    .bloodSugarStatus(status)
                    .riskPercentage(riskPercentageVal)
                    .riskLevel(riskLevelVal)
                    .build();
        }).collect(Collectors.toList());

        return new PageImpl<>(mapped, pageable, page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public DailyHealthLogResponse getLogById(Long id) {
        DailyHealthLog log = dailyHealthLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhật ký sức khỏe có mã số ID: " + id));
        return toResponse(log);
    }

    @Override
    @Transactional
    public DailyHealthLogResponse createLog(Long patientId, DailyHealthLogRequest request) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bệnh nhân có mã số ID: " + patientId));
        DailyHealthLog log = toEntity(request);
        log.setPatient(patient);
        DailyHealthLog savedLog = dailyHealthLogRepository.save(log);
        triggerDailyAiPrediction(patient, savedLog);
        return toResponse(savedLog);
    }

    @Override
    @Transactional
    public DailyHealthLogResponse updateLog(Long id, DailyHealthLogRequest request) {
        DailyHealthLog log = dailyHealthLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhật ký sức khỏe có mã số ID: " + id));
        updateEntity(log, request);
        DailyHealthLog updatedLog = dailyHealthLogRepository.save(log);
        triggerDailyAiPrediction(updatedLog.getPatient(), updatedLog);
        return toResponse(updatedLog);
    }

    @Override
    @Transactional
    public void deleteLog(Long id) {
        DailyHealthLog log = dailyHealthLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhật ký sức khỏe có mã số ID: " + id));
        dailyHealthLogRepository.delete(log);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyHealthLogResponse> getChartData(Long patientId, LocalDate from, LocalDate to) {
        List<DailyHealthLog> logs = dailyHealthLogRepository.findByPatientIdAndLogDateBetweenOrderByLogDate(patientId, from, to);
        // Resolve thresholds once per patient/metric/type
        Map<String, Optional<fpt.swp391.GlucoTrackAlert.model.HealthThreshold>> resolved = new HashMap<>();
        for (DailyHealthLog log : logs) {
            Long pId = log.getPatient() != null ? log.getPatient().getId() : null;
            String pType = log.getPatient() != null ? log.getPatient().getPatientType() : null;
            String key = (pId == null ? "null" : pId.toString()) + "|" + (pType == null ? "" : pType) + "|" + MetricType.BLOOD_SUGAR.name();
            if (!resolved.containsKey(key)) {
                resolved.put(key, healthThresholdService.resolveThreshold(pId, pType, MetricType.BLOOD_SUGAR));
            }
        }

        return logs.stream().map(log -> {
            Long pId = log.getPatient() != null ? log.getPatient().getId() : null;
            String pType = log.getPatient() != null ? log.getPatient().getPatientType() : null;
            String key = (pId == null ? "null" : pId.toString()) + "|" + (pType == null ? "" : pType) + "|" + MetricType.BLOOD_SUGAR.name();
            Optional<fpt.swp391.GlucoTrackAlert.model.HealthThreshold> opt = resolved.get(key);
            String status = "unknown";
            if (log.getBloodSugar() != null) {
                if (opt.isPresent()) {
                    fpt.swp391.GlucoTrackAlert.model.HealthThreshold t = opt.get();
                    double v = log.getBloodSugar().doubleValue();
                    double normalMin = t.getNormalMin().doubleValue();
                    double normalMax = t.getNormalMax().doubleValue();
                    double warningMin = t.getWarningMin().doubleValue();
                    double warningMax = t.getWarningMax().doubleValue();
                    if (v >= normalMin && v <= normalMax) status = "NORMAL";
                    else if (v < normalMin) status = (v >= warningMin) ? "LOW_WARNING" : "LOW_DANGER";
                    else status = (v <= warningMax) ? "HIGH_WARNING" : "HIGH_DANGER";
                } else {
                    status = "unknown";
                }
            }
            return DailyHealthLogResponse.builder()
                    .id(log.getId())
                    .patientId(log.getPatient() != null ? log.getPatient().getId() : null)
                    .userId(log.getPatient() != null && log.getPatient().getUser() != null
                            ? log.getPatient().getUser().getId() : null)
                    .patientName(log.getPatient() != null ? log.getPatient().getFullName() : null)
                    .logDate(log.getLogDate())
                    .bloodSugar(log.getBloodSugar())
                    .systolic(log.getSystolic())
                    .diastolic(log.getDiastolic())
                    .sleepHours(log.getSleepHours())
                    .waterMl(log.getWaterMl())
                    .sugarConsumptionLevel(log.getSugarConsumptionLevel())
                    .symptoms(log.getSymptoms())
                    .note(log.getNote())
                    .createdAt(log.getCreatedAt())
                    .updatedAt(log.getUpdatedAt())
                    .patientType(log.getPatient() != null ? log.getPatient().getPatientType() : null)
                    .bloodSugarStatus(status)
                    .build();
        }).collect(Collectors.toList());
    }

    // === Helper Methods ===

    private DailyHealthLogResponse toResponse(DailyHealthLog log) {
        if (log == null) {
            return null;
        }
        BigDecimal riskPercentage = null;
        String riskLevel = null;
        String aiSummary = null;
        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT risk_percentage, risk_level, ai_summary FROM risk_assessments WHERE daily_health_log_id = ? ORDER BY id DESC LIMIT 1",
                log.getId()
            );
            if (!list.isEmpty()) {
                Map<String, Object> map = list.get(0);
                riskPercentage = (BigDecimal) map.get("risk_percentage");
                riskLevel = (String) map.get("risk_level");
                aiSummary = (String) map.get("ai_summary");
            }
        } catch (Exception e) {
            // ignore
        }

        return DailyHealthLogResponse.builder()
                .id(log.getId())
                .patientId(log.getPatient() != null ? log.getPatient().getId() : null)
                .userId(log.getPatient() != null && log.getPatient().getUser() != null
                    ? log.getPatient().getUser().getId() : null)
                .patientName(log.getPatient() != null ? log.getPatient().getFullName() : null)
                .logDate(log.getLogDate())
                .bloodSugar(log.getBloodSugar())
                .systolic(log.getSystolic())
                .diastolic(log.getDiastolic())
                .sleepHours(log.getSleepHours())
                .waterMl(log.getWaterMl())
                .sugarConsumptionLevel(log.getSugarConsumptionLevel())
                .symptoms(log.getSymptoms())
                .note(log.getNote())
                .createdAt(log.getCreatedAt())
                .updatedAt(log.getUpdatedAt())
                .patientType(log.getPatient() != null ? log.getPatient().getPatientType() : null)
                .bloodSugarStatus(healthThresholdService.evaluate(
                    log.getBloodSugar(),
                    log.getPatient() != null ? log.getPatient().getId() : null,
                    log.getPatient() != null ? log.getPatient().getPatientType() : null,
                    MetricType.BLOOD_SUGAR))
                .riskPercentage(riskPercentage)
                .riskLevel(riskLevel)
                .aiSummary(aiSummary)
                .build();
    }

    private void triggerDailyAiPrediction(Patient patient, DailyHealthLog log) {
        if (patient == null || log == null || log.getBloodSugar() == null) {
            return;
        }

        new Thread(() -> {
            try {
                int genderVal = 0; // Default Male
                if (patient.getGender() != null) {
                    String g = patient.getGender().toLowerCase();
                    if (g.contains("fem") || g.contains("nữ")) genderVal = 1;
                    else if (g.contains("oth") || g.contains("khác")) genderVal = -1;
                }

                double ageVal = patient.getAge() != null ? patient.getAge() : 0.0;
                int hyperVal = Boolean.TRUE.equals(patient.getHypertension()) ? 1 : 0;
                int heartVal = Boolean.TRUE.equals(patient.getHeartDisease()) ? 1 : 0;
                int marriedVal = patient.getEverMarried() != null && patient.getEverMarried().equalsIgnoreCase("Yes") ? 1 : 0;

                int workVal = 0; // Default Private
                if (patient.getWorkType() != null) {
                    String w = patient.getWorkType();
                    if (w.equalsIgnoreCase("Self-employed")) workVal = 1;
                    else if (w.equalsIgnoreCase("Govt_job")) workVal = 2;
                    else if (w.equalsIgnoreCase("children")) workVal = -1;
                    else if (w.equalsIgnoreCase("Never_worked")) workVal = -2;
                }

                int resVal = 1; // Default Urban
                if (patient.getResidenceType() != null && patient.getResidenceType().equalsIgnoreCase("Rural")) {
                    resVal = 0;
                }

                // convert blood sugar from mmol/L to mg/dL by multiplying by 18
                double glucoseVal = log.getBloodSugar().doubleValue() * 18.0;
                double bmiVal = patient.getBmi() != null ? patient.getBmi().doubleValue() : 25.0;

                int smokeVal = -1; // Default Unknown
                if (patient.getSmokingStatus() != null) {
                    String s = patient.getSmokingStatus();
                    if (s.equalsIgnoreCase("never smoked")) smokeVal = 0;
                    else if (s.equalsIgnoreCase("formerly smoked")) smokeVal = 1;
                    else if (s.equalsIgnoreCase("smokes")) smokeVal = 2;
                }

                // Construct JSON payload
                String jsonPayload = String.format(
                    "{\"gender\":%d,\"age\":%.1f,\"hypertension\":%d,\"heart_disease\":%d,\"work_type\":%d,\"Residence_type\":%d,\"avg_glucose_level\":%.2f,\"bmi\":%.2f,\"smoking_status\":%d}",
                    genderVal, ageVal, hyperVal, heartVal, workVal, resVal, glucoseVal, bmiVal, smokeVal
                );

                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:8000/predict"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(3))
                    .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    double riskPercentage = 0.0;
                    String riskLevel = "Low";

                    if (responseBody.contains("risk_percentage")) {
                        int idx = responseBody.indexOf("risk_percentage");
                        int start = responseBody.indexOf(":", idx) + 1;
                        int end = responseBody.indexOf(",", start);
                        if (end == -1) end = responseBody.indexOf("}", start);
                        riskPercentage = Double.parseDouble(responseBody.substring(start, end).trim());
                    }
                    if (responseBody.contains("risk_level")) {
                        int idx = responseBody.indexOf("risk_level");
                        int start = responseBody.indexOf("\"", responseBody.indexOf(":", idx)) + 1;
                        int end = responseBody.indexOf("\"", start);
                        riskLevel = responseBody.substring(start, end).trim();
                    }

                    // Delete existing prediction for this log
                    jdbcTemplate.update("DELETE FROM risk_assessments WHERE daily_health_log_id = ?", log.getId());

                    // Insert new prediction
                    String aiSummary = "Dựa trên mô hình học máy Random Forest phân tích chỉ số hôm nay, nguy cơ xảy ra biến chứng đột quỵ của bạn là " + String.format("%.2f", riskPercentage) + "% (Mức độ: " + riskLevel + ").";
                    String recommendation = "Hãy tiếp tục duy trì chế độ sinh hoạt lành mạnh và kiểm soát lượng đường huyết.";

                    jdbcTemplate.update(
                        "INSERT INTO risk_assessments (patient_id, daily_health_log_id, assessment_type, risk_level, risk_percentage, ai_summary, recommendation, assessed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        patient.getId(),
                        log.getId(),
                        "DAILY_AI_PREDICTION",
                        riskLevel,
                        new java.math.BigDecimal(riskPercentage),
                        aiSummary,
                        recommendation,
                        java.time.LocalDateTime.now()
                    );
                }
            } catch (Exception e) {
                System.err.println("Error calling AI prediction API: " + e.getMessage());
            }
        }).start();
    }

    private DailyHealthLog toEntity(DailyHealthLogRequest request) {
        if (request == null) {
            return null;
        }
        return DailyHealthLog.builder()
                .logDate(request.getLogDate())
                .bloodSugar(request.getBloodSugar())
                .systolic(request.getSystolic())
                .diastolic(request.getDiastolic())
                .sleepHours(request.getSleepHours())
                .waterMl(request.getWaterMl())
                .sugarConsumptionLevel(request.getSugarConsumptionLevel())
                .symptoms(request.getSymptoms())
                .note(request.getNote())
                .build();
    }

    private void updateEntity(DailyHealthLog entity, DailyHealthLogRequest request) {
        if (entity == null || request == null) {
            return;
        }
        entity.setLogDate(request.getLogDate());
        entity.setBloodSugar(request.getBloodSugar());
        entity.setSystolic(request.getSystolic());
        entity.setDiastolic(request.getDiastolic());
        entity.setSleepHours(request.getSleepHours());
        entity.setWaterMl(request.getWaterMl());
        entity.setSugarConsumptionLevel(request.getSugarConsumptionLevel());
        entity.setSymptoms(request.getSymptoms());
        entity.setNote(request.getNote());
    }
}
