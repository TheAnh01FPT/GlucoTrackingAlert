package fpt.swp391.GlucoTrackAlert.service.impl;

import fpt.swp391.GlucoTrackAlert.enums.RiskLevel;
import fpt.swp391.GlucoTrackAlert.model.healthlog.DailyHealthLog;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.model.risk.AiAnalysisLog;
import fpt.swp391.GlucoTrackAlert.model.risk.RiskAssessment;
import fpt.swp391.GlucoTrackAlert.model.risk.RiskWarning;
import fpt.swp391.GlucoTrackAlert.repository.healthlog.DailyHealthLogRepository;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.repository.risk.AiAnalysisLogRepository;
import fpt.swp391.GlucoTrackAlert.repository.risk.RiskAssessmentRepository;
import fpt.swp391.GlucoTrackAlert.repository.risk.RiskWarningRepository;
import fpt.swp391.GlucoTrackAlert.service.ComplicationRiskService;
import fpt.swp391.GlucoTrackAlert.service.RiskModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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
    private final RestTemplate restTemplate;

    // Chạy trong transaction RIÊNG (REQUIRES_NEW): nếu bước đánh giá rủi ro
    // này lỗi (vd. thiếu cột DB như 'hypertension_score' từng gặp), nó chỉ
    // tự rollback phần của nó, không kéo theo rollback transaction chính đang
    // lưu DailyHealthLog ở DailyHealthLogServiceImpl.createLog()/updateLog().
    // CHẠY NỀN (@Async): đây là phần CHẬM NHẤT trong luồng sửa/tạo nhật ký
    // sức khỏe, vì gọi HTTP đồng bộ sang microservice Python để dự đoán
    // nguy cơ đột quỵ (connect timeout 3s + read timeout 8s, xem
    // WebMvcConfig). An toàn để chạy nền vì bản ghi DailyHealthLog đã được
    // lưu (commit) trước khi hàm này được gọi.
    // Vì @Async nên tự try/catch bọc toàn bộ thân hàm, exception không còn
    // bay ngược lên được cho DailyHealthLogServiceImpl bắt như trước.
    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void assessPatient(Long patientId, Long dailyHealthLogId) {
        try {
            doAssessPatient(patientId, dailyHealthLogId);
        } catch (Exception e) {
            log.error("[ComplicationRisk] Đánh giá biến chứng thất bại cho patientId={}: {}", patientId, e.getMessage(), e);
        }
    }

    private void doAssessPatient(Long patientId, Long dailyHealthLogId) {
        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null) {
            return;
        }

        DailyHealthLog dailyLog = dailyHealthLogRepository.findById(dailyHealthLogId).orElse(null);
        if (dailyLog == null) {
            return;
        }

        Double patientAgeObj = patient.getAge() != null ? patient.getAge().doubleValue() : null;
        double age = patientAgeObj != null ? patientAgeObj : 50.0;
        boolean htn = Boolean.TRUE.equals(patient.getHypertension());
        Double diastolicObj = dailyLog.getDiastolic() != null ? dailyLog.getDiastolic().doubleValue() : null;
        double diastolic = diastolicObj != null ? diastolicObj : 80.0;
        Double bloodSugarObj = dailyLog.getBloodSugar() != null ? dailyLog.getBloodSugar().doubleValue() : null;
        // default in mmol/L (approx 6.7 mmol/L ~ 120 mg/dL)
        double bloodSugar = bloodSugarObj != null ? bloodSugarObj : 6.7;

        boolean lowConfidenceFlag = (patientAgeObj == null) || (diastolicObj == null) || (bloodSugarObj == null);

        // Compute hypertension score for single-log assessment
        double htnScore;
        if (diastolicObj != null) {
            double clamped = Math.max(50.0, Math.min(180.0, diastolic));
            // normalize into 0..1 range roughly between 50 and 180
            htnScore = (clamped - 50.0) / (180.0 - 50.0);
            if (Boolean.TRUE.equals(patient.getHypertension())) {
                htnScore = Math.max(htnScore, 0.3);
            }
        } else {
            htnScore = Boolean.TRUE.equals(patient.getHypertension()) ? 0.6 : 0.0;
        }

        double riskPct = riskModelService.predictRiskPercentage(age, diastolic, bloodSugar, htnScore);
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
                .hypertensionScore(htnScore)
                .assessedAt(LocalDateTime.now());
        if (lowConfidenceFlag) {
            assessmentBuilder.lowConfidence(true);
        }
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
                .inputData("{\"age\":" + age + ",\"diastolic\":" + diastolic
                        + ",\"blood_sugar\":" + bloodSugar + ",\"hypertension_score\":" + htnScore + "}")
                .outputResult("{\"riskPercentage\":" + riskPct + ",\"riskLevel\":\"" + level.name() + "\"}")
                .riskLevel(level.name())
                .createdAt(LocalDateTime.now())
                .build();
        aiAnalysisLogRepository.save(aiLog);

        // --- PHẦN 2: ĐÁNH GIÁ NGUY CƠ ĐỘT QUỴ THEO NGÀY (DAILY STROKE RISK ASSESSMENT) ---
        // Sử dụng mô hình Random Forest (Python API) để dự đoán nguy cơ đột quỵ dựa trên các chỉ số đường huyết và hồ sơ bệnh nhân.
        try {
            // Ánh xạ thuộc tính Giới tính của bệnh nhân sang dạng số (Male: 0, Female: 1, Other: -1) theo đặc tả của mô hình AI.
            int genderVal = 0;
            if (patient.getGender() != null) {
                String g = patient.getGender().toLowerCase();
                if (g.contains("fem") || g.contains("nữ")) {
                    genderVal = 1;
                } else if (g.contains("oth") || g.contains("khác")) {
                    genderVal = -1;
                }
            }

            // Lấy độ tuổi của bệnh nhân, mặc định là 50 tuổi nếu thông tin chưa đầy đủ.
            double ageVal = patient.getAge() != null ? patient.getAge().doubleValue() : 50.0;
            // Ánh xạ tiền sử tăng huyết áp (Hypertension: Có -> 1, Không -> 0).
            int hyperVal = Boolean.TRUE.equals(patient.getHypertension()) ? 1 : 0;
            // Ánh xạ tiền sử bệnh tim mạch (Heart Disease: Có -> 1, Không -> 0).
            int heartVal = Boolean.TRUE.equals(patient.getHeartDisease()) ? 1 : 0;

            // Ánh xạ Loại hình công việc (Work Type) thành giá trị số tương ứng với dữ liệu huấn luyện mô hình.
            int workVal = 0; // Mặc định là Private: 0
            if (patient.getWorkType() != null) {
                String w = patient.getWorkType();
                if (w.equalsIgnoreCase("Self-employed")) {
                    workVal = 1;
                } else if (w.equalsIgnoreCase("Govt_job")) {
                    workVal = 2;
                } else if (w.equalsIgnoreCase("children")) {
                    workVal = -1;
                } else if (w.equalsIgnoreCase("Never_worked")) {
                    workVal = -2;
                }
            }

            // Ánh xạ Loại hình nơi cư trú (Residence Type): Rural -> 0, Urban/Khác -> 1
            int resVal = 1;
            if (patient.getResidenceType() != null && patient.getResidenceType().equalsIgnoreCase("Rural")) {
                resVal = 0;
            }

            // Lấy chỉ số BMI, mặc định là 25.0 nếu không có dữ liệu.
            double bmiVal = patient.getBmi() != null ? patient.getBmi().doubleValue() : 25.0;

            // Ánh xạ tình trạng hút thuốc (Smoking Status): never -> 0, formerly -> 1, smokes -> 2, unknown/khác -> -1
            int smokeVal = -1;
            if (patient.getSmokingStatus() != null) {
                String s = patient.getSmokingStatus();
                if (s.equalsIgnoreCase("never smoked")) {
                    smokeVal = 0;
                } else if (s.equalsIgnoreCase("formerly smoked")) {
                    smokeVal = 1;
                } else if (s.equalsIgnoreCase("smokes")) {
                    smokeVal = 2;
                }
            }

            // Chuyển đổi chỉ số đường huyết từ đơn vị mmol/L sang mg/dL phục vụ cho mô hình dự báo đột quỵ.
            double glucMgDl = bloodSugar * 18.0;

            // Đóng gói Payload gửi yêu cầu đến server AI Python
            Map<String, Object> payload = new HashMap<>();
            payload.put("gender", genderVal);
            payload.put("age", ageVal);
            payload.put("hypertension", hyperVal);
            payload.put("heart_disease", heartVal);
            payload.put("work_type", workVal);
            payload.put("Residence_type", resVal);
            payload.put("avg_glucose_level", glucMgDl);
            payload.put("bmi", bmiVal);
            payload.put("smoking_status", smokeVal);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Thực hiện cuộc gọi REST POST tới API Python AI Đột quỵ
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "http://127.0.0.1:8000/predict",
                    new HttpEntity<>(payload, headers),
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Double strokePct = null;
                if (body.get("risk_percentage") != null) {
                    strokePct = Double.parseDouble(body.get("risk_percentage").toString());
                }
                String rawRiskLevel = (String) body.get("risk_level");

                // Quy đổi mức độ nguy cơ và đưa ra lời khuyên phù hợp
                String mappedLevel = "LOW";
                String strokeAdvice = "Chỉ số sức khỏe của bạn ở mức an toàn, nguy cơ đột quỵ thấp.";

                if (rawRiskLevel != null) {
                    String lowerLevel = rawRiskLevel.toLowerCase();
                    if (lowerLevel.contains("critical") || (strokePct != null && strokePct >= 75)) {
                        mappedLevel = "CRITICAL";
                        strokeAdvice = "🚨 Nguy cơ đột quỵ rất cao (Nguy hiểm)! Cần tham vấn bác sĩ ngay để kiểm soát huyết áp và các chỉ số sức khỏe.";
                    } else if (lowerLevel.contains("high") || (strokePct != null && strokePct >= 50)) {
                        mappedLevel = "HIGH";
                        strokeAdvice = "⚠️ Nguy cơ đột quỵ cao! Bạn nên điều chỉnh chế độ sinh hoạt, hạn chế các chất kích thích và theo dõi huyết áp thường xuyên.";
                    } else if (lowerLevel.contains("medium") || lowerLevel.contains("moderate") || (strokePct != null && strokePct >= 25)) {
                        mappedLevel = "MEDIUM";
                        strokeAdvice = "⚡ Nguy cơ đột quỵ ở mức trung bình. Hãy chú ý giữ thói quen rèn luyện thể thao đều đặn.";
                    }
                }

                BigDecimal finalPct = BigDecimal.valueOf(strokePct != null ? strokePct : 0.0).setScale(2, RoundingMode.HALF_UP);

                // Lưu kết quả đánh giá vào bảng risk_assessments
                RiskAssessment strokeAssessment = RiskAssessment.builder()
                        .patient(patient)
                        .dailyHealthLogId(dailyHealthLogId)
                        .assessmentType("STROKE")
                        .riskLevel(mappedLevel)
                        .riskPercentage(finalPct)
                        .recommendation(strokeAdvice)
                        .assessedAt(LocalDateTime.now())
                        .lowConfidence(lowConfidenceFlag)
                        .build();
                strokeAssessment = riskAssessmentRepository.save(strokeAssessment);

                // Nếu mức nguy cơ vượt quá mức LOW (Medium, High, Critical) -> Tạo bản ghi cảnh báo khẩn cấp trong risk_warnings
                if (!"LOW".equals(mappedLevel)) {
                    RiskWarning strokeWarning = RiskWarning.builder()
                            .patient(patient)
                            .riskAssessmentId(strokeAssessment.getId())
                            .dailyHealthLogId(dailyHealthLogId)
                            .riskType("STROKE")
                            .riskLevel(mappedLevel)
                            .riskPercentage(finalPct)
                            .message(strokeAdvice)
                            .status("new")
                            .notified(false)
                            .createdAt(LocalDateTime.now())
                            .build();
                    riskWarningRepository.save(strokeWarning);
                }

                // Ghi nhật ký kiểm toán AI (AiAnalysisLog) phục vụ cho việc quản trị
                AiAnalysisLog strokeAiLog = AiAnalysisLog.builder()
                        .patientId(patientId)
                        .dailyHealthLogId(dailyHealthLogId)
                        .analysisType("STROKE_DAILY_V1")
                        .inputData(payload.toString())
                        .outputResult(body.toString())
                        .riskLevel(mappedLevel)
                        .createdAt(LocalDateTime.now())
                        .build();
                aiAnalysisLogRepository.save(strokeAiLog);

                log.info("Đánh giá đột quỵ theo ngày thành công cho bệnh nhân {}: {}%", patientId, finalPct);
            }
        } catch (Exception e) {
            log.error("Lỗi khi đánh giá đột quỵ theo ngày cho bệnh nhân {}: {}", patientId, e.getMessage());
        }
    }

    public static String getRecommendation(RiskLevel level) {
        return switch (level) {
            case LOW ->
                "Nguy cơ thấp. Duy trì lối sống lành mạnh, kiểm tra định kỳ 6 tháng/lần.";
            case MEDIUM ->
                "Nguy cơ trung bình. Kiểm soát đường huyết và huyết áp, tái khám 3 tháng/lần.";
            case HIGH ->
                "Nguy cơ cao. Cần tư vấn bác sĩ chuyên khoa thận sớm, tái khám 1 tháng/lần.";
            case CRITICAL ->
                "Nguy cơ rất cao. Cần đến cơ sở y tế ngay để được đánh giá chức năng thận.";
        };
    }
}
