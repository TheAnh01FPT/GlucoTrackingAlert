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

import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

@Service
public class PatientServiceImpl implements PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    @Autowired
    public PatientServiceImpl(PatientRepository patientRepository, UserRepository userRepository) {
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
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
                .build();

        calculateAgeAndBmi(patient);

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

        calculateAgeAndBmi(patient);

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
        // 1. Calculate Age
        if (patient.getDateOfBirth() != null) {
            patient.setAge(Period.between(patient.getDateOfBirth(), LocalDate.now()).getYears());
        }

        // 2. Calculate BMI
        if (patient.getHeightCm() != null && patient.getWeightKg() != null
                && patient.getHeightCm().compareTo(BigDecimal.ZERO) > 0) {
            // Height in meters = heightCm / 100
            BigDecimal heightInMeters = patient.getHeightCm().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            // Height squared = heightInMeters * heightInMeters
            BigDecimal heightSquared = heightInMeters.multiply(heightInMeters);
            // BMI = weightKg / heightSquared
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
                .patientType(determinePatientType(patient))
                .isPregnant(patient.getIsPregnant())
                .createdAt(patient.getCreatedAt())
                .updatedAt(patient.getUpdatedAt())
                .build();
    }
}
