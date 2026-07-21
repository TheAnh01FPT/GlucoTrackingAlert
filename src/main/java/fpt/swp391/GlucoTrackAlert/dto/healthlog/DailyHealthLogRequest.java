package fpt.swp391.GlucoTrackAlert.dto.healthlog;

import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class DailyHealthLogRequest {
    @NotNull(message = "Ngày ghi nhật ký không được để trống")
    @PastOrPresent(message = "Ngày ghi nhật ký không được ở tương lai")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate logDate;

    @DecimalMin(value = "0.0", inclusive = false, message = "Chỉ số đường huyết phải lớn hơn 0")
    @DecimalMax(value = "50.0", message = "Chỉ số đường huyết không hợp lệ")
    private BigDecimal bloodSugar;

    @Min(value = 30, message = "Huyết áp tâm thu tối thiểu là 30 mmHg")
    @Max(value = 300, message = "Huyết áp tâm thu tối đa là 300 mmHg")
    private Integer systolic;

    @Min(value = 20, message = "Huyết áp tâm trương tối thiểu là 20 mmHg")
    @Max(value = 200, message = "Huyết áp tâm trương tối đa là 200 mmHg")
    private Integer diastolic;

    @DecimalMin(value = "0.0", message = "Giờ ngủ không thể âm")
    @DecimalMax(value = "24.0", message = "Giờ ngủ không thể vượt quá 24 giờ")
    private BigDecimal sleepHours;

    @Min(value = 0, message = "Lượng nước uống không thể âm")
    @Max(value = 10000, message = "Lượng nước uống không thể vượt quá 10000 ml")
    private Integer waterMl;

    @Size(max = 20, message = "Mức độ tiêu thụ đường không vượt quá 20 ký tự")
    private String sugarConsumptionLevel;

    @AssertTrue(message = "Huyết áp tâm thu và tâm trương phải được nhập cùng nhau")
    public boolean isBloodPressurePairValid() {
        return (systolic == null && diastolic == null) || (systolic != null && diastolic != null);
    }

    @AssertTrue(message = "Huyết áp tâm thu phải lớn hơn hoặc bằng huyết áp tâm trương")
    public boolean isBloodPressureRangeValid() {
        return systolic == null || diastolic == null || systolic >= diastolic;
    }

    private String symptoms;
    private String note;
    private Integer physicalActivity;
}
