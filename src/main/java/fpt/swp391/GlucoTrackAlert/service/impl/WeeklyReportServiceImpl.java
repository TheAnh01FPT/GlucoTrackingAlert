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
import fpt.swp391.GlucoTrackAlert.service.impl.ComplicationRiskServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    // Holds the computed metrics for a patient/week so the calculation logic can be
    // shared between "create new report" and "update existing report" paths.
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

        boolean htn = Boolean.TRUE.equals(patient.getHypertensionDiagnosed()) || highBpDays >= 3;

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
        // set risk percentage and lowConfidence based on snapshot
        report.setRiskPercentage(s.riskPercentage());
        report.setLowConfidence(s.logCount() < 7);
    }

    // Creates the RiskAssessment / RiskWarning / AiAnalysisLog audit trail rows that go
    // along with a (re)computed report. Always called with `report` already persisted
    // (has a non-null id).
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

        recordAssessment(patient, patientId, report, snapshot);
        return report;
    }

    /**
     * Recomputes an already-existing weekly report IN PLACE (same row/id) instead of
     * inserting a new one and deleting the old one.
     *
     * Root cause of the bug being fixed here: the previous implementation called
     * generateWeeklyReport() - annotated @Transactional(propagation = REQUIRES_NEW) -
     * via a plain `this` call from inside the same class (recalculateIfExists ->
     * generateWeeklyReport). Spring's @Transactional only intercepts calls that go
     * through the proxy, so that self-invocation silently ignored REQUIRES_NEW and ran
     * in the SAME transaction/Hibernate Session as the caller (createLog/updateLog).
     * When the insert of the "new" report then failed (duplicate patient+week, since
     * the old row hadn't been deleted yet), the exception was swallowed by the catch
     * block, but the Session was already corrupted - the very next flush (e.g. while
     * building the response) threw:
     *   "null id in ... WeeklyHealthReport entry (don't flush the Session after an
     *   exception occurs)"
     * Updating the existing row in place removes both the duplicate-row race and the
     * need for a nested transaction/self-invocation altogether.
     */
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
                recordAssessment(patient, patientId, saved, snapshot);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
