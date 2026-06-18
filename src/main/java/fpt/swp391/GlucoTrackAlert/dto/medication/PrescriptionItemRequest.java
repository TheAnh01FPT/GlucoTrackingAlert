package fpt.swp391.GlucoTrackAlert.dto.medication;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PrescriptionItemRequest {

    @NotBlank(message = "Tên thuốc không được để trống")
    private String medicineName;

    @NotBlank(message = "Liều lượng không được để trống")
    private String dosage;

    private String frequency;

    // "07:00,12:00,19:00" — phân cách bằng dấu phẩy, mỗi giá trị theo định dạng HH:mm
    @NotBlank(message = "Giờ uống thuốc (timeOfDay) không được để trống")
    @Pattern(
            regexp = "^([01]\\d|2[0-3]):[0-5]\\d(\\s*,\\s*([01]\\d|2[0-3]):[0-5]\\d)*$",
            message = "timeOfDay phải có định dạng HH:mm, phân cách bằng dấu phẩy (vd: 07:00,12:00,19:00)"
    )
    private String timeOfDay;

    // Bắt buộc nhập số ngày uống thuốc để tính endDate rõ ràng, tránh trường hợp
    // endDate = startDate (chỉ sinh log 1 ngày) khi bác sĩ quên điền.
    @NotNull(message = "Số ngày uống thuốc (durationDays) không được để trống")
    @Min(value = 1, message = "Số ngày uống thuốc phải lớn hơn hoặc bằng 1")
    private Integer durationDays;

    private String instructions;

    private LocalDate startDate;
}