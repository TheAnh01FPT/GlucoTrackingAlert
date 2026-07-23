package fpt.swp391.GlucoTrackAlert.dto.recommendation;

import fpt.swp391.GlucoTrackAlert.enums.RecommendationCategory;
import fpt.swp391.GlucoTrackAlert.enums.RecommendationPriority;
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
    private RecommendationPriority priority;
    private RecommendationCategory category;
    private boolean isRead;
    private String doctorName;
    private Long doctorId;
    private String patientName;
    private Long patientId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}