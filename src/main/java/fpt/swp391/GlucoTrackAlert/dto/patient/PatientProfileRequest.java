package fpt.swp391.GlucoTrackAlert.dto.patient;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientProfileRequest {
    
    @NotNull(message = "User ID không được trống")
    private Long userId;
    
    @NotBlank(message = "Họ và tên không được để trống")
    @Size(max = 150, message = "Họ và tên không được vượt quá 150 ký tự")
    private String fullName;
    
    @Past(message = "Ngày sinh phải là một ngày trong quá khứ")
    private LocalDate dateOfBirth;
    
    @Size(max = 20, message = "Giới tính không vượt quá 20 ký tự")
    private String gender;
    
    @Pattern(regexp = "^$|[0-9]{10,11}$", message = "Số điện thoại phải gồm 10 hoặc 11 chữ số")
    private String phone;
    
    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String address;
    
    @DecimalMin(value = "50.0", message = "Chiều cao tối thiểu là 50 cm")
    @DecimalMax(value = "250.0", message = "Chiều cao tối đa là 250 cm")
    private BigDecimal heightCm;
    
    @DecimalMin(value = "10.0", message = "Cân nặng tối thiểu là 10 kg")
    @DecimalMax(value = "300.0", message = "Cân nặng tối đa là 300 kg")
    private BigDecimal weightKg;
}
