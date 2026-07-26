package fpt.swp391.GlucoTrackAlert.service.healthlog;

import fpt.swp391.GlucoTrackAlert.dto.CustomRangeResult;
import fpt.swp391.GlucoTrackAlert.model.risk.WeeklyHealthReport;
import java.time.LocalDate;

public interface WeeklyReportService {
    WeeklyHealthReport generateWeeklyReport(Long patientId, LocalDate weekStart);
    void recalculateIfExists(Long patientId, LocalDate weekStart);
    void syncWeeklyReport(Long patientId, LocalDate weekStart);
    CustomRangeResult computeCustomRange(Long patientId, LocalDate from, LocalDate to);
}
