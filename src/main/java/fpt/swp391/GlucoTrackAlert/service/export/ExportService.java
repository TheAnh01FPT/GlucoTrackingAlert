package fpt.swp391.GlucoTrackAlert.service.export;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;

public interface ExportService {
    ByteArrayInputStream exportDailyLogsToExcel(Long patientId, LocalDate fromDate, LocalDate toDate);
}
