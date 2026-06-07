package fpt.swp391.GlucoTrackAlert.service.patient;

import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileRequest;
import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileResponse;

public interface PatientService {
    PatientProfileResponse getProfileByUserId(Long userId);
    PatientProfileResponse createProfile(PatientProfileRequest request);
    PatientProfileResponse updateProfile(Long userId, PatientProfileRequest request);
}
