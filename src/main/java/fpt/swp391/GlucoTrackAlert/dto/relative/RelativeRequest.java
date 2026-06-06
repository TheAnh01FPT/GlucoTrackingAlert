package fpt.swp391.GlucoTrackAlert.dto.relative;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RelativeRequest {

    @NotNull(message = "Patient ID không được để trống")
    private Long patientId;

    @NotBlank(message = "Họ và tên người thân không được để trống")
    @Size(max = 150, message = "Họ và tên không vượt quá 150 ký tự")
    private String fullName;

    @Size(max = 50, message = "Quan hệ không vượt quá 50 ký tự")
    private String relationship;

    @Min(value = 0, message = "Tuổi không được nhỏ hơn 0")
    @Max(value = 150, message = "Tuổi không hợp lệ")
    private Integer age;

    @Pattern(regexp = "^$|[0-9]{10,11}$", message = "Số điện thoại người thân phải gồm 10 hoặc 11 chữ số")
    private String phone;

    @NotBlank(message = "Email người thân không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 150, message = "Email không vượt quá 150 ký tự")
    private String email;

    @Builder.Default
    private Boolean notifyEnabled = true;
}
