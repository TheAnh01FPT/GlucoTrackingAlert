package fpt.swp391.GlucoTrackAlert.service;

public interface ComplicationRiskService {
    void assessPatient(Long patientId, Long dailyHealthLogId);
}