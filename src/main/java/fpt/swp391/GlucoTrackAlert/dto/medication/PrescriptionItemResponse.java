package fpt.swp391.GlucoTrackAlert.dto.medication;

import lombok.*;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PrescriptionItemResponse {
    private Long id;
    private String medicineName;
    private String dosage;
    private String frequency;
    private String timeOfDay;
    private Integer durationDays;
    private String instructions;
    private LocalDate startDate;
    private LocalDate endDate;
}