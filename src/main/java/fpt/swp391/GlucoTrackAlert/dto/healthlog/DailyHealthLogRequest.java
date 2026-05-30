package fpt.swp391.GlucoTrackAlert.dto.healthlog;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class DailyHealthLogRequest {
    private LocalDate logDate;
    private BigDecimal bloodSugar;
    private Integer systolic;
    private Integer diastolic;
    private BigDecimal sleepHours;
    private Integer waterMl;
    private String sugarConsumptionLevel;
    private String symptoms;
    private String note;
}
