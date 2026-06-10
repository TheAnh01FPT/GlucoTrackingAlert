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
}
