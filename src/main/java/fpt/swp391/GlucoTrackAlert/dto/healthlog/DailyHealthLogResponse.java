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
    private String patientType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
}
