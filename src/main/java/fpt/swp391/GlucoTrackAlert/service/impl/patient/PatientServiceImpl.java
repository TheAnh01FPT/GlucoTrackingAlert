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
                // Đóng gói chỉ số lâm sàng người dùng tự chọn
                .apHi(request.getApHi())
                .apLo(request.getApLo())
                .cholesterol(request.getCholesterol())
                .gluc(request.getGluc())
                .smoke(request.getSmoke())
                .alco(request.getAlco())
                .active(request.getActive())
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

        // Cập nhật chỉ số lâm sàng động từ form chỉnh sửa
        patient.setApHi(request.getApHi());
        patient.setApLo(request.getApLo());
        patient.setCholesterol(request.getCholesterol());
        patient.setGluc(request.getGluc());
        patient.setSmoke(request.getSmoke());
        patient.setAlco(request.getAlco());
        patient.setActive(request.getActive());

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

        try {
            if (patient.getDateOfBirth() != null && patient.getHeightCm() != null && patient.getWeightKg() != null) {

                long ageDays = java.time.temporal.ChronoUnit.DAYS.between(patient.getDateOfBirth(), LocalDate.now());
                int genderCode = "Male".equalsIgnoreCase(patient.getGender()) || "Nam".equalsIgnoreCase(patient.getGender()) ? 2 : 1;

                // Đóng gói JSON lấy ĐÚNG dữ liệu thực tế người dùng đã lưu trong DB
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("age_days", ageDays);
                requestBody.put("gender", genderCode);
                requestBody.put("height", patient.getHeightCm().doubleValue());
                requestBody.put("weight", patient.getWeightKg().doubleValue());

                // Kiểm tra nếu Null thì dùng mức nền mặc định an toàn
                requestBody.put("ap_hi", patient.getApHi() != null ? patient.getApHi() : 120);
                requestBody.put("ap_lo", patient.getApLo() != null ? patient.getApLo() : 80);
                requestBody.put("cholesterol", patient.getCholesterol() != null ? patient.getCholesterol() : 1);
                requestBody.put("gluc", patient.getGluc() != null ? patient.getGluc() : 100.0);
                requestBody.put("smoke", patient.getSmoke() != null ? patient.getSmoke() : 0);
                requestBody.put("alco", patient.getAlco() != null ? patient.getAlco() : 0);
                requestBody.put("active", patient.getActive() != null ? patient.getActive() : 1);

                String pythonApiUrl = "http://127.0.0.1:5000/predict-cardio";
                Map<String, Object> response = restTemplate.postForObject(pythonApiUrl, requestBody, Map.class);

                if (response != null && response.containsKey("cardio_risk_percentage")) {
                    riskPercent = Double.parseDouble(response.get("cardio_risk_percentage").toString());

                    if (riskPercent > 50.0) {
                        riskLevel = "HIGH_RISK";
                        alertMsg = "⚠️ Nguy cơ tim mạch cao! Bạn nên điều chỉnh chế độ sinh hoạt, hạn chế các chất kích thích và theo dõi huyết áp thường xuyên.";
                    } else if (riskPercent > 20.0) {
                        riskLevel = "MODERATE_RISK";
                        alertMsg = "⚡ Nguy cơ tim mạch ở mức trung bình. Hãy chú ý giữ thói quen rèn luyện thể thao đều đặn.";
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Chưa kết nối được trạm AI Python: " + e.getMessage());
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
                .createdAt(patient.getCreatedAt())
                .updatedAt(patient.getUpdatedAt())
                // Trả về dữ liệu gốc lâm sàng để hiển thị Form Chỉnh Sửa
                .apHi(patient.getApHi())
                .apLo(patient.getApLo())
                .cholesterol(patient.getCholesterol())
                .gluc(patient.getGluc())
                .smoke(patient.getSmoke())
                .alco(patient.getAlco())
                .active(patient.getActive())
                // Kết quả phân tích từ AI
                .cardioRiskPercentage(riskPercent)
                .cardioRiskLevel(riskLevel)
                .cardioAlertMessage(alertMsg)
                .build();
    }
}