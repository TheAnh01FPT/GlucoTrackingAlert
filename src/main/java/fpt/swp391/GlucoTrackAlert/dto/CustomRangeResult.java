package fpt.swp391.GlucoTrackAlert.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CustomRangeResult(
    LocalDate fromDate,
    LocalDate toDate,
    int logCount,
    BigDecimal avgBloodSugar,
    BigDecimal avgSystolic,
    BigDecimal avgDiastolic,
    BigDecimal riskPercentage,
    String riskLevel,
    String recommendation,
    boolean lowConfidence
) {}