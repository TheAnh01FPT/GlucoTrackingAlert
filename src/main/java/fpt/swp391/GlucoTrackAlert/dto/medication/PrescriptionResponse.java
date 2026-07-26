package fpt.swp391.GlucoTrackAlert.dto.medication;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PrescriptionResponse {
    private Long id;
    private Long patientId;
    private String patientName;
    private Long doctorId;
    private String doctorName;
    private LocalDate prescribedDate;
    private String note;
    private String status;
    private List<PrescriptionItemResponse> items;
}