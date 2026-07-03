package fpt.swp391.GlucoTrackAlert.dto.healthlog;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyHealthLogResponse {
    private Long id;
    private Long patientId;
    private Long userId;
    private String patientName;
    private LocalDate logDate;
    private BigDecimal bloodSugar;
    private Integer systolic;
    private Integer diastolic;
    private BigDecimal sleepHours;
    private Integer waterMl;
    private String sugarConsumptionLevel;
    private String symptoms;
    private String note;
    private String bloodSugarStatus;
    private String systolicStatus;
    private String diastolicStatus;
    private java.math.BigDecimal bloodSugarNormalMin;
    private java.math.BigDecimal bloodSugarNormalMax;
    private java.math.BigDecimal bloodSugarWarningMin;
    private java.math.BigDecimal bloodSugarWarningMax;
    private String patientType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private BigDecimal riskPercentage;
    private String riskLevel;
    private String aiSummary;
}
