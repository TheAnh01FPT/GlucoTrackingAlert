package fpt.swp391.GlucoTrackAlert.service.patient;

import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileRequest;
import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileResponse;
import java.util.List;

public interface PatientService {
    PatientProfileResponse getProfileByUserId(Long userId);
    boolean existsByUserId(Long userId);
    PatientProfileResponse createProfile(PatientProfileRequest request);
    PatientProfileResponse updateProfile(Long userId, PatientProfileRequest request);
     List<PatientProfileResponse> getAllPatients();
}
