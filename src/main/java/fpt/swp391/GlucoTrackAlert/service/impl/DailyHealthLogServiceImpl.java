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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;

@Service
@RequiredArgsConstructor
public class DailyHealthLogServiceImpl implements DailyHealthLogService {

    private final DailyHealthLogRepository dailyHealthLogRepository;
    private final PatientRepository patientRepository;
    private final HealthThresholdService healthThresholdService;
    private final ComplicationRiskService complicationRiskService;
    private final WeeklyReportService weeklyReportService;
    private final WeeklyHealthReportRepository weeklyHealthReportRepository;

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
                .build();
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

}