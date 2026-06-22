package fpt.swp391.GlucoTrackAlert.dto.recommendation;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorRecommendationResponse {
    private Long id;
    private String title;
    private String recommendation;
    private String status;
    private String doctorName;
    private Long doctorId;
    private String patientName;
    private Long patientId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}