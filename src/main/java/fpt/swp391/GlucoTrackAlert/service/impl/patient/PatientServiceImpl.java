package fpt.swp391.GlucoTrackAlert.service.impl.patient;

import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileRequest;
import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileResponse;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
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
    private final RestTemplate restTemplate;

    @Autowired
    public PatientServiceImpl(PatientRepository patientRepository, UserRepository userRepository) {
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
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
                // 🏠 Nhóm thông số tại nhà (Bắt buộc)
                .cp(request.getCp())
                .trestbps(request.getTrestbps())
                .fbs(request.getFbs())
                .exang(request.getExang())
                // 🏥 Nhóm thông số bệnh viện (Có thể để trống - Null)
                .chol(request.getChol())
                .restecg(request.getRestecg())
                .thalach(request.getThalach())
                .oldpeak(request.getOldpeak())
                .slope(request.getSlope())
                .ca(request.getCa())
                .thal(request.getThal())
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

        // Cập nhật cấu trúc 13 trường chuẩn mới
        patient.setCp(request.getCp());
        patient.setTrestbps(request.getTrestbps());
        patient.setFbs(request.getFbs());
        patient.setExang(request.getExang());

        patient.setChol(request.getChol());
        patient.setRestecg(request.getRestecg());
        patient.setThalach(request.getThalach());
        patient.setOldpeak(request.getOldpeak());
        patient.setSlope(request.getSlope());
        patient.setCa(request.getCa());
        patient.setThal(request.getThal());

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
        Double riskPercent = 0.0;
        String riskLevel = "NORMAL";
        String alertMsg = "Chỉ số tim mạch của bạn ở mức an toàn.";
        String streamType = "LUONG_2";

        try {
            if (patient.getDateOfBirth() != null) {
                int ageYears = patient.getAge() != null ? patient.getAge() : Period.between(patient.getDateOfBirth(), LocalDate.now()).getYears();
                int sexCode = "Nam".equalsIgnoreCase(patient.getGender()) ? 1 : 0;

                // 1. Khởi tạo Request Body với LinkedHashMap để giữ thứ tự các trường
                Map<String, Object> requestBody = new java.util.LinkedHashMap<>();
                requestBody.put("age", ageYears);
                requestBody.put("sex", sexCode);
                requestBody.put("cp", patient.getCp() != null ? patient.getCp() : 0);
                requestBody.put("trestbps", patient.getTrestbps() != null ? patient.getTrestbps() : 120);
                requestBody.put("fbs", patient.getFbs() != null ? patient.getFbs() : 0);
                requestBody.put("exang", patient.getExang() != null ? patient.getExang() : 0);

                // 2. Phân luồng dữ liệu
                if (patient.getChol() != null && patient.getRestecg() != null && patient.getThalach() != null) {
                    requestBody.put("chol", patient.getChol());
                    requestBody.put("restecg", patient.getRestecg());
                    requestBody.put("thalach", patient.getThalach());
                    requestBody.put("oldpeak", patient.getOldpeak() != null ? patient.getOldpeak().doubleValue() : 0.0);
                    requestBody.put("slope", patient.getSlope() != null ? patient.getSlope() : 1);
                    requestBody.put("ca", patient.getCa() != null ? patient.getCa() : 0);
                    requestBody.put("thal", patient.getThal() != null ? patient.getThal() : 2);
                    streamType = "LUONG_1";
                } else {
                    requestBody.put("chol", 200);
                    requestBody.put("restecg", 0);
                    requestBody.put("thalach", 150);
                    requestBody.put("oldpeak", 0.0);
                    requestBody.put("slope", 1);
                    requestBody.put("ca", 0);
                    requestBody.put("thal", 2);
                    streamType = "LUONG_2";
                }

                // 3. DEBUG LOG: In ra console để kiểm tra dữ liệu trước khi gửi sang Python
                System.out.println("=== [DEBUG] JAVA GỬI AI: " + requestBody.toString());

                // 4. Gọi Python API
                String pythonApiUrl = "http://127.0.0.1:5000/predict";
                Map<String, Object> response = restTemplate.postForObject(pythonApiUrl, requestBody, Map.class);

                // 5. Xử lý phản hồi từ AI
                if (response != null) {
                    System.out.println("=== [DEBUG] AI PHẢN HỒI: " + response.toString());

                    if (response.containsKey("risk_percentage")) {
                        Object riskObj = response.get("risk_percentage");
                        riskPercent = Double.parseDouble(riskObj.toString());
                        String aiRisk = response.getOrDefault("risk_level", "LOW").toString();

                        if ("HIGH".equalsIgnoreCase(aiRisk) || riskPercent > 50.0) {
                            riskLevel = "HIGH_RISK";
                            alertMsg = "⚠️ Cảnh báo nguy cơ tim mạch ở mức cao!";
                        } else if (riskPercent > 20.0) {
                            riskLevel = "MODERATE_RISK";
                            alertMsg = "⚡ Nguy cơ tim mạch ở mức trung bình.";
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("=== [ERROR] KẾT NỐI AI THẤT BẠI: " + e.getMessage());
        }

        // 6. Build response cuối cùng
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
                .createdAt(patient.getCreatedAt())
                .updatedAt(patient.getUpdatedAt())
                .cp(patient.getCp())
                .trestbps(patient.getTrestbps())
                .fbs(patient.getFbs())
                .exang(patient.getExang())
                .chol(patient.getChol())
                .restecg(patient.getRestecg())
                .thalach(patient.getThalach())
                .oldpeak(patient.getOldpeak())
                .slope(patient.getSlope())
                .ca(patient.getCa())
                .thal(patient.getThal())
                .cardioRiskPercentage(riskPercent)
                .cardioRiskLevel(riskLevel)
                .cardioAlertMessage(alertMsg)
                .cardioStreamType(streamType)
                .build();
    }
}