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
import fpt.swp391.GlucoTrackAlert.service.cardioai.WeeklyCardioAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    // Đã tiêm thêm Service tim mạch tuần vào đây để giải quyết lỗi "Cannot resolve symbol"
    private final WeeklyCardioAiService weeklyCardioAiService;

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
            boolean htn
    ) {}

    private WeeklySnapshot computeSnapshot(Patient patient, Long patientId, LocalDate weekStart, LocalDate weekEnd) {
        List<DailyHealthLog> logs = dailyHealthLogRepository.findByPatientIdAndLogDateBetweenOrderByLogDate(patientId, weekStart, weekEnd);

        BigDecimal avgBloodSugar = null;
        BigDecimal avgSystolic = null;
        BigDecimal avgDiastolic = null;

        double sumBs = 0; int cntBs = 0; double sumSys = 0; int cntSys = 0; double sumDia = 0; int cntDia = 0;
        for (DailyHealthLog l : logs) {
            if (l.getBloodSugar() != null) { sumBs += l.getBloodSugar().doubleValue(); cntBs++; }
            if (l.getSystolic() != null) { sumSys += l.getSystolic().doubleValue(); cntSys++; }
            if (l.getDiastolic() != null) { sumDia += l.getDiastolic().doubleValue(); cntDia++; }
        }
        if (cntBs>0) avgBloodSugar = BigDecimal.valueOf(sumBs / cntBs).setScale(2, RoundingMode.HALF_UP);
        if (cntSys>0) avgSystolic = BigDecimal.valueOf(sumSys / cntSys).setScale(2, RoundingMode.HALF_UP);
        if (cntDia>0) avgDiastolic = BigDecimal.valueOf(sumDia / cntDia).setScale(2, RoundingMode.HALF_UP);

        Optional<fpt.swp391.GlucoTrackAlert.model.HealthThreshold> systolicThreshold = healthThresholdService.resolveThreshold(patientId, patient.getPatientType(), fpt.swp391.GlucoTrackAlert.enums.MetricType.SYSTOLIC);
        Optional<fpt.swp391.GlucoTrackAlert.model.HealthThreshold> diastolicThreshold = healthThresholdService.resolveThreshold(patientId, patient.getPatientType(), fpt.swp391.GlucoTrackAlert.enums.MetricType.DIASTOLIC);

        long highBpDays = logs.stream().filter(log -> {
            boolean sysHigh = systolicThreshold.isPresent() && log.getSystolic() != null && log.getSystolic().doubleValue() >= systolicThreshold.get().getNormalMax().doubleValue();
            boolean diaHigh = diastolicThreshold.isPresent() && log.getDiastolic() != null && log.getDiastolic().doubleValue() >= diastolicThreshold.get().getNormalMax().doubleValue();
            return sysHigh || diaHigh;
        }).count();

        boolean htn = Boolean.TRUE.equals(patient.getHypertension()) || highBpDays >= 3;

        double age = patient.getAge() != null ? patient.getAge().doubleValue() : 50.0;
        double avgDiaForModel = avgDiastolic != null ? avgDiastolic.doubleValue() : 80.0;
        double avgBsForModel = avgBloodSugar != null ? avgBloodSugar.doubleValue() : 6.0;

        double riskPct = riskModelService.predictRiskPercentage(age, avgDiaForModel, avgBsForModel, htn);
        RiskLevel level = riskModelService.mapToRiskLevel(riskPct);
        BigDecimal riskPercentage = BigDecimal.valueOf(riskPct).setScale(2, RoundingMode.HALF_UP);

        return new WeeklySnapshot(avgBloodSugar, avgSystolic, avgDiastolic, logs.size(), level, riskPct, riskPercentage, age, avgDiaForModel, avgBsForModel, htn);
    }

    private void applySnapshot(WeeklyHealthReport report, WeeklySnapshot s) {
        report.setAverageBloodSugar(s.avgBloodSugar());
        report.setAverageSystolic(s.avgSystolic());
        report.setAverageDiastolic(s.avgDiastolic());
        report.setHealthStatus(s.level().name());
        report.setRecommendation(ComplicationRiskServiceImpl.getRecommendation(s.level()));
        report.setAiSummary("Tổng hợp " + s.logCount() + " log, đường huyết TB: " + (s.avgBloodSugar()!=null?s.avgBloodSugar():"null") + " mmol/L, huyết áp TB: " + (s.avgSystolic()!=null?s.avgSystolic():"null") + "/" + (s.avgDiastolic()!=null?s.avgDiastolic():"null"));
        report.setRiskPercentage(s.riskPercentage());
        report.setLowConfidence(s.logCount() < 7);
    }

    private void recordAssessment(Patient patient, Long patientId, WeeklyHealthReport report, WeeklySnapshot s) {
        RiskAssessment assessment = RiskAssessment.builder()
                .patient(patient)
                .weeklyReportId(report.getId())
                .dailyHealthLogId(null)
                .assessmentType("NEPHROPATHY_WEEKLY")
                .riskLevel(s.level().name())
                .riskPercentage(s.riskPercentage())
                .lowConfidence(s.logCount() < 7)
                .recommendation(report.getRecommendation())
                .assessedAt(LocalDateTime.now())
                .build();
        assessment = riskAssessmentRepository.save(assessment);

        report.setRiskAssessmentId(assessment.getId());
        weeklyHealthReportRepository.save(report);

        if (s.level() != RiskLevel.LOW) {
            RiskWarning warning = RiskWarning.builder()
                    .patient(patient)
                    .riskAssessmentId(assessment.getId())
                    .dailyHealthLogId(null)
                    .riskType("NEPHROPATHY_WEEKLY")
                    .riskLevel(s.level().name())
                    .riskPercentage(s.riskPercentage())
                    .message(report.getRecommendation())
                    .status("new")
                    .notified(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            riskWarningRepository.save(warning);
        }

        AiAnalysisLog aiLog = AiAnalysisLog.builder()
                .patientId(patientId)
                .dailyHealthLogId(null)
                .weeklyReportId(report.getId())
                .analysisType("CKD_WEEKLY_LOGISTIC_V1")
                .inputData("{\"age\":"+s.age()+",\"avg_diastolic\":"+s.avgDiaForModel()+",\"avg_blood_sugar\":"+s.avgBsForModel()+",\"htn\":"+s.htn()+"}")
                .outputResult("{\"riskPercentage\":"+s.riskPct()+",\"riskLevel\":\""+s.level().name()+"\"}")
                .riskLevel(s.level().name())
                .createdAt(LocalDateTime.now())
                .build();
        aiAnalysisLogRepository.save(aiLog);
    }

    @Override
    @Transactional
    public WeeklyHealthReport generateWeeklyReport(Long patientId, LocalDate weekStart) {
        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null) return null;

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
                if (patient == null) return;
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

    /**
     * Hàm xử lý tự động tính toán trung bình cộng chỉ số tuần, kết hợp Profile tĩnh
     * và đồng bộ kết quả AI Tim mạch vào Database hệ thống.
     */
    @Transactional
    public void processWeeklyEvaluation(Patient patient, LocalDate weekStart, LocalDate weekEnd, WeeklyHealthReport report) {
        try {
            // Gọi sang file WeeklyCardioAiService để tính toán trung bình nhật ký tuần và giao tiếp Flask AI
            Map<String, Object> aiHeartResult = weeklyCardioAiService.calculateWeeklyHeartRisk(patient, weekStart, weekEnd);

            if (aiHeartResult != null && !aiHeartResult.isEmpty()) {
                Double riskPercentage = (Double) aiHeartResult.get("cardio_risk_percentage");
                String riskLevel = (String) aiHeartResult.get("risk_level");
                String summary = (String) aiHeartResult.get("summary");

                @SuppressWarnings("unchecked")
                List<String> adviceList = (List<String>) aiHeartResult.get("advice");
                String adviceText = (adviceList != null) ? String.join("\n• ", adviceList) : "";

                // Bước 1: Lưu kết quả phân tích AI tim mạch chi tiết vào bảng risk_assessments
                RiskAssessment assessment = RiskAssessment.builder()
                        .patient(patient)
                        .weeklyReportId(report.getId()) // Liên kết trực tiếp chặt chẽ với báo cáo tuần hiện tại
                        .assessmentType("WEEKLY_CARDIO_RISK")
                        .riskLevel(riskLevel)
                        .riskPercentage(BigDecimal.valueOf(riskPercentage != null ? riskPercentage : 0.0).setScale(2, RoundingMode.HALF_UP))
                        .aiSummary(summary)
                        .recommendation(adviceText)
                        .assessedAt(LocalDateTime.now())
                        .build();
                riskAssessmentRepository.save(assessment);

                // Bước 2: Tạo cảnh báo đẩy về bảng risk_warnings nếu mức độ nguy cơ vượt ngưỡng an toàn (Khác LOW)
                if (!"LOW".equalsIgnoreCase(riskLevel)) {
                    RiskWarning warning = RiskWarning.builder()
                            .patient(patient)
                            .riskAssessmentId(assessment.getId())
                            .riskType("WEEKLY_CARDIO_RISK")
                            .riskLevel(riskLevel)
                            .riskPercentage(assessment.getRiskPercentage())
                            .message("Cảnh báo nguy cơ tim mạch tuần: " + summary + "\nKhuyến nghị:\n• " + adviceText)
                            .status("new")
                            .notified(false)
                            .createdAt(LocalDateTime.now())
                            .build();
                    riskWarningRepository.save(warning);
                }

                // Bước 3: Đồng bộ cập nhật thêm tóm tắt tim mạch này vào thông tin chung của WeeklyHealthReport
                String combinedSummary = report.getAiSummary() + " | [AI Tim Mạch]: " + summary;
                report.setAiSummary(combinedSummary);
                weeklyHealthReportRepository.save(report);

                log.info("Đã đồng bộ đánh giá AI tim mạch tuần hoàn tất cho bệnh nhân id: {}", patient.getId());
            }
        } catch (Exception e) {
            log.error("Gặp lỗi trong tiến trình xử lý và đồng bộ kết quả AI tim mạch tuần: ", e);
        }
    }
}