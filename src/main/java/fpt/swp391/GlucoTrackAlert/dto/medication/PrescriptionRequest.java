package fpt.swp391.GlucoTrackAlert.dto.medication;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PrescriptionRequest {

    @NotNull(message = "patientId không được để trống")
    private Long patientId;

    // KHÔNG nhận doctorId từ client: controller sẽ tự gán bác sĩ đang đăng nhập
    // (lấy từ JWT/SecurityContext) để tránh giả mạo người kê đơn.
    // Field này vẫn tồn tại để service/response dùng nội bộ, nhưng giá trị
    // client gửi lên sẽ bị bỏ qua và ghi đè trong controller.
    private Long doctorId;

    private LocalDate prescribedDate;

    private String note;

    @NotEmpty(message = "Đơn thuốc phải có ít nhất một loại thuốc (items)")
    @Valid
    private List<PrescriptionItemRequest> items;
}