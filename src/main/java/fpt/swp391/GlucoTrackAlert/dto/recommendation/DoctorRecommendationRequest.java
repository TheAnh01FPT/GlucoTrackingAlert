package fpt.swp391.GlucoTrackAlert.dto.recommendation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DoctorRecommendationRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    @NotBlank(message = "Nội dung khuyến nghị không được để trống")
    private String recommendation;

    @NotNull(message = "Bệnh nhân không được để trống")
    private Long patientId;
}