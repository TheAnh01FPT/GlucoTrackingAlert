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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Chỉ số lâm sàng Cleveland trả về hiển thị
    private Integer cp;
    private Integer trestbps;
    private Integer fbs;
    private Integer exang;
    private Integer chol;
    private Integer restecg;
    private Integer thalach;
    private BigDecimal oldpeak;
    private Integer slope;
    private Integer ca;
    private Integer thal;

    // Bộ ba kết quả xử lý của Trạm AI
    private Double cardioRiskPercentage;
    private String cardioRiskLevel;
    private String cardioAlertMessage;
    private String cardioStreamType;     // Lưu trạng thái: "LUONG_1" (Bệnh viện) hoặc "LUONG_2" (Tại nhà)
}