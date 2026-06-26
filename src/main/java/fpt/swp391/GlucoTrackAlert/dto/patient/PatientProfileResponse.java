package fpt.swp391.GlucoTrackAlert.dto.patient;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientProfileResponse {
    private Long id;
    private Long userId;
    private String email;
    private String fullName;
    private LocalDate dateOfBirth;
    private Integer age;
    private String gender;
    private String phone;
    private String address;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private BigDecimal bmi;
    private String status;
    private String identityCard;
    private String insuranceNumber;
    private String patientType;
    private Boolean isPregnant;
    private Boolean hypertension;
    private Boolean heartDisease;
    private String everMarried;
    private String workType;
    private String residenceType;
    private String smokingStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Double cardioRiskPercentage; // Tỷ lệ nguy cơ tim mạch (%)
    private String cardioRiskLevel;

    private String cardioAlertMessage;
    private Integer apHi;        // Huyết áp tâm thu (Ví dụ: 120)
    private Integer apLo;        // Huyết áp tâm trương (Ví dụ: 80)
    private Integer cholesterol; // 1: Bình thường, 2: Trên chuẩn, 3: Cao
    private Double gluc;         // Chỉ số đường huyết (mg/dL)
    private Integer smoke;       // 1: Có hút thuốc, 0: Không
    private Integer alco;        // 1: Có uống rượu, 0: Không
    private Integer active;      // 1: Có thể thao/vận động, 0: Khô// Tin nhắn khuyên dùng từ cấu hình Admin
}