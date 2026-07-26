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
    private String identityCardImage;
    private String identityCardStatus;
    private String insuranceNumber;
    private String insuranceNumberImage;
    private String insuranceCardStatus;
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

    // Các thông số sinh hoạt nền
    private Integer cholesterol;
    private Integer smoke;
    private Integer alco;
    private Integer active;

    // Kết quả phân tích động trả về từ các API AI trạm Python
    private Double strokeRiskPercentage;
    private String strokeRiskLevel;
    private String strokeAlertMessage;

    private Double cardioRiskPercentage;
    private String cardioRiskLevel;
    private String cardioAlertMessage;

    // Các chỉ số trung bình tuần tính từ logs để hiển thị lên UI nếu cần
    private Double computedAvgSystolic;
    private Double computedAvgDiastolic;
    private Double computedAvgGlucMmol;
}