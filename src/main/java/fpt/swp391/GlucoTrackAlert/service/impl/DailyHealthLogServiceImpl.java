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
import fpt.swp391.GlucoTrackAlert.service.ComplicationRiskService;
import fpt.swp391.GlucoTrackAlert.service.WeeklyReportService;
import fpt.swp391.GlucoTrackAlert.repository.risk.WeeklyHealthReportRepository;
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
import org.springframework.scheduling.annotation.Scheduled;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;

@Service
@RequiredArgsConstructor
public class DailyHealthLogServiceImpl implements DailyHealthLogService {

    private final DailyHealthLogRepository dailyHealthLogRepository;
    private final PatientRepository patientRepository;
    private final HealthThresholdService healthThresholdService;
    private final ComplicationRiskService complicationRiskService;
    private final WeeklyReportService weeklyReportService;
    private final WeeklyHealthReportRepository weeklyHealthReportRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public Page<DailyHealthLogResponse> getLogs(Long patientId, Pageable pageable) {
        Page<DailyHealthLog> page = dailyHealthLogRepository.findByPatientIdOrderByLogDateDesc(patientId, pageable);

        // Resolve thresholds grouped by (patientId, patientType, metricType) to avoid N+1
        Map<String, Optional<fpt.swp391.GlucoTrackAlert.model.HealthThreshold>> resolved = new HashMap<>();

        // Pre-resolve thresholds for blood sugar, systolic and diastolic to avoid N+1
        for (DailyHealthLog log : page.getContent()) {
            Long pId = log.getPatient() != null ? log.getPatient().getId() : null;
            String pType = log.getPatient() != null ? log.getPatient().getPatientType() : null;
            String keyBs = (pId == null ? "null" : pId.toString()) + "|" + (pType == null ? "" : pType) + "|" + MetricType.BLOOD_SUGAR.name();
            if (!resolved.containsKey(keyBs)) resolved.put(keyBs, healthThresholdService.resolveThreshold(pId, pType, MetricType.BLOOD_SUGAR));
            String keySys = (pId == null ? "null" : pId.toString()) + "|" + (pType == null ? "" : pType) + "|" + MetricType.SYSTOLIC.name();
            if (!resolved.containsKey(keySys)) resolved.put(keySys, healthThresholdService.resolveThreshold(pId, pType, MetricType.SYSTOLIC));
            String keyDia = (pId == null ? "null" : pId.toString()) + "|" + (pType == null ? "" : pType) + "|" + MetricType.DIASTOLIC.name();
            if (!resolved.containsKey(keyDia)) resolved.put(keyDia, healthThresholdService.resolveThreshold(pId, pType, MetricType.DIASTOLIC));
        }

        List<DailyHealthLogResponse> mapped = page.getContent().stream().map(log -> {
            Long pId = log.getPatient() != null ? log.getPatient().getId() : null;
            String pType = log.getPatient() != null ? log.getPatient().getPatientType() : null;
            String key = (pId == null ? "null" : pId.toString()) + "|" + (pType == null ? "" : pType) + "|" + MetricType.BLOOD_SUGAR.name();
            Optional<fpt.swp391.GlucoTrackAlert.model.HealthThreshold> opt = resolved.get(key);
            String bloodSugarStatus = evaluateStatus(log.getBloodSugar(), opt);

            Optional<fpt.swp391.GlucoTrackAlert.model.HealthThreshold> optSys = resolved.get((log.getPatient() != null ? log.getPatient().getId().toString() : "null") + "|" + (log.getPatient() != null ? log.getPatient().getPatientType() : "") + "|" + MetricType.SYSTOLIC.name());
            String systolicStatus = evaluateStatus(log.getSystolic() != null ? java.math.BigDecimal.valueOf(log.getSystolic()) : null, optSys);

            Optional<fpt.swp391.GlucoTrackAlert.model.HealthThreshold> optDia = resolved.get((log.getPatient() != null ? log.getPatient().getId().toString() : "null") + "|" + (log.getPatient() != null ? log.getPatient().getPatientType() : "") + "|" + MetricType.DIASTOLIC.name());
            String diastolicStatus = evaluateStatus(log.getDiastolic() != null ? java.math.BigDecimal.valueOf(log.getDiastolic()) : null, optDia);
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
                    .bloodSugarStatus(bloodSugarStatus)
                    .systolicStatus(systolicStatus)
                    .diastolicStatus(diastolicStatus)
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

    private String evaluateStatus(java.math.BigDecimal value, Optional<fpt.swp391.GlucoTrackAlert.model.HealthThreshold> opt) {
        String status = "unknown";
        if (value == null) return status;
        if (opt.isPresent()) {
            fpt.swp391.GlucoTrackAlert.model.HealthThreshold t = opt.get();
            double v = value.doubleValue();
            double normalMin = t.getNormalMin().doubleValue();
            double normalMax = t.getNormalMax().doubleValue();
            double warningMin = t.getWarningMin().doubleValue();
            double warningMax = t.getWarningMax().doubleValue();
            if (v >= normalMin && v <= normalMax) {
                status = "NORMAL";
            } else if (v < normalMin) {
                status = (v >= warningMin) ? "LOW_WARNING" : "LOW_DANGER";
            } else {
                status = (v <= warningMax) ? "HIGH_WARNING" : "HIGH_DANGER";
            }
        }
        return status;
    }

    @Override
    @Transactional
    public DailyHealthLogResponse createLog(Long patientId, DailyHealthLogRequest request) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bệnh nhân có mã số ID: " + patientId));

        if (dailyHealthLogRepository.existsByPatientIdAndLogDate(patientId, request.getLogDate())) {
            throw new RuntimeException("Bạn đã nhập nhật ký sức khỏe cho ngày " + request.getLogDate() + " rồi. Vui lòng chỉnh sửa thay vì tạo mới.");
        }

        DailyHealthLog log = toEntity(request);
        log.setPatient(patient);
        DailyHealthLog savedLog = dailyHealthLogRepository.save(log);
        java.time.LocalDate logDate = savedLog.getLogDate();
        java.time.LocalDate weekStart = logDate.with(java.time.DayOfWeek.MONDAY);
        java.time.LocalDate weekEnd = weekStart.plusDays(6);
        try {
            // On create: when a full week is completed (Sunday), generate weekly report if not exists
            java.time.DayOfWeek dow = logDate.getDayOfWeek();
            if (dow == java.time.DayOfWeek.SUNDAY) {
                java.util.List<DailyHealthLog> weekLogs = dailyHealthLogRepository.findByPatientIdAndLogDateBetweenOrderByLogDate(patientId, weekStart, weekEnd);
                if (weekLogs.size() == 7 && !weeklyHealthReportRepository.existsByPatientIdAndWeekStart(patientId, weekStart)) {
                    weeklyReportService.generateWeeklyReport(patientId, weekStart);
                }
            }
            // NOTE: daily assessment (complicationRiskService) was intentionally disabled to avoid
            // running immediate per-day analysis when importing historical logs.
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Ensure weekly report metrics are recalculated if a report already exists for this week.
        try {
            weeklyReportService.recalculateIfExists(patientId, weekStart);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return toResponse(savedLog);
    }

    @Override
    @Transactional
    public DailyHealthLogResponse updateLog(Long id, DailyHealthLogRequest request) {
        DailyHealthLog log = dailyHealthLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhật ký sức khỏe có mã số ID: " + id));
        // Prevent duplicate date for the same patient (excluding this id)
        Long patientId = log.getPatient() != null ? log.getPatient().getId() : null;
        if (patientId != null && dailyHealthLogRepository.existsByPatientIdAndLogDateAndIdNot(patientId, request.getLogDate(), id)) {
            throw new RuntimeException("Bạn đã nhập nhật ký sức khỏe cho ngày " + request.getLogDate() + " rồi. Vui lòng chỉnh sửa bản ghi hiện có.");
        }
        updateEntity(log, request);
        DailyHealthLog updatedLog = dailyHealthLogRepository.save(log);
        try {
            Long pid = updatedLog.getPatient() != null ? updatedLog.getPatient().getId() : null;
            if (pid != null) {
                java.time.LocalDate weekStart = updatedLog.getLogDate().with(java.time.DayOfWeek.MONDAY);
                weeklyReportService.recalculateIfExists(pid, weekStart);
                // also re-run daily assessment
                // NOTE: This daily assessment (`NEPHROPATHY`) is kept for internal/logging
                // purposes only. The risk-warnings pages now show weekly assessments
                // (`NEPHROPATHY_WEEKLY`) generated by the weekly report service.
                complicationRiskService.assessPatient(pid, updatedLog.getId());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return toResponse(updatedLog);
    }

    @Override
    @Transactional
    public void deleteLog(Long id) {
        DailyHealthLog log = dailyHealthLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhật ký sức khỏe có mã số ID: " + id));
        Long patientId = log.getPatient() != null ? log.getPatient().getId() : null;
        java.time.LocalDate weekStart = log.getLogDate() != null ? log.getLogDate().with(java.time.DayOfWeek.MONDAY) : null;
        try {
            dailyHealthLogRepository.delete(log);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Rethrow the original DataIntegrityViolationException so the
            // GlobalExceptionHandler can map it to HTTP 409 with a user-friendly message.
            throw ex;
        }
        // Keep the weekly report (if one already exists for this week) in sync with the
        // remaining logs, otherwise it would keep showing stale averages/risk that still
        // include the log that was just deleted.
        if (patientId != null && weekStart != null) {
            try {
                weeklyReportService.recalculateIfExists(patientId, weekStart);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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
                    if (v >= normalMin && v <= normalMax) {
                        status = "NORMAL"; 
                    }else if (v < normalMin) {
                        status = (v >= warningMin) ? "LOW_WARNING" : "LOW_DANGER"; 
                    }else {
                        status = (v <= warningMax) ? "HIGH_WARNING" : "HIGH_DANGER";
                    }
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
                .systolicStatus(healthThresholdService.evaluate(
                    log.getSystolic() != null ? java.math.BigDecimal.valueOf(log.getSystolic()) : null,
                    log.getPatient() != null ? log.getPatient().getId() : null,
                    log.getPatient() != null ? log.getPatient().getPatientType() : null,
                    MetricType.SYSTOLIC))
                .diastolicStatus(healthThresholdService.evaluate(
                    log.getDiastolic() != null ? java.math.BigDecimal.valueOf(log.getDiastolic()) : null,
                    log.getPatient() != null ? log.getPatient().getId() : null,
                    log.getPatient() != null ? log.getPatient().getPatientType() : null,
                    MetricType.DIASTOLIC))
                // expose numeric thresholds for blood sugar tooltip
                .bloodSugarNormalMin(healthThresholdService.resolveThreshold(
                    log.getPatient() != null ? log.getPatient().getId() : null,
                    log.getPatient() != null ? log.getPatient().getPatientType() : null,
                    MetricType.BLOOD_SUGAR).map(t -> t.getNormalMin()).orElse(null))
                .bloodSugarNormalMax(healthThresholdService.resolveThreshold(
                    log.getPatient() != null ? log.getPatient().getId() : null,
                    log.getPatient() != null ? log.getPatient().getPatientType() : null,
                    MetricType.BLOOD_SUGAR).map(t -> t.getNormalMax()).orElse(null))
                .bloodSugarWarningMin(healthThresholdService.resolveThreshold(
                    log.getPatient() != null ? log.getPatient().getId() : null,
                    log.getPatient() != null ? log.getPatient().getPatientType() : null,
                    MetricType.BLOOD_SUGAR).map(t -> t.getWarningMin()).orElse(null))
                .bloodSugarWarningMax(healthThresholdService.resolveThreshold(
                    log.getPatient() != null ? log.getPatient().getId() : null,
                    log.getPatient() != null ? log.getPatient().getPatientType() : null,
                    MetricType.BLOOD_SUGAR).map(t -> t.getWarningMax()).orElse(null))
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

    @Scheduled(cron = "0 50 23 * * SUN")
    public void generateWeeklyReportsForWeek() {
        // Weekly scheduled job: generate weekly reports for patients who have logs in the week but missing a report
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        java.time.LocalDate weekEnd = weekStart.plusDays(6);
        for (Patient patient : patientRepository.findAll()) {
            Long pid = patient.getId();
            List<DailyHealthLog> logs = dailyHealthLogRepository.findByPatientIdAndLogDateBetweenOrderByLogDate(pid, weekStart, weekEnd);
            if (logs == null || logs.isEmpty()) continue;
            if (!weeklyHealthReportRepository.existsByPatientIdAndWeekStart(pid, weekStart)) {
                try {
                    weeklyReportService.generateWeeklyReport(pid, weekStart);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }


    private BigDecimal getBigDecimalSafe(Object obj) {
        if (obj == null) return null;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        if (obj instanceof Number) return BigDecimal.valueOf(((Number) obj).doubleValue());
        return new BigDecimal(obj.toString());
    }

    @Override
    @Transactional
    public void assessWeeklyRisk(Long patientId) {
        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null) {
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfWeek = today.minusWeeks(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        // Get logs for the previous week
        List<DailyHealthLog> weeklyLogs = dailyHealthLogRepository.findByPatientIdAndLogDateBetweenOrderByLogDate(patientId, startOfWeek, endOfWeek);
        if (weeklyLogs == null || weeklyLogs.isEmpty()) {
            return;
        }

        // Find logs with non-null blood sugar
        double sumSugar = 0;
        int sugarCount = 0;
        double sumSystolic = 0;
        int countSystolic = 0;
        double sumDiastolic = 0;
        int countDiastolic = 0;
        double sumSleep = 0;
        int countSleep = 0;
        double sumWater = 0;
        int countWater = 0;
        int highSugarDays = 0;
        int warningCount = 0;

        DailyHealthLog latestLogWithSugar = null;
        LocalDateTime maxUpdatedAt = null;

        String patientType = patient.getPatientType();

        for (DailyHealthLog log : weeklyLogs) {
            if (log.getBloodSugar() != null) {
                BigDecimal sugarVal = log.getBloodSugar();
                sumSugar += sugarVal.doubleValue();
                sugarCount++;
                latestLogWithSugar = log;

                // Evaluate sugar threshold
                String sugarStatus = healthThresholdService.evaluate(sugarVal, patientId, patientType, MetricType.BLOOD_SUGAR);
                if (!"NORMAL".equals(sugarStatus) && !"UNKNOWN".equalsIgnoreCase(sugarStatus) && !"unknown".equalsIgnoreCase(sugarStatus)) {
                    highSugarDays++;
                }
                if (!"NORMAL".equals(sugarStatus) && !"UNKNOWN".equalsIgnoreCase(sugarStatus) && !"unknown".equalsIgnoreCase(sugarStatus)) {
                    warningCount++;
                }
            }

            if (log.getSystolic() != null) {
                double sysVal = log.getSystolic();
                sumSystolic += sysVal;
                countSystolic++;

                String sysStatus = healthThresholdService.evaluate(BigDecimal.valueOf(sysVal), patientId, patientType, MetricType.SYSTOLIC);
                if (!"NORMAL".equals(sysStatus) && !"UNKNOWN".equalsIgnoreCase(sysStatus) && !"unknown".equalsIgnoreCase(sysStatus)) {
                    warningCount++;
                }
            }

            if (log.getDiastolic() != null) {
                double diaVal = log.getDiastolic();
                sumDiastolic += diaVal;
                countDiastolic++;

                String diaStatus = healthThresholdService.evaluate(BigDecimal.valueOf(diaVal), patientId, patientType, MetricType.DIASTOLIC);
                if (!"NORMAL".equals(diaStatus) && !"UNKNOWN".equalsIgnoreCase(diaStatus) && !"unknown".equalsIgnoreCase(diaStatus)) {
                    warningCount++;
                }
            }

            if (log.getSleepHours() != null) {
                sumSleep += log.getSleepHours().doubleValue();
                countSleep++;
            }

            if (log.getWaterMl() != null) {
                sumWater += log.getWaterMl().doubleValue();
                countWater++;
            }

            if (log.getUpdatedAt() != null) {
                if (maxUpdatedAt == null || log.getUpdatedAt().isAfter(maxUpdatedAt)) {
                    maxUpdatedAt = log.getUpdatedAt();
                }
            }
        }

        if (sugarCount == 0 || latestLogWithSugar == null) {
            return; // No blood sugar data to assess
        }

        double avgSugarMmol = sumSugar / sugarCount;
        double avgGlucoseMgDl = avgSugarMmol * 18.0;

        // Check if a weekly assessment already exists
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            "SELECT ra.id, ra.assessed_at FROM risk_assessments ra " +
            "JOIN daily_health_logs dhl ON ra.daily_health_log_id = dhl.id " +
            "WHERE ra.patient_id = ? AND ra.assessment_type = 'WEEKLY_AI_PREDICTION' " +
            "AND dhl.log_date >= ? AND dhl.log_date <= ? ORDER BY ra.id DESC",
            patientId,
            startOfWeek,
            endOfWeek
        );

        boolean needUpdate = true;

        // Force update if the weekly report record doesn't exist yet
        List<Map<String, Object>> existingReport = jdbcTemplate.queryForList(
            "SELECT id FROM weekly_health_reports WHERE patient_id = ? AND week_start = ? AND week_end = ?",
            patientId,
            startOfWeek,
            endOfWeek
        );

        if (!existing.isEmpty() && !existingReport.isEmpty()) {
            Map<String, Object> map = existing.get(0);
            Object assessedAtObj = map.get("assessed_at");
            LocalDateTime assessedAt = null;
            if (assessedAtObj instanceof LocalDateTime) {
                assessedAt = (LocalDateTime) assessedAtObj;
            } else if (assessedAtObj instanceof java.sql.Timestamp) {
                assessedAt = ((java.sql.Timestamp) assessedAtObj).toLocalDateTime();
            }

            // If the latest update in logs is not after the assessment time, and we have exactly 1 record, we don't need to recalculate
            if (assessedAt != null && maxUpdatedAt != null && !maxUpdatedAt.isAfter(assessedAt) && existing.size() == 1) {
                needUpdate = false;
            }
        }

        if (needUpdate) {
            // Call AI API synchronously
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

                double bmiVal = patient.getBmi() != null ? patient.getBmi().doubleValue() : 25.0;

                int smokeVal = -1; // Default Unknown
                if (patient.getSmokingStatus() != null) {
                    String s = patient.getSmokingStatus();
                    if (s.equalsIgnoreCase("never smoked")) smokeVal = 0;
                    else if (s.equalsIgnoreCase("formerly smoked")) smokeVal = 1;
                    else if (s.equalsIgnoreCase("smokes")) smokeVal = 2;
                }

                // Construct JSON payload using calculated average glucose
                String jsonPayload = String.format(
                    "{\"gender\":%d,\"age\":%.1f,\"hypertension\":%d,\"heart_disease\":%d,\"work_type\":%d,\"Residence_type\":%d,\"avg_glucose_level\":%.2f,\"bmi\":%.2f,\"smoking_status\":%d}",
                    genderVal, ageVal, hyperVal, heartVal, workVal, resVal, avgGlucoseMgDl, bmiVal, smokeVal
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

                    // Delete existing weekly assessments for this week
                    for (Map<String, Object> row : existing) {
                        Long oldId = ((Number) row.get("id")).longValue();
                        jdbcTemplate.update("DELETE FROM risk_assessments WHERE id = ?", oldId);
                    }

                    // Insert new prediction linked to the latest log of the week
                    String aiSummary = "Dựa trên mô hình học máy Random Forest phân tích chỉ số trung bình tuần này, nguy cơ xảy ra biến chứng đột quỵ của bạn là " + String.format("%.2f", riskPercentage) + "% (Mức độ: " + riskLevel + ").";
                    String recommendation = "Hãy tiếp tục duy trì chế độ sinh hoạt lành mạnh và kiểm soát lượng đường huyết trung bình ở mức an toàn.";

                    jdbcTemplate.update(
                        "INSERT INTO risk_assessments (patient_id, daily_health_log_id, assessment_type, risk_level, risk_percentage, ai_summary, recommendation, assessed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        patient.getId(),
                        latestLogWithSugar.getId(),
                        "WEEKLY_AI_PREDICTION",
                        riskLevel,
                        new java.math.BigDecimal(riskPercentage),
                        aiSummary,
                        recommendation,
                        java.time.LocalDateTime.now()
                    );

                    // --- GENERATE & SAVE WEEKLY REPORT ---
                    LocalDate prevStartOfWeek = startOfWeek.minusWeeks(1);
                    LocalDate prevEndOfWeek = endOfWeek.minusWeeks(1);
                    List<Map<String, Object>> prevReports = jdbcTemplate.queryForList(
                        "SELECT id, average_blood_sugar, average_systolic, average_diastolic, average_sleep_hours, average_water_ml " +
                        "FROM weekly_health_reports WHERE patient_id = ? AND week_start = ? AND week_end = ?",
                        patientId, prevStartOfWeek, prevEndOfWeek
                    );

                    BigDecimal prevSugar = null;
                    BigDecimal prevSystolic = null;
                    BigDecimal prevDiastolic = null;
                    BigDecimal prevSleep = null;
                    BigDecimal prevWater = null;
                    Long prevReportId = null;

                    if (!prevReports.isEmpty()) {
                        Map<String, Object> prev = prevReports.get(0);
                        prevReportId = ((Number) prev.get("id")).longValue();
                        prevSugar = getBigDecimalSafe(prev.get("average_blood_sugar"));
                        prevSystolic = getBigDecimalSafe(prev.get("average_systolic"));
                        prevDiastolic = getBigDecimalSafe(prev.get("average_diastolic"));
                        prevSleep = getBigDecimalSafe(prev.get("average_sleep_hours"));
                        prevWater = getBigDecimalSafe(prev.get("average_water_ml"));
                    }

                    // Averages
                    BigDecimal avgSugarVal = BigDecimal.valueOf(avgSugarMmol).setScale(2, java.math.RoundingMode.HALF_UP);
                    BigDecimal avgSystolicVal = countSystolic > 0 ? BigDecimal.valueOf(sumSystolic / countSystolic).setScale(2, java.math.RoundingMode.HALF_UP) : null;
                    BigDecimal avgDiastolicVal = countDiastolic > 0 ? BigDecimal.valueOf(sumDiastolic / countDiastolic).setScale(2, java.math.RoundingMode.HALF_UP) : null;
                    BigDecimal avgSleepVal = countSleep > 0 ? BigDecimal.valueOf(sumSleep / countSleep).setScale(2, java.math.RoundingMode.HALF_UP) : null;
                    BigDecimal avgWaterVal = countWater > 0 ? BigDecimal.valueOf(sumWater / countWater).setScale(2, java.math.RoundingMode.HALF_UP) : null;

                    // Changes
                    BigDecimal sugarChange = null;
                    BigDecimal sugarChangePercent = null;
                    if (prevSugar != null) {
                        sugarChange = avgSugarVal.subtract(prevSugar);
                        if (prevSugar.compareTo(BigDecimal.ZERO) > 0) {
                            sugarChangePercent = sugarChange.multiply(BigDecimal.valueOf(100)).divide(prevSugar, 2, java.math.RoundingMode.HALF_UP);
                        }
                    }

                    BigDecimal systolicChange = null;
                    if (avgSystolicVal != null && prevSystolic != null) {
                        systolicChange = avgSystolicVal.subtract(prevSystolic);
                    }

                    BigDecimal diastolicChange = null;
                    if (avgDiastolicVal != null && prevDiastolic != null) {
                        diastolicChange = avgDiastolicVal.subtract(prevDiastolic);
                    }

                    BigDecimal sleepChange = null;
                    if (avgSleepVal != null && prevSleep != null) {
                        sleepChange = avgSleepVal.subtract(prevSleep);
                    }

                    // Trend and health status
                    String trendStatus = "STABLE";
                    if (sugarChange != null) {
                        int cmp = sugarChange.compareTo(BigDecimal.ZERO);
                        if (cmp > 0) {
                            trendStatus = "WORSENING";
                        } else if (cmp < 0) {
                            trendStatus = "IMPROVING";
                        }
                    }

                    String healthStatus = "GOOD";
                    if (warningCount > 0) {
                        if (highSugarDays > 2 || warningCount > 4) {
                            healthStatus = "DANGER";
                        } else {
                            healthStatus = "WARNING";
                        }
                    }

                    // Delete existing report
                    jdbcTemplate.update(
                        "DELETE FROM weekly_health_reports WHERE patient_id = ? AND week_start = ? AND week_end = ?",
                        patientId, startOfWeek, endOfWeek
                    );

                    // Insert new report
                    jdbcTemplate.update(
                        "INSERT INTO weekly_health_reports (" +
                        "patient_id, baseline_id, previous_report_id, week_start, week_end, " +
                        "average_blood_sugar, average_systolic, average_diastolic, average_sleep_hours, average_water_ml, " +
                        "high_sugar_days, warning_count, blood_sugar_change, blood_sugar_change_percent, " +
                        "systolic_change, diastolic_change, sleep_hours_change, trend_status, health_status, " +
                        "ai_summary, recommendation, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        patientId,
                        null,
                        prevReportId,
                        startOfWeek,
                        endOfWeek,
                        avgSugarVal,
                        avgSystolicVal,
                        avgDiastolicVal,
                        avgSleepVal,
                        avgWaterVal,
                        highSugarDays,
                        warningCount,
                        sugarChange,
                        sugarChangePercent,
                        systolicChange,
                        diastolicChange,
                        sleepChange,
                        trendStatus,
                        healthStatus,
                        aiSummary,
                        recommendation,
                        java.time.LocalDateTime.now()
                    );
                }
            } catch (Exception e) {
                System.err.println("Error calculating weekly AI prediction and report: " + e.getMessage());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> calculateDynamicRisk(Long patientId, LocalDate from, LocalDate to) {
        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null) {
            return null;
        }

        // Get logs for the current range
        List<DailyHealthLog> logs = dailyHealthLogRepository.findByPatientIdAndLogDateBetweenOrderByLogDate(patientId, from, to);
        if (logs == null || logs.isEmpty()) {
            return null;
        }

        double sumSugar = 0;
        int sugarCount = 0;
        double sumSystolic = 0;
        int countSystolic = 0;
        double sumDiastolic = 0;
        int countDiastolic = 0;
        double sumSleep = 0;
        int countSleep = 0;
        double sumWater = 0;
        int countWater = 0;
        int highSugarDays = 0;
        int warningCount = 0;

        String patientType = patient.getPatientType();

        for (DailyHealthLog log : logs) {
            if (log.getBloodSugar() != null) {
                BigDecimal sugarVal = log.getBloodSugar();
                sumSugar += sugarVal.doubleValue();
                sugarCount++;

                // Evaluate sugar threshold
                String sugarStatus = healthThresholdService.evaluate(sugarVal, patientId, patientType, MetricType.BLOOD_SUGAR);
                if (!"NORMAL".equals(sugarStatus) && !"UNKNOWN".equalsIgnoreCase(sugarStatus) && !"unknown".equalsIgnoreCase(sugarStatus)) {
                    highSugarDays++;
                }
                if (!"NORMAL".equals(sugarStatus) && !"UNKNOWN".equalsIgnoreCase(sugarStatus) && !"unknown".equalsIgnoreCase(sugarStatus)) {
                    warningCount++;
                }
            }

            if (log.getSystolic() != null) {
                double sysVal = log.getSystolic();
                sumSystolic += sysVal;
                countSystolic++;

                String sysStatus = healthThresholdService.evaluate(BigDecimal.valueOf(sysVal), patientId, patientType, MetricType.SYSTOLIC);
                if (!"NORMAL".equals(sysStatus) && !"UNKNOWN".equalsIgnoreCase(sysStatus) && !"unknown".equalsIgnoreCase(sysStatus)) {
                    warningCount++;
                }
            }

            if (log.getDiastolic() != null) {
                double diaVal = log.getDiastolic();
                sumDiastolic += diaVal;
                countDiastolic++;

                String diaStatus = healthThresholdService.evaluate(BigDecimal.valueOf(diaVal), patientId, patientType, MetricType.DIASTOLIC);
                if (!"NORMAL".equals(diaStatus) && !"UNKNOWN".equalsIgnoreCase(diaStatus) && !"unknown".equalsIgnoreCase(diaStatus)) {
                    warningCount++;
                }
            }

            if (log.getSleepHours() != null) {
                sumSleep += log.getSleepHours().doubleValue();
                countSleep++;
            }

            if (log.getWaterMl() != null) {
                sumWater += log.getWaterMl().doubleValue();
                countWater++;
            }
        }

        if (sugarCount == 0) {
            return null; // Yêu cầu có ít nhất 1 bản ghi có đường huyết để dự đoán
        }

        double avgSugarMmol = sumSugar / sugarCount;
        double avgGlucoseMgDl = avgSugarMmol * 18.0;

        BigDecimal avgSugarVal = BigDecimal.valueOf(avgSugarMmol).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal avgSystolicVal = countSystolic > 0 ? BigDecimal.valueOf(sumSystolic / countSystolic).setScale(2, java.math.RoundingMode.HALF_UP) : null;
        BigDecimal avgDiastolicVal = countDiastolic > 0 ? BigDecimal.valueOf(sumDiastolic / countDiastolic).setScale(2, java.math.RoundingMode.HALF_UP) : null;
        BigDecimal avgSleepVal = countSleep > 0 ? BigDecimal.valueOf(sumSleep / countSleep).setScale(2, java.math.RoundingMode.HALF_UP) : null;
        BigDecimal avgWaterVal = countWater > 0 ? BigDecimal.valueOf(sumWater / countWater).setScale(2, java.math.RoundingMode.HALF_UP) : null;

        // Call AI API synchronously
        double riskPercentage = 0.0;
        String riskLevel = "Không rõ";

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

            double bmiVal = patient.getBmi() != null ? patient.getBmi().doubleValue() : 25.0;

            int smokeVal = -1; // Default Unknown
            if (patient.getSmokingStatus() != null) {
                String s = patient.getSmokingStatus();
                if (s.equalsIgnoreCase("never smoked")) smokeVal = 0;
                else if (s.equalsIgnoreCase("formerly smoked")) smokeVal = 1;
                else if (s.equalsIgnoreCase("smokes")) smokeVal = 2;
            }

            // Construct JSON payload using calculated average glucose
            String jsonPayload = String.format(
                "{\"gender\":%d,\"age\":%.1f,\"hypertension\":%d,\"heart_disease\":%d,\"work_type\":%d,\"Residence_type\":%d,\"avg_glucose_level\":%.2f,\"bmi\":%.2f,\"smoking_status\":%d}",
                genderVal, ageVal, hyperVal, heartVal, workVal, resVal, avgGlucoseMgDl, bmiVal, smokeVal
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
            }
        } catch (Exception e) {
            System.err.println("Error calling Stroke AI service: " + e.getMessage());
            riskLevel = "Không xác định";
        }

        // Calculate comparison range
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate prevFrom = from.minusDays(daysBetween);
        LocalDate prevTo = from.minusDays(1);

        List<DailyHealthLog> prevLogs = dailyHealthLogRepository.findByPatientIdAndLogDateBetweenOrderByLogDate(patientId, prevFrom, prevTo);

        double prevSumSugar = 0;
        int prevSugarCount = 0;
        double prevSumSystolic = 0;
        int prevCountSystolic = 0;
        double prevSumDiastolic = 0;
        int prevCountDiastolic = 0;
        double prevSumSleep = 0;
        int prevCountSleep = 0;
        double prevSumWater = 0;
        int prevCountWater = 0;

        for (DailyHealthLog log : prevLogs) {
            if (log.getBloodSugar() != null) {
                prevSumSugar += log.getBloodSugar().doubleValue();
                prevSugarCount++;
            }
            if (log.getSystolic() != null) {
                prevSumSystolic += log.getSystolic();
                prevCountSystolic++;
            }
            if (log.getDiastolic() != null) {
                prevSumDiastolic += log.getDiastolic();
                prevCountDiastolic++;
            }
            if (log.getSleepHours() != null) {
                prevSumSleep += log.getSleepHours().doubleValue();
                prevCountSleep++;
            }
            if (log.getWaterMl() != null) {
                prevSumWater += log.getWaterMl().doubleValue();
                prevCountWater++;
            }
        }

        BigDecimal prevSugar = prevSugarCount > 0 ? BigDecimal.valueOf(prevSumSugar / prevSugarCount).setScale(2, java.math.RoundingMode.HALF_UP) : null;
        BigDecimal prevSystolic = prevCountSystolic > 0 ? BigDecimal.valueOf(prevSumSystolic / prevCountSystolic).setScale(2, java.math.RoundingMode.HALF_UP) : null;
        BigDecimal prevDiastolic = prevCountDiastolic > 0 ? BigDecimal.valueOf(prevSumDiastolic / prevCountDiastolic).setScale(2, java.math.RoundingMode.HALF_UP) : null;
        BigDecimal prevSleep = prevCountSleep > 0 ? BigDecimal.valueOf(prevSumSleep / prevCountSleep).setScale(2, java.math.RoundingMode.HALF_UP) : null;
        BigDecimal prevWater = prevCountWater > 0 ? BigDecimal.valueOf(prevSumWater / prevCountWater).setScale(2, java.math.RoundingMode.HALF_UP) : null;

        // Compute changes
        BigDecimal sugarChange = null;
        BigDecimal sugarChangePercent = null;
        if (prevSugar != null) {
            sugarChange = avgSugarVal.subtract(prevSugar);
            if (prevSugar.compareTo(BigDecimal.ZERO) > 0) {
                sugarChangePercent = sugarChange.multiply(BigDecimal.valueOf(100)).divide(prevSugar, 2, java.math.RoundingMode.HALF_UP);
            }
        }

        BigDecimal systolicChange = null;
        if (avgSystolicVal != null && prevSystolic != null) {
            systolicChange = avgSystolicVal.subtract(prevSystolic);
        }

        BigDecimal diastolicChange = null;
        if (avgDiastolicVal != null && prevDiastolic != null) {
            diastolicChange = avgDiastolicVal.subtract(prevDiastolic);
        }

        BigDecimal sleepChange = null;
        if (avgSleepVal != null && prevSleep != null) {
            sleepChange = avgSleepVal.subtract(prevSleep);
        }

        BigDecimal waterChange = null;
        if (avgWaterVal != null && prevWater != null) {
            waterChange = avgWaterVal.subtract(prevWater);
        }

        // Trend status
        String trendStatus = "STABLE";
        if (sugarChange != null) {
            int cmp = sugarChange.compareTo(BigDecimal.ZERO);
            if (cmp > 0) {
                trendStatus = "WORSENING";
            } else if (cmp < 0) {
                trendStatus = "IMPROVING";
            }
        }

        // Health status
        String healthStatus = "GOOD";
        if (warningCount > 0) {
            if (highSugarDays > 2 || warningCount > 4) {
                healthStatus = "DANGER";
            } else {
                healthStatus = "WARNING";
            }
        }

        // Return everything in the map
        Map<String, Object> result = new HashMap<>();
        result.put("riskPercentage", String.format("%.2f", riskPercentage));
        result.put("riskLevel", riskLevel);
        result.put("assessedAtStr", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        result.put("logDateStr", from.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " - " + to.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        
        result.put("avgSugar", avgSugarVal != null ? String.format("%.2f", avgSugarVal.doubleValue()) : null);
        result.put("avgSystolic", avgSystolicVal != null ? String.format("%.2f", avgSystolicVal.doubleValue()) : null);
        result.put("avgDiastolic", avgDiastolicVal != null ? String.format("%.2f", avgDiastolicVal.doubleValue()) : null);
        result.put("avgSleep", avgSleepVal != null ? String.format("%.2f", avgSleepVal.doubleValue()) : null);
        result.put("avgWater", avgWaterVal != null ? String.format("%.2f", avgWaterVal.doubleValue()) : null);

        result.put("highSugarDays", highSugarDays);
        result.put("warningCount", warningCount);

        result.put("sugarChange", sugarChange != null ? String.format("%.2f", sugarChange.doubleValue()) : null);
        result.put("sugarChangePercent", sugarChangePercent != null ? String.format("%.2f", sugarChangePercent.doubleValue()) : null);
        result.put("systolicChange", systolicChange != null ? String.format("%.2f", systolicChange.doubleValue()) : null);
        result.put("diastolicChange", diastolicChange != null ? String.format("%.2f", diastolicChange.doubleValue()) : null);
        result.put("sleepChange", sleepChange != null ? String.format("%.2f", sleepChange.doubleValue()) : null);
        result.put("waterChange", waterChange != null ? String.format("%.2f", waterChange.doubleValue()) : null);

        result.put("trendStatus", trendStatus);
        result.put("healthStatus", healthStatus);

        return result;
    }
}
