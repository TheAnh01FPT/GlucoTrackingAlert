package fpt.swp391.GlucoTrackAlert.dto.patient;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientProfileRequest {

    @NotNull(message = "User ID không được trống")
    private Long userId;

    @Size(max = 150, message = "Họ và tên không được vượt quá 150 ký tự")
    private String fullName;

    @Past(message = "Ngày sinh phải là một ngày trong quá khứ")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    @Size(max = 20, message = "Giới tính không vượt quá 20 ký tự")
    private String gender;

    @Pattern(regexp = "^$|^0[0-9]{9}$", message = "Số điện thoại phải bắt đầu bằng số 0 và gồm đúng 10 chữ số")
    private String phone;

    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String address;

    @DecimalMin(value = "50.0", message = "Chiều cao tối thiểu là 50 cm")
    @DecimalMax(value = "250.0", message = "Chiều cao tối đa là 250 cm")
    private BigDecimal heightCm;

    @DecimalMin(value = "10.0", message = "Cân nặng tối thiểu là 10 kg")
    @DecimalMax(value = "300.0", message = "Cân nặng tối đa là 300 kg")
    private BigDecimal weightKg;

    @Size(max = 20, message = "Số căn cước công dân không được vượt quá 20 ký tự")
    @Pattern(regexp = "^$|[0-9]{12}$", message = "Số căn cước công dân phải gồm đúng 12 chữ số")
    private String identityCard;

    private String identityCardImage;

    private String identityCardStatus;

    @Size(max = 50, message = "Mã bảo hiểm y tế không được vượt quá 50 ký tự")
    @Pattern(regexp = "^$|[0-9]{10}$", message = "Mã số bảo hiểm y tế phải gồm đúng 10 chữ số")
    private String insuranceNumber;

    private String insuranceNumberImage;

    private String insuranceCardStatus;

    private Boolean isPregnant;

    private Boolean hypertension;

    private Boolean heartDisease;

    @Size(max = 20, message = "Tình trạng hôn nhân không được vượt quá 20 ký tự")
    private String everMarried;

    @Size(max = 50, message = "Loại hình công việc không được vượt quá 50 ký tự")
    private String workType;

    @Size(max = 20, message = "Khu vực sinh sống không được vượt quá 20 ký tự")
    private String residenceType;

    @Size(max = 50, message = "Tình trạng hút thuốc không được vượt quá 50 ký tự")
    private String smokingStatus;

    // Chỉ giữ lại các trường thói quen sinh hoạt nền nhận từ Form
    private Integer cholesterol;
    private Integer smoke;
    private Integer alco;
    private Integer active;// 1: Có thể thao/vận động, 0: Không
}