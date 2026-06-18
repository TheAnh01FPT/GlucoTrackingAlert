package fpt.swp391.GlucoTrackAlert.dto.medication;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MedicationLogResponse {
    private Long id;
    private Long prescriptionItemId;
    private String medicineName;
    private String dosage;
    private String instructions;
    private String scheduledTime;
    private String takenAt;
    private String status;
    private String note;
}