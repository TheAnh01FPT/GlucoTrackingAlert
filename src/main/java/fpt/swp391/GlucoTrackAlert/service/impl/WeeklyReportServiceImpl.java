package fpt.swp391.GlucoTrackAlert.service.impl;

import fpt.swp391.GlucoTrackAlert.enums.RiskLevel;
import fpt.swp391.GlucoTrackAlert.model.DailyHealthLog;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.model.risk.RiskAssessment;
import fpt.swp391.GlucoTrackAlert.model.risk.RiskWarning;
import fpt.swp391.GlucoTrackAlert.model.risk.AiAnalysisLog;
import fpt.swp391.GlucoTrackAlert.model.risk.WeeklyHealthReport;
import fpt.swp391.GlucoTrackAlert.repository.DailyHealthLogRepository;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.repository.risk.WeeklyHealthReportRepository;
import fpt.swp391.GlucoTrackAlert.repository.risk.RiskAssessmentRepository;
import fpt.swp391.GlucoTrackAlert.repository.risk.RiskWarningRepository;
import fpt.swp391.GlucoTrackAlert.repository.risk.AiAnalysisLogRepository;
import fpt.swp391.GlucoTrackAlert.service.HealthThresholdService;
import fpt.swp391.GlucoTrackAlert.service.RiskModelService;
import fpt.swp391.GlucoTrackAlert.service.WeeklyReportService;
import fpt.swp391.GlucoTrackAlert.dto.CustomRangeResult;
import fpt.swp391.GlucoTrackAlert.service.impl.ComplicationRiskServiceImpl;
import fpt.swp391.GlucoTrackAlert.service.cardioai.WeeklyCardioAiService;
import fpt.swp391.GlucoTrackAlert.service.strokeai.WeeklyStrokeAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportServiceImpl implements WeeklyReportService {

    private final DailyHealthLogRepository dailyHealthLogRepository;
    private final PatientRepository patientRepository;
    private final HealthThresholdService healthThresholdService;
    private final RiskModelService riskModelService;
    private final WeeklyHealthReportRepository weeklyHealthReportRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final RiskWarningRepository riskWarningRepository;
    private final AiAnalysisLogRepository aiAnalysisLogRepository;

    private final WeeklyCardioAiService weeklyCardioAiService;
    private final WeeklyStrokeAiService weeklyStrokeAiService;

    // Tạm thời giữ nguyên record Snapshot cho đồng bộ cấu trúc cũ của bạn
    private record WeeklySnapshot(
            BigDecimal avgBloodSugar,
            BigDecimal avgSystolic,
            BigDecimal avgDiastolic,
            int logCount,
            RiskLevel level,
            double riskPct,
            BigDecimal riskPercentage,
            double age,
            double avgDiaForModel,
            double avgBsForModel,
            boolean htn,
            double htnScore) {

    }

    private WeeklySnapshot computeSnapshot(Patient patient, Long patientId, LocalDate weekStart, LocalDate weekEnd) {
        List<DailyHealthLog> logs = dailyHealthLogRepository.findByPatientIdAndLogDateBetweenOrderByLogDate(patientId, weekStart, weekEnd);

        BigDecimal avgBloodSugar = null;
        BigDecimal avgSystolic = null;
        BigDecimal avgDiastolic = null;

        double sumBs = 0;
        int cntBs = 0;
        double sumSys = 0;
        int cntSys = 0;
        double sumDia = 0;
        int cntDia = 0;
        for (DailyHealthLog l : logs) {
            if (l.getBloodSugar() != null) {
                sumBs += l.getBloodSugar().doubleValue();
                cntBs++;
            }
            if (l.getSystolic() != null) {
                sumSys += l.getSystolic().doubleValue();
                cntSys++;
            }
            if (l.getDiastolic() != null) {
                sumDia += l.getDiastolic().doubleValue();
                cntDia++;
            }
        }
        if (cntBs > 0) {
            avgBloodSugar = BigDecimal.valueOf(sumBs / cntBs).setScale(2, RoundingMode.HALF_UP);
        }
        if (cntSys > 0) {
            avgSystolic = BigDecimal.valueOf(sumSys / cntSys).setScale(2, RoundingMode.HALF_UP);
        }
        if (cntDia > 0) {
            avgDiastolic = BigDecimal.valueOf(sumDia / cntDia).setScale(2, RoundingMode.HALF_UP);
        }

        Optional<fpt.swp391.GlucoTrackAlert.model.HealthThreshold> systolicThreshold = healthThresholdService.resolveThreshold(patientId, patient.getPatientType(), fpt.swp391.GlucoTrackAlert.enums.MetricType.SYSTOLIC);
        Optional<fpt.swp391.GlucoTrackAlert.model.HealthThreshold> diastolicThreshold = healthThresholdService.resolveThreshold(patientId, patient.getPatientType(), fpt.swp391.GlucoTrackAlert.enums.MetricType.DIASTOLIC);

        long highBpDays = logs.stream().filter(log -> {
            boolean sysHigh = systolicThreshold.isPresent() && log.getSystolic() != null && log.getSystolic().doubleValue() >= systolicThreshold.get().getNormalMax().doubleValue();
            boolean diaHigh = diastolicThreshold.isPresent() && log.getDiastolic() != null && log.getDiastolic().doubleValue() >= diastolicThreshold.get().getNormalMax().doubleValue();
            return sysHigh || diaHigh;
        }).count();

        boolean htnFromLogs = !logs.isEmpty() && (double) highBpDays / logs.size() >= 0.4;
        boolean htn = Boolean.TRUE.equals(patient.getHypertension()) || htnFromLogs;

        // Tính điểm hypertension liên tục 0.0 - 1.0 theo logs và tiền sử
        double htnScore;
        if (!logs.isEmpty() && logs.size() >= 4) {
            // đủ dữ liệu tuần -> ưu tiên phản ánh mức kiểm soát thực tế
            htnScore = (double) highBpDays / logs.size();
            if (Boolean.TRUE.equals(patient.getHypertension())) {
                // có tiền sử THA -> đặt sàn tối thiểu
                htnScore = Math.max(htnScore, 0.3);
            }
        } else {
            // không đủ log -> dựa vào tiền sử nhưng giảm độ tin cậy (0.6 thay vì 1.0)
            htnScore = Boolean.TRUE.equals(patient.getHypertension()) ? 0.6 : 0.0;
        }

        double age = patient.getAge() != null ? patient.getAge().doubleValue() : 50.0;
        double avgDiaForModel = avgDiastolic != null ? avgDiastolic.doubleValue() : 80.0;
        double avgBsForModel = avgBloodSugar != null ? avgBloodSugar.doubleValue() : 6.0;

        double riskPct = riskModelService.predictRiskPercentage(age, avgDiaForModel, avgBsForModel, htnScore);
        RiskLevel level = riskModelService.mapToRiskLevel(riskPct);
        BigDecimal riskPercentage = BigDecimal.valueOf(riskPct).setScale(2, RoundingMode.HALF_UP);

        return new WeeklySnapshot(avgBloodSugar, avgSystolic, avgDiastolic, logs.size(), level, riskPct, riskPercentage, age, avgDiaForModel, avgBsForModel, htn, htnScore);
    }

    private void applySnapshot(WeeklyHealthReport report, WeeklySnapshot s) {
        report.setAverageBloodSugar(s.avgBloodSugar());
        report.setAverageSystolic(s.avgSystolic());
        report.setAverageDiastolic(s.avgDiastolic());
        report.setHealthStatus(s.level().name());
        report.setRecommendation(ComplicationRiskServiceImpl.getRecommendation(s.level()));
        report.setAiSummary("Tổng hợp " + s.logCount() + " log, đường huyết TB: " + (s.avgBloodSugar() != null ? s.avgBloodSugar() : "null") + " mmol/L, huyết áp TB: " + (s.avgSystolic() != null ? s.avgSystolic() : "null") + "/" + (s.avgDiastolic() != null ? s.avgDiastolic() : "null"));
        // set risk percentage and lowConfidence based on snapshot
        report.setRiskPercentage(s.riskPercentage());
        report.setLowConfidence(s.logCount() < 7);
    }

    private void recordAssessment(Patient patient, Long patientId, WeeklyHealthReport report, WeeklySnapshot s) {
        // Update the existing assessment for this weekly report instead of always
        // inserting a new one, otherwise every recalculation (triggered on every
        // daily log create/edit/delete) creates a duplicate RiskAssessment row.
        RiskAssessment assessment = riskAssessmentRepository
                .findByWeeklyReportIdAndAssessmentType(report.getId(), "NEPHROPATHY_WEEKLY")
                .orElseGet(() -> RiskAssessment.builder()
                .patient(patient)
                .weeklyReportId(report.getId())
                .dailyHealthLogId(null)
                .assessmentType("NEPHROPATHY_WEEKLY")
                .build());

        assessment.setRiskLevel(s.level().name());
        assessment.setRiskPercentage(s.riskPercentage());
        assessment.setLowConfidence(s.logCount() < 7);
        assessment.setHypertensionScore(s.htnScore());
        assessment.setRecommendation(report.getRecommendation());
        assessment.setAssessedAt(LocalDateTime.now());
        final RiskAssessment savedAssessment = riskAssessmentRepository.save(assessment);

        report.setRiskAssessmentId(savedAssessment.getId());
        weeklyHealthReportRepository.save(report);

        // Same idea for the warning: update the existing one tied to this
        // assessment rather than spamming a new warning on every recalculation.
        Optional<RiskWarning> existingWarning = riskWarningRepository
                .findByRiskAssessmentIdAndRiskType(savedAssessment.getId(), "NEPHROPATHY_WEEKLY");

        if (s.level() != RiskLevel.LOW) {
            RiskWarning warning = existingWarning.orElseGet(() -> RiskWarning.builder()
                    .patient(patient)
                    .riskAssessmentId(savedAssessment.getId())
                    .dailyHealthLogId(null)
                    .riskType("NEPHROPATHY_WEEKLY")
                    .status("new")
                    .notified(false)
                    .createdAt(LocalDateTime.now())
                    .build());
            warning.setRiskLevel(s.level().name());
            warning.setRiskPercentage(s.riskPercentage());
            warning.setMessage(report.getRecommendation());
            riskWarningRepository.save(warning);
        } else {
            // Risk dropped back to LOW: remove any stale warning instead of
            // leaving it around or creating a fresh redundant one.
            existingWarning.ifPresent(riskWarningRepository::delete);
        }

        AiAnalysisLog aiLog = aiAnalysisLogRepository
                .findByWeeklyReportIdAndAnalysisType(report.getId(), "CKD_WEEKLY_LOGISTIC_V1")
                .orElseGet(() -> AiAnalysisLog.builder()
                .patientId(patientId)
                .dailyHealthLogId(null)
                .weeklyReportId(report.getId())
                .analysisType("CKD_WEEKLY_LOGISTIC_V1")
                .build());
        aiLog.setInputData("{\"age\":" + s.age() + ",\"avg_diastolic\":" + s.avgDiaForModel() + ",\"avg_blood_sugar\":" + s.avgBsForModel() + ",\"htn\":" + s.htn() + "}");
        aiLog.setOutputResult("{\"riskPercentage\":" + s.riskPct() + ",\"riskLevel\":\"" + s.level().name() + "\"}");
        aiLog.setRiskLevel(s.level().name());
        aiLog.setCreatedAt(LocalDateTime.now());
        aiAnalysisLogRepository.save(aiLog);
    }

    @Override
    @Transactional
    public WeeklyHealthReport generateWeeklyReport(Long patientId, LocalDate weekStart) {
        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null) {
            return null;
        }

        LocalDate weekEnd = weekStart.plusDays(6);
        WeeklySnapshot snapshot = computeSnapshot(patient, patientId, weekStart, weekEnd);

        WeeklyHealthReport report = WeeklyHealthReport.builder()
                .patient(patient)
                .weekStart(weekStart)
                .weekEnd(weekEnd)
                .riskAssessmentId(null)
                .createdAt(LocalDateTime.now())
                .build();
        applySnapshot(report, snapshot);
        report = weeklyHealthReportRepository.save(report);

        // 1. Thực hiện ghi nhận đánh giá cũ (bệnh thận...)
        recordAssessment(patient, patientId, report, snapshot);

        // 2. Tự động kích hoạt luồng đánh giá AI Tim Mạch cuối tuần mới
        processWeeklyEvaluation(patient, weekStart, weekEnd, report);

        return report;
    }

    @Override
    @Transactional
    public void recalculateIfExists(Long patientId, LocalDate weekStart) {
        weeklyHealthReportRepository.findByPatientIdAndWeekStart(patientId, weekStart).ifPresent(existing -> {
            try {
                Patient patient = patientRepository.findById(patientId).orElse(null);
                if (patient == null) {
                    return;
                }
                LocalDate weekEnd = weekStart.plusDays(6);
                WeeklySnapshot snapshot = computeSnapshot(patient, patientId, weekStart, weekEnd);
                applySnapshot(existing, snapshot);
                WeeklyHealthReport saved = weeklyHealthReportRepository.save(existing);

                // Cập nhật lại đánh giá cũ
                recordAssessment(patient, patientId, saved, snapshot);

                // Tái đánh giá lại AI tim mạch cho tuần này khi chỉ số nhật ký thay đổi
                processWeeklyEvaluation(patient, weekStart, weekEnd, saved);
            } catch (Exception e) {
                log.error("Lỗi khi tính toán lại báo cáo tuần: ", e);
            }
        });
    }

    // REQUIRES_NEW: đây là entry point được DailyHealthLogServiceImpl.createLog()/
    // updateLog() gọi trong try/catch. Nếu để mặc định (tham gia transaction của
    // caller) thì bất kỳ lỗi nào ở đây (thiếu cột DB, lỗi tính toán báo cáo tuần...)
    // sẽ đánh dấu rollback-only cho cả transaction lưu DailyHealthLog, gây
    // UnexpectedRollbackException dù exception đã được catch ở tầng trên.
    // CHẠY NỀN (@Async): trước đây DailyHealthLogServiceImpl chờ đồng bộ
    // báo cáo tuần xong mới trả kết quả cho người dùng, làm trang sửa nhật
    // ký bị chậm. Việc này an toàn để chạy nền vì bản ghi DailyHealthLog
    // đã được lưu (commit) trước khi hàm này được gọi.
    // Vì @Async nên tự try/catch ở đây, exception không còn bay ngược lên
    // được cho caller bắt như trước.
    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncWeeklyReport(Long patientId, LocalDate weekStart) {
        try {
            if (weeklyHealthReportRepository.existsByPatientIdAndWeekStart(patientId, weekStart)) {
                recalculateIfExists(patientId, weekStart);
                return;
            }

            long logCount = dailyHealthLogRepository.countByPatientIdAndLogDateBetween(patientId, weekStart, weekStart.plusDays(6));
            if (logCount >= 3) {
                generateWeeklyReport(patientId, weekStart);
            }
        } catch (Exception e) {
            log.error("[WeeklyReport] Đồng bộ báo cáo tuần thất bại cho patientId={}: {}", patientId, e.getMessage(), e);
        }
    }

    @Override
    public CustomRangeResult computeCustomRange(Long patientId, LocalDate from, LocalDate to) {
        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null) {
            return null;
        }

        WeeklySnapshot s = computeSnapshot(patient, patientId, from, to);

        return new CustomRangeResult(
                from,
                to,
                s.logCount(),
                s.avgBloodSugar(),
                s.avgSystolic(),
                s.avgDiastolic(),
                s.riskPercentage(),
                s.level().name(),
                ComplicationRiskServiceImpl.getRecommendation(s.level()),
                s.logCount() < 3
        );
    }

    /**
     * Hàm xử lý tự động tính toán trung bình cộng chỉ số tuần, kết hợp Profile
     * tĩnh và đồng bộ kết quả AI Tim mạch vào Database hệ thống.
     */
    @Transactional
    public void processWeeklyEvaluation(Patient patient, LocalDate weekStart, LocalDate weekEnd, WeeklyHealthReport report) {
        try {
            Map<String, Object> aiHeartResult = weeklyCardioAiService.calculateWeeklyHeartRisk(patient, weekStart, weekEnd);

            if (aiHeartResult != null && !aiHeartResult.isEmpty()) {
                Double riskPercentage = (Double) aiHeartResult.get("cardio_risk_percentage");
                String riskLevel = (String) aiHeartResult.get("risk_level");
                String summary = (String) aiHeartResult.get("summary");

                @SuppressWarnings("unchecked")
                List<String> adviceList = (List<String>) aiHeartResult.get("advice");
                String adviceText = (adviceList != null) ? String.join("\n• ", adviceList) : "";

                // Update the existing cardio assessment for this weekly report instead
                // of always inserting a new one (avoids duplicate rows on every
                // recalculation triggered by daily log create/edit/delete).
                RiskAssessment assessment = riskAssessmentRepository
                        .findByWeeklyReportIdAndAssessmentType(report.getId(), "WEEKLY_CARDIO_RISK")
                        .orElseGet(() -> RiskAssessment.builder()
                        .patient(patient)
                        .weeklyReportId(report.getId())
                        .assessmentType("WEEKLY_CARDIO_RISK")
                        .build());
                assessment.setRiskLevel(riskLevel);
                assessment.setRiskPercentage(BigDecimal.valueOf(riskPercentage != null ? riskPercentage : 0.0).setScale(2, RoundingMode.HALF_UP));
                assessment.setAiSummary(summary);
                assessment.setRecommendation(adviceText);
                assessment.setAssessedAt(LocalDateTime.now());
                riskAssessmentRepository.save(assessment);

                Optional<RiskWarning> existingWarning = riskWarningRepository
                        .findByRiskAssessmentIdAndRiskType(assessment.getId(), "WEEKLY_CARDIO_RISK");

                if (!"LOW".equalsIgnoreCase(riskLevel)) {
                    RiskWarning warning = existingWarning.orElseGet(() -> RiskWarning.builder()
                            .patient(patient)
                            .riskAssessmentId(assessment.getId())
                            .riskType("WEEKLY_CARDIO_RISK")
                            .status("new")
                            .notified(false)
                            .createdAt(LocalDateTime.now())
                            .build());
                    warning.setRiskLevel(riskLevel);
                    warning.setRiskPercentage(assessment.getRiskPercentage());
                    warning.setMessage("Cảnh báo nguy cơ tim mạch tuần: " + summary + "\nKhuyến nghị:\n• " + adviceText);
                    riskWarningRepository.save(warning);
                } else {
                    existingWarning.ifPresent(riskWarningRepository::delete);
                }

                String baseSummary = report.getAiSummary() != null && report.getAiSummary().contains(" | [AI Tim Mạch]: ")
                        ? report.getAiSummary().substring(0, report.getAiSummary().indexOf(" | [AI Tim Mạch]: "))
                        : report.getAiSummary();
                String combinedSummary = baseSummary + " | [AI Tim Mạch]: " + summary;
                report.setAiSummary(combinedSummary);
                weeklyHealthReportRepository.save(report);

                log.info("Đã đồng bộ đánh giá AI tim mạch tuần hoàn tất cho bệnh nhân id: {}", patient.getId());
            }
        } catch (Exception e) {
            log.error("Gặp lỗi trong tiến trình xử lý và đồng bộ kết quả AI tim mạch tuần: ", e);
        }

        // --- ĐỒNG BỘ ĐÁNH GIÁ AI ĐỘT QUỴ TUẦN (WEEKLY STROKE RISK ASSESSMENT) ---
        // Tổng hợp dữ liệu trong tuần để chạy đánh giá AI và đồng bộ kết quả nguy cơ đột quỵ tuần.
        try {
            // Gọi service AI để tính toán trung bình cộng chỉ số tuần và lấy kết quả dự đoán đột quỵ
            Map<String, Object> aiStrokeResult = weeklyStrokeAiService.calculateWeeklyStrokeRisk(patient, weekStart, weekEnd);

            if (aiStrokeResult != null && !aiStrokeResult.isEmpty()) {
                Double riskPercentage = null;
                if (aiStrokeResult.get("risk_percentage") != null) {
                    riskPercentage = Double.parseDouble(aiStrokeResult.get("risk_percentage").toString());
                }
                String rawRiskLevel = (String) aiStrokeResult.get("risk_level");

                // Mức độ mặc định
                String mappedLevel = "LOW";
                String strokeAdvice = "Chỉ số sức khỏe của bạn ở mức an toàn, nguy cơ đột quỵ thấp.";

                // Phân loại mức độ nguy cơ dựa trên nhãn trả về và xác suất tính toán được
                if (rawRiskLevel != null) {
                    String lowerLevel = rawRiskLevel.toLowerCase();
                    if (lowerLevel.contains("critical") || (riskPercentage != null && riskPercentage >= 75)) {
                        mappedLevel = "CRITICAL";
                        strokeAdvice = "🚨 Nguy cơ đột quỵ rất cao (Nguy hiểm)! Cần tham vấn bác sĩ ngay để kiểm soát huyết áp và các chỉ số sức khỏe.";
                    } else if (lowerLevel.contains("high") || (riskPercentage != null && riskPercentage >= 50)) {
                        mappedLevel = "HIGH";
                        strokeAdvice = "⚠️ Nguy cơ đột quỵ cao! Bạn nên điều chỉnh chế độ sinh hoạt, hạn chế các chất kích thích và theo dõi huyết áp thường xuyên.";
                    } else if (lowerLevel.contains("medium") || lowerLevel.contains("moderate") || (riskPercentage != null && riskPercentage >= 25)) {
                        mappedLevel = "MEDIUM";
                        strokeAdvice = "⚡ Nguy cơ đột quỵ ở mức trung bình. Hãy chú ý giữ thói quen rèn luyện thể thao đều đặn.";
                    }
                }

                // Cập nhật kết quả đánh giá nguy cơ đột quỵ tuần cho báo cáo tuần này (tránh tạo trùng lặp dòng mới)
                RiskAssessment assessment = riskAssessmentRepository
                        .findByWeeklyReportIdAndAssessmentType(report.getId(), "WEEKLY_STROKE_RISK")
                        .orElseGet(() -> RiskAssessment.builder()
                        .patient(patient)
                        .weeklyReportId(report.getId())
                        .assessmentType("WEEKLY_STROKE_RISK")
                        .build());
                assessment.setRiskLevel(mappedLevel);
                BigDecimal finalPct = BigDecimal.valueOf(riskPercentage != null ? riskPercentage : 0.0).setScale(2, RoundingMode.HALF_UP);
                assessment.setRiskPercentage(finalPct);
                assessment.setRecommendation(strokeAdvice);
                assessment.setAssessedAt(LocalDateTime.now());
                riskAssessmentRepository.save(assessment);

                // Đồng bộ hóa cảnh báo khẩn cấp nếu mức nguy cơ không phải mức AN TOÀN (LOW)
                Optional<RiskWarning> existingWarning = riskWarningRepository
                        .findByRiskAssessmentIdAndRiskType(assessment.getId(), "WEEKLY_STROKE_RISK");

                if (!"LOW".equalsIgnoreCase(mappedLevel)) {
                    RiskWarning warning = existingWarning.orElseGet(() -> RiskWarning.builder()
                            .patient(patient)
                            .riskAssessmentId(assessment.getId())
                            .riskType("WEEKLY_STROKE_RISK")
                            .status("new")
                            .notified(false)
                            .createdAt(LocalDateTime.now())
                            .build());
                    warning.setRiskLevel(mappedLevel);
                    warning.setRiskPercentage(finalPct);
                    warning.setMessage("Cảnh báo nguy cơ đột quỵ tuần: " + mappedLevel + "\nKhuyến nghị:\n• " + strokeAdvice);
                    riskWarningRepository.save(warning);
                } else {
                    // Nếu sau khi cập nhật dữ liệu, mức nguy cơ giảm về LOW -> xóa cảnh báo cũ
                    existingWarning.ifPresent(riskWarningRepository::delete);
                }

                // Lưu nhật ký phân tích AI thô phục vụ kiểm tra hệ thống
                AiAnalysisLog strokeAiLog = AiAnalysisLog.builder()
                        .patientId(patient.getId())
                        .weeklyReportId(report.getId())
                        .analysisType("STROKE_WEEKLY_V1")
                        .inputData("{"
                                + "\"patientId\":" + patient.getId()
                                + ",\"weekStart\":\"" + weekStart + "\""
                                + ",\"weekEnd\":\"" + weekEnd + "\""
                                + "}")
                        .outputResult(aiStrokeResult.toString())
                        .riskLevel(mappedLevel)
                        .createdAt(LocalDateTime.now())
                        .build();
                aiAnalysisLogRepository.save(strokeAiLog);

                log.info("Đã đồng bộ đánh giá AI đột quỵ tuần hoàn tất cho bệnh nhân id: {}", patient.getId());
            }
        } catch (Exception e) {
            log.error("Gặp lỗi trong tiến trình xử lý và đồng bộ kết quả AI đột quỵ tuần: ", e);
        }
    }
}
