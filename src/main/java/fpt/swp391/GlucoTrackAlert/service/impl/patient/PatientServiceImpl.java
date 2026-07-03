package fpt.swp391.GlucoTrackAlert.service.impl.patient;

import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileRequest;
import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileResponse;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.repository.DailyHealthLogRepository;
import fpt.swp391.GlucoTrackAlert.repository.risk.WeeklyHealthReportRepository;
import fpt.swp391.GlucoTrackAlert.model.DailyHealthLog;
import fpt.swp391.GlucoTrackAlert.model.risk.WeeklyHealthReport;
import fpt.swp391.GlucoTrackAlert.service.patient.PatientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

@Service
public class PatientServiceImpl implements PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final DailyHealthLogRepository dailyHealthLogRepository;
    private final WeeklyHealthReportRepository weeklyHealthReportRepository;
    private final RestTemplate restTemplate;

    @Autowired
    public PatientServiceImpl(PatientRepository patientRepository,
                              UserRepository userRepository,
                              DailyHealthLogRepository dailyHealthLogRepository,
                              WeeklyHealthReportRepository weeklyHealthReportRepository) {
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.dailyHealthLogRepository = dailyHealthLogRepository;
        this.weeklyHealthReportRepository = weeklyHealthReportRepository;
        this.restTemplate = new RestTemplate();
    }

    @Override
    @Transactional(readOnly = true)
    public PatientProfileResponse getProfileByUserId(Long userId) {
        Patient patient = patientRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Patient profile not found for user ID: " + userId));
        return mapToResponse(patient);
    }

    @Override
    @Transactional
    public PatientProfileResponse createProfile(PatientProfileRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + request.getUserId()));

        if (patientRepository.findByUserId(request.getUserId()).isPresent()) {
            throw new RuntimeException("Patient profile already exists for user ID: " + request.getUserId());
        }

        boolean isPregnantVal = false;
        if ("Nữ".equalsIgnoreCase(request.getGender()) && request.getIsPregnant() != null) {
            isPregnantVal = request.getIsPregnant();
        }

        // --- AUTOMATIC MAPPING FOR SMOKING STATUS (SOLUTION 1) ---
        // --- ĐOẠN MỚI CẬP NHẬT CHUẨN: ---
        String smokeStatus = request.getSmokingStatus();
        int autoSmokeBit = ("smokes".equalsIgnoreCase(smokeStatus) || "formerly smoked".equalsIgnoreCase(smokeStatus)) ? 1 : 0;

        Patient patient = Patient.builder()
                .user(user)
                .fullName(request.getFullName())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .phone(request.getPhone())
                .address(request.getAddress())
                .heightCm(request.getHeightCm())
                .weightKg(request.getWeightKg())
                .identityCard(request.getIdentityCard())
                .insuranceNumber(request.getInsuranceNumber())
                .isPregnant(isPregnantVal)
                .status("active")
                .hypertension(request.getHypertension() != null ? request.getHypertension() : false)
                .heartDisease(request.getHeartDisease() != null ? request.getHeartDisease() : false)
                .everMarried(request.getEverMarried() != null ? request.getEverMarried() : "No")
                .workType(request.getWorkType())
                .residenceType(request.getResidenceType())
                .smokingStatus(request.getSmokingStatus() != null ? request.getSmokingStatus() : "Unknown")
                .cholesterol(request.getCholesterol() != null ? request.getCholesterol() : 1)
                .smoke(autoSmokeBit) // Tự động lưu trạng thái số
                .alco(request.getAlco() != null ? request.getAlco() : 0)
                .active(request.getActive() != null ? request.getActive() : 1)
                .build();

        calculateAgeAndBmi(patient);
        patient.setPatientType(determinePatientType(patient));

        Patient savedPatient = patientRepository.save(patient);
        return mapToResponse(savedPatient);
    }

    @Override
    @Transactional
    public PatientProfileResponse updateProfile(Long userId, PatientProfileRequest request) {
        Patient patient = patientRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Patient profile not found for user ID: " + userId));

        patient.setFullName(request.getFullName());
        patient.setDateOfBirth(request.getDateOfBirth());
        patient.setGender(request.getGender());
        patient.setPhone(request.getPhone());
        patient.setAddress(request.getAddress());
        patient.setHeightCm(request.getHeightCm());
        patient.setWeightKg(request.getWeightKg());
        patient.setIdentityCard(request.getIdentityCard());
        patient.setInsuranceNumber(request.getInsuranceNumber());

        boolean isPregnantVal = false;
        if ("Nữ".equalsIgnoreCase(request.getGender()) && request.getIsPregnant() != null) {
            isPregnantVal = request.getIsPregnant();
        }
        patient.setIsPregnant(isPregnantVal);

        if (Boolean.TRUE.equals(patient.getHypertension())) {
            if (request.getHypertension() != null && !request.getHypertension()) {
                throw new RuntimeException("Không thể tự ý hủy bỏ trạng thái Tăng huyết áp. Vui lòng gửi yêu cầu thay đổi kèm bằng chứng y tế.");
            }
            patient.setHypertension(true);
        } else {
            patient.setHypertension(request.getHypertension() != null ? request.getHypertension() : false);
        }

        if (Boolean.TRUE.equals(patient.getHeartDisease())) {
            if (request.getHeartDisease() != null && !request.getHeartDisease()) {
                throw new RuntimeException("Không thể tự ý hủy bỏ trạng thái Tiền sử bệnh tim. Vui lòng gửi yêu cầu thay đổi kèm bằng chứng y tế.");
            }
            patient.setHeartDisease(true);
        } else {
            patient.setHeartDisease(request.getHeartDisease() != null ? request.getHeartDisease() : false);
        }

        patient.setEverMarried(request.getEverMarried());
        patient.setWorkType(request.getWorkType());
        patient.setResidenceType(request.getResidenceType());
        patient.setSmokingStatus(request.getSmokingStatus());

        // --- AUTOMATIC MAPPING ON UPDATE ---
        String currentSmokeStatus = request.getSmokingStatus();
        int autoSmokeBit = ("smokes".equalsIgnoreCase(currentSmokeStatus) || "formerly smoked".equalsIgnoreCase(currentSmokeStatus)) ? 1 : 0;
        patient.setSmoke(autoSmokeBit);

        patient.setCholesterol(request.getCholesterol() != null ? request.getCholesterol() : 1);
        patient.setAlco(request.getAlco() != null ? request.getAlco() : 0);
        patient.setActive(request.getActive() != null ? request.getActive() : 1);

        calculateAgeAndBmi(patient);
        patient.setPatientType(determinePatientType(patient));

        Patient updatedPatient = patientRepository.save(patient);
        return mapToResponse(updatedPatient);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientProfileResponse> getAllPatients() {
        return patientRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    private void calculateAgeAndBmi(Patient patient) {
        if (patient.getDateOfBirth() != null) {
            patient.setAge(Period.between(patient.getDateOfBirth(), LocalDate.now()).getYears());
        }

        if (patient.getHeightCm() != null && patient.getWeightKg() != null
                && patient.getHeightCm().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal heightInMeters = patient.getHeightCm().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            BigDecimal heightSquared = heightInMeters.multiply(heightInMeters);
            BigDecimal bmi = patient.getWeightKg().divide(heightSquared, 2, RoundingMode.HALF_UP);
            patient.setBmi(bmi);
        } else {
            patient.setBmi(null);
        }
    }

    private String determinePatientType(Patient patient) {
        if (patient.getAge() != null && patient.getAge() < 18) {
            return "child";
        }
        if (patient.getAge() != null && patient.getAge() >= 60) {
            return "elderly";
        }
        if ("Nữ".equalsIgnoreCase(patient.getGender()) && Boolean.TRUE.equals(patient.getIsPregnant())) {
            return "pregnant";
        }
        return "adult";
    }

    private PatientProfileResponse mapToResponse(Patient patient) {
        Double strokeRiskPercent = 0.0;
        String strokeRiskLevel = "NORMAL";
        String strokeAlertMsg = "Chỉ số sức khỏe của bạn ở mức an toàn, nguy cơ đột quỵ thấp.";

        Double cardioRiskPercent = 0.0;
        String cardioRiskLevel = "NORMAL";
        String cardioAlertMsg = "Chỉ số tim mạch của bạn ở mức an toàn.";

        Double rawAvgBloodSugar = null;
        Double rawAvgSystolic = null;
        Double rawAvgDiastolic = null;

        try {
            if (patient.getDateOfBirth() != null && patient.getHeightCm() != null && patient.getWeightKg() != null) {
                LocalDate today = LocalDate.now();
                LocalDate startOfWeek = today.with(java.time.DayOfWeek.MONDAY);
                LocalDate endOfWeek = startOfWeek.plusDays(6);

                List<DailyHealthLog> weeklyLogs = dailyHealthLogRepository.findByPatientIdAndLogDateBetweenOrderByLogDate(patient.getId(), startOfWeek, endOfWeek);

                if (weeklyLogs.isEmpty()) {
                    LocalDate lastWeekStart = startOfWeek.minusWeeks(1);
                    LocalDate lastWeekEnd = lastWeekStart.plusDays(6);
                    weeklyLogs = dailyHealthLogRepository.findByPatientIdAndLogDateBetweenOrderByLogDate(patient.getId(), lastWeekStart, lastWeekEnd);
                }

                if (!weeklyLogs.isEmpty()) {
                    double sumSugar = 0; int sugarCount = 0;
                    double sumSystolic = 0; int systolicCount = 0;
                    double sumDiastolic = 0; int diastolicCount = 0;

                    for (DailyHealthLog log : weeklyLogs) {
                        if (log.getBloodSugar() != null) {
                            sumSugar += log.getBloodSugar().doubleValue();
                            sugarCount++;
                        }
                        if (log.getSystolic() != null) {
                            sumSystolic += log.getSystolic().doubleValue();
                            systolicCount++;
                        }
                        if (log.getDiastolic() != null) {
                            sumDiastolic += log.getDiastolic().doubleValue();
                            diastolicCount++;
                        }
                    }

                    if (sugarCount > 0) rawAvgBloodSugar = sumSugar / sugarCount;
                    if (systolicCount > 0) rawAvgSystolic = sumSystolic / systolicCount;
                    if (diastolicCount > 0) rawAvgDiastolic = sumDiastolic / diastolicCount;
                }

                if (rawAvgBloodSugar == null || rawAvgSystolic == null || rawAvgDiacholicIsNull(rawAvgDiastolic)) {
                    List<WeeklyHealthReport> reports = weeklyHealthReportRepository.findByPatientIdOrderByWeekStartDesc(patient.getId());
                    if (!reports.isEmpty()) {
                        WeeklyHealthReport report = reports.get(0);
                        if (rawAvgBloodSugar == null && report.getAverageBloodSugar() != null) rawAvgBloodSugar = report.getAverageBloodSugar().doubleValue();
                        if (rawAvgSystolic == null && report.getAverageSystolic() != null) rawAvgSystolic = report.getAverageSystolic().doubleValue();
                        if (rawAvgDiastolic == null && report.getAverageDiastolic() != null) rawAvgDiastolic = report.getAverageDiastolic().doubleValue();
                    }
                }

                double avgGlucoseMgDl = (rawAvgBloodSugar != null) ? (rawAvgBloodSugar * 18.0) : 100.0;
                double finalSystolic = (rawAvgSystolic != null) ? rawAvgSystolic : 120.0;
                double finalDiastolic = (rawAvgDiastolic != null) ? rawAvgDiastolic : 80.0;

                // --- 1. GỌI MÔ HÌNH AI TIM MẠCH ĐỘNG (Port 5000 - /predict-cardio) ---
                try {
                    Map<String, Object> cardioRequest = new HashMap<>();

                    // Tính số ngày tuổi (age * 365) thay cho trường tĩnh tĩnh cũ
                    long ageInDays = (patient.getAge() != null ? patient.getAge() : 30) * 365L;
                    cardioRequest.put("age_days", ageInDays);

                    // Map giới tính: Nam -> 1, Nữ/Khác -> 2 theo chuẩn Dataset Cardio gốc
                    int genderCardioCode = "Male".equalsIgnoreCase(patient.getGender()) || "Nam".equalsIgnoreCase(patient.getGender()) ? 1 : 2;
                    cardioRequest.put("gender", genderCardioCode);

                    cardioRequest.put("height", patient.getHeightCm() != null ? patient.getHeightCm().doubleValue() : 165.0);
                    cardioRequest.put("weight", patient.getWeightKg() != null ? patient.getWeightKg().doubleValue() : 60.0);

                    // Gửi dữ liệu động lấy từ Nhật ký tuần (khớp hoàn toàn với Key mới bên Flask nhận diện)
                    cardioRequest.put("systolic", finalSystolic);
                    cardioRequest.put("diastolic", finalDiastolic);
                    cardioRequest.put("blood_sugar", avgGlucoseMgDl);

                    cardioRequest.put("cholesterol", patient.getCholesterol() != null ? patient.getCholesterol() : 1);
                    cardioRequest.put("smoke", patient.getSmoke() != null ? patient.getSmoke() : 0);
                    cardioRequest.put("alco", patient.getAlco() != null ? patient.getAlco() : 0);
                    cardioRequest.put("active", patient.getActive() != null ? patient.getActive() : 1);

                    String cardioApiUrl = "http://127.0.0.1:5000/predict-cardio";
                    Map<String, Object> cardioResponse = restTemplate.postForObject(cardioApiUrl, cardioRequest, Map.class);

                    if (cardioResponse != null && "success".equalsIgnoreCase(cardioResponse.get("status").toString())) {
                        cardioRiskPercent = Double.parseDouble(cardioResponse.get("cardio_risk_percentage").toString());
                        if (cardioResponse.containsKey("riskLevel")) cardioRiskLevel = cardioResponse.get("riskLevel").toString();
                        if (cardioResponse.containsKey("advice")) cardioAlertMsg = cardioResponse.get("advice").toString();
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ Lỗi trạm AI Cardio (Port 5000): " + e.getMessage());
                }

                // --- 2. GỌI MÔ HÌNH AI ĐỘT QUỴ ĐỘNG (Port 8000 - /predict) ---
                try {
                    int genderStroke = 0;
                    if (patient.getGender() != null) {
                        String g = patient.getGender().toLowerCase();
                        if (g.contains("fem") || g.contains("nữ")) genderStroke = 1;
                    }

                    double ageVal = patient.getAge() != null ? patient.getAge().doubleValue() : 0.0;
                    int hyperVal = (Boolean.TRUE.equals(patient.getHypertension()) || finalSystolic >= 140.0 || finalDiastolic >= 90.0) ? 1 : 0;
                    int heartVal = Boolean.TRUE.equals(patient.getHeartDisease()) ? 1 : 0;

                    int workVal = 0;
                    if (patient.getWorkType() != null) {
                        String w = patient.getWorkType();
                        if (w.equalsIgnoreCase("Self-employed")) workVal = 1;
                        else if (w.equalsIgnoreCase("Govt_job")) workVal = 2;
                        else if (w.equalsIgnoreCase("children")) workVal = -1;
                        else if (w.equalsIgnoreCase("Never_worked")) workVal = -2;
                    }

                    int resVal = 1;
                    if (patient.getResidenceType() != null && patient.getResidenceType().equalsIgnoreCase("Rural")) {
                        resVal = 0;
                    }

                    double bmiVal = patient.getBmi() != null ? patient.getBmi().doubleValue() : 25.0;

                    int strokeSmokeVal = -1;
                    if (patient.getSmokingStatus() != null) {
                        String s = patient.getSmokingStatus();
                        if (s.equalsIgnoreCase("never smoked")) strokeSmokeVal = 0;
                        else if (s.equalsIgnoreCase("formerly smoked")) strokeSmokeVal = 1;
                        else if (s.equalsIgnoreCase("smokes")) strokeSmokeVal = 2;
                    }

                    Map<String, Object> strokeRequest = new HashMap<>();
                    strokeRequest.put("gender", genderStroke);
                    strokeRequest.put("age", ageVal);
                    strokeRequest.put("hypertension", hyperVal);
                    strokeRequest.put("heart_disease", heartVal);
                    strokeRequest.put("work_type", workVal);
                    strokeRequest.put("Residence_type", resVal);
                    strokeRequest.put("avg_glucose_level", avgGlucoseMgDl);
                    strokeRequest.put("bmi", bmiVal);
                    strokeRequest.put("smoking_status", strokeSmokeVal);

                    String strokeApiUrl = "http://127.0.0.1:8000/predict";
                    Map<String, Object> strokeResponse = restTemplate.postForObject(strokeApiUrl, strokeRequest, Map.class);

                    if (strokeResponse != null && strokeResponse.containsKey("risk_percentage")) {
                        strokeRiskPercent = Double.parseDouble(strokeResponse.get("risk_percentage").toString());
                        String responseRiskLevel = strokeResponse.get("risk_level").toString();

                        if ("Critical".equalsIgnoreCase(responseRiskLevel)) {
                            strokeRiskLevel = "CRITICAL_RISK";
                            strokeAlertMsg = "🚨 Nguy cơ đột quỵ rất cao (Nguy hiểm)! Cần tham vấn bác sĩ ngay để kiểm soát huyết áp và các chỉ số sức khỏe.";
                        } else if ("High".equalsIgnoreCase(responseRiskLevel)) {
                            strokeRiskLevel = "HIGH_RISK";
                            strokeAlertMsg = "⚠️ Nguy cơ đột quỵ cao! Bạn nên điều chỉnh chế độ sinh hoạt, hạn chế các chất kích thích và theo dõi huyết áp thường xuyên.";
                        } else if ("Medium".equalsIgnoreCase(responseRiskLevel)) {
                            strokeRiskLevel = "MODERATE_RISK";
                            strokeAlertMsg = "⚡ Nguy cơ đột quỵ ở mức trung bình. Hãy chú ý giữ thói quen rèn luyện thể thao đều đặn.";
                        } else {
                            strokeRiskLevel = "NORMAL";
                            strokeAlertMsg = "Chỉ số sức khỏe của bạn ở mức an toàn, nguy cơ đột quỵ thấp.";
                        }
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ Lỗi trạm AI Stroke (Port 8000): " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Lỗi xử lý tổng hợp dữ liệu AI: " + e.getMessage());
        }

        return PatientProfileResponse.builder()
                .id(patient.getId())
                .userId(patient.getUser().getId())
                .email(patient.getUser().getEmail())
                .fullName(patient.getFullName())
                .dateOfBirth(patient.getDateOfBirth())
                .age(patient.getAge())
                .gender(patient.getGender())
                .phone(patient.getPhone())
                .address(patient.getAddress())
                .heightCm(patient.getHeightCm())
                .weightKg(patient.getWeightKg())
                .bmi(patient.getBmi())
                .status(patient.getStatus())
                .identityCard(patient.getIdentityCard())
                .insuranceNumber(patient.getInsuranceNumber())
                .patientType(patient.getPatientType())
                .isPregnant(patient.getIsPregnant())
                .hypertension(patient.getHypertension())
                .heartDisease(patient.getHeartDisease())
                .everMarried(patient.getEverMarried())
                .workType(patient.getWorkType())
                .residenceType(patient.getResidenceType())
                .smokingStatus(patient.getSmokingStatus())
                .createdAt(patient.getCreatedAt())
                .updatedAt(patient.getUpdatedAt())
                .cholesterol(patient.getCholesterol())
                .smoke(patient.getSmoke())
                .alco(patient.getAlco())
                .active(patient.getActive())
                // Kết quả AI kết nối View động
                .strokeRiskPercentage(strokeRiskPercent)
                .strokeRiskLevel(strokeRiskLevel)
                .strokeAlertMessage(strokeAlertMsg)
                .cardioRiskPercentage(cardioRiskPercent)
                .cardioRiskLevel(cardioRiskLevel)
                .cardioAlertMessage(cardioAlertMsg)
                // Các chỉ số tính toán để render UI nếu cần dùng đến
                .computedAvgGlucMmol(rawAvgBloodSugar)
                .computedAvgSystolic(rawAvgSystolic)
                .computedAvgDiastolic(rawAvgDiastolic)
                .build();
    }

    private boolean rawAvgDiacholicIsNull(Double rawAvgDiastolic) {
        return rawAvgDiastolic == null;
    }
}