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

    @Size(max = 20, message = "Số căn cước công dân không được vượt quá 20 ký tự")
    @Pattern(regexp = "^$|[0-9]{12}$", message = "Số căn cước công dân phải gồm đúng 12 chữ số")
    private String identityCard;

    @Size(max = 50, message = "Mã bảo hiểm y tế không được vượt quá 50 ký tự")
    @Pattern(regexp = "^$|[0-9]{10}$", message = "Mã số bảo hiểm y tế phải gồm đúng 10 chữ số")
    private String insuranceNumber;

    private Boolean isPregnant;

    // --- 🔀 ĐỒNG BỘ ĐẦU VÀO PHÂN LỒNG AI ---
    private Integer cp;          // Loại đau ngực (0: Điển hình, 1: Không điển hình, 2: Đau không do tim, 3: Không triệu chứng)
    private Integer trestbps;    // Huyết áp tâm thu đo lúc nghỉ ngơi (mmHg)
    private Integer fbs;         // Đường huyết lúc đói > 120 mg/dl (1: Đúng, 0: Sai)
    private Integer exang;       // Xuất hiện đau ngực khi vận động gắng sức (1: Có, 0: Không)

    private Integer chol;        // Hàm lượng Cholesterol trong máu (mg/dl)
    private Integer restecg;     // Kết quả điện tâm đồ (0: Bình thường, 1: Bất thường ST, 2: Phì đại thất trái)
    private Integer thalach;     // Nhịp tim đạt mức tối đa (bpm)
    private BigDecimal oldpeak;  // Độ suy giảm đoạn ST sau vận động
    private Integer slope;       // Độ dốc đoạn ST kiểm tra gắng sức (0-2)
    private Integer ca;          // Số lượng mạch máu lớn bị nghẽn (0-4)
    private Integer thal;        // Kết quả xạ hình tim (0-3)
}