package fpt.swp391.GlucoTrackAlert.service.impl;

import fpt.swp391.GlucoTrackAlert.enums.RiskLevel;
import fpt.swp391.GlucoTrackAlert.model.DailyHealthLog;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.model.risk.AiAnalysisLog;
import fpt.swp391.GlucoTrackAlert.model.risk.RiskAssessment;
import fpt.swp391.GlucoTrackAlert.model.risk.RiskWarning;
import fpt.swp391.GlucoTrackAlert.repository.DailyHealthLogRepository;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.repository.risk.AiAnalysisLogRepository;
import fpt.swp391.GlucoTrackAlert.repository.risk.RiskAssessmentRepository;
import fpt.swp391.GlucoTrackAlert.repository.risk.RiskWarningRepository;
import fpt.swp391.GlucoTrackAlert.service.ComplicationRiskService;
import fpt.swp391.GlucoTrackAlert.service.RiskModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplicationRiskServiceImpl implements ComplicationRiskService {

    private final PatientRepository patientRepository;
    private final DailyHealthLogRepository dailyHealthLogRepository;
    private final RiskModelService riskModelService;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final RiskWarningRepository riskWarningRepository;
    private final AiAnalysisLogRepository aiAnalysisLogRepository;

    @Override
    public void assessPatient(Long patientId, Long dailyHealthLogId) {
        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null) return;

        DailyHealthLog log = dailyHealthLogRepository.findById(dailyHealthLogId).orElse(null);
        if (log == null) return;

        Double patientAgeObj = patient.getAge() != null ? patient.getAge().doubleValue() : null;
        double age = patientAgeObj != null ? patientAgeObj : 50.0;
        boolean htn = Boolean.TRUE.equals(patient.getHypertensionDiagnosed());
        Double diastolicObj = log.getDiastolic() != null ? log.getDiastolic().doubleValue() : null;
        double diastolic = diastolicObj != null ? diastolicObj : 80.0;
        Double bloodSugarObj = log.getBloodSugar() != null ? log.getBloodSugar().doubleValue() : null;
        double bloodSugar = bloodSugarObj != null ? bloodSugarObj : 120.0;

        boolean lowConfidenceFlag = (patientAgeObj == null) || (diastolicObj == null) || (bloodSugarObj == null);

        double riskPct = riskModelService.predictRiskPercentage(age, diastolic, bloodSugar, htn);
        RiskLevel level = riskModelService.mapToRiskLevel(riskPct);
        BigDecimal riskPercentage = BigDecimal.valueOf(riskPct).setScale(2, RoundingMode.HALF_UP);

        String recommendation = getRecommendation(level);

        RiskAssessment.RiskAssessmentBuilder assessmentBuilder = RiskAssessment.builder()
            .patient(patient)
            .dailyHealthLogId(dailyHealthLogId)
            .assessmentType("NEPHROPATHY")
            .riskLevel(level.name())
            .riskPercentage(riskPercentage)
            .recommendation(recommendation)
            .assessedAt(LocalDateTime.now());
        if (lowConfidenceFlag) assessmentBuilder.lowConfidence(true);
        RiskAssessment assessment = assessmentBuilder.build();
        RiskAssessment savedAssessment = riskAssessmentRepository.save(assessment);

        if (level != RiskLevel.LOW) {
            RiskWarning warning = RiskWarning.builder()
                    .patient(patient)
                    .riskAssessmentId(savedAssessment.getId())
                    .dailyHealthLogId(dailyHealthLogId)
                    .riskType("NEPHROPATHY")
                    .riskLevel(level.name())
                    .riskPercentage(riskPercentage)
                    .message(recommendation)
                    .status("new")
                    .notified(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            riskWarningRepository.save(warning);
        }

        AiAnalysisLog aiLog = AiAnalysisLog.builder()
                .patientId(patientId)
                .dailyHealthLogId(dailyHealthLogId)
                .analysisType("CKD_LOGISTIC_REGRESSION_V1")
                .inputData("{\"age\":" + age + ",\"diastolic\":" + diastolic +
                        ",\"blood_sugar\":" + bloodSugar + ",\"hypertension\":" + htn + "}")
                .outputResult("{\"riskPercentage\":" + riskPct + ",\"riskLevel\":\"" + level.name() + "\"}")
                .riskLevel(level.name())
                .createdAt(LocalDateTime.now())
                .build();
        aiAnalysisLogRepository.save(aiLog);
    }

    private String getRecommendation(RiskLevel level) {
        return switch (level) {
            case LOW -> "Nguy cơ thấp. Duy trì lối sống lành mạnh, kiểm tra định kỳ 6 tháng/lần.";
            case MEDIUM -> "Nguy cơ trung bình. Kiểm soát đường huyết và huyết áp, tái khám 3 tháng/lần.";
            case HIGH -> "Nguy cơ cao. Cần tư vấn bác sĩ chuyên khoa thận sớm, tái khám 1 tháng/lần.";
            case CRITICAL -> "Nguy cơ rất cao. Cần đến cơ sở y tế ngay để được đánh giá chức năng thận.";
        };
    }
}