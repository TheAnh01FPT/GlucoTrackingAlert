package fpt.swp391.GlucoTrackAlert.service;

import fpt.swp391.GlucoTrackAlert.model.risk.WeeklyHealthReport;
import java.time.LocalDate;

public interface WeeklyReportService {
    WeeklyHealthReport generateWeeklyReport(Long patientId, LocalDate weekStart);
    void recalculateIfExists(Long patientId, LocalDate weekStart);
}
