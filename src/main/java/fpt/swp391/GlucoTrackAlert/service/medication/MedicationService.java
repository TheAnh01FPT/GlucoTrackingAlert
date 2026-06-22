package fpt.swp391.GlucoTrackAlert.service.medication;

import fpt.swp391.GlucoTrackAlert.dto.medication.MedicationLogResponse;
import fpt.swp391.GlucoTrackAlert.dto.medication.PrescriptionRequest;
import fpt.swp391.GlucoTrackAlert.dto.medication.PrescriptionResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface MedicationService {
    PrescriptionResponse createPrescription(PrescriptionRequest request);
    List<PrescriptionResponse> getPrescriptionsByPatient(Long patientId);
    List<MedicationLogResponse> getDailyLogs(Long patientId, LocalDate date);
    MedicationLogResponse markTaken(Long logId);
    Map<String, Object> getAdherenceStat(Long patientId);
    void cancelPrescription(Long prescriptionId);
}