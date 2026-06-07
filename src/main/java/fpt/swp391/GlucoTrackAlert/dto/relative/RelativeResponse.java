package fpt.swp391.GlucoTrackAlert.dto.relative;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RelativeResponse {
    private Long id;
    private Long patientId;
    private String patientName;
    private String fullName;
    private String relationship;
    private Integer age;
    private String phone;
    private String email;
    private Boolean notifyEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
