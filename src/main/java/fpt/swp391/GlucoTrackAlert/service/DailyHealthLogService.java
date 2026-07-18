package fpt.swp391.GlucoTrackAlert.service;

import fpt.swp391.GlucoTrackAlert.dto.healthlog.DailyHealthLogRequest;
import fpt.swp391.GlucoTrackAlert.dto.healthlog.DailyHealthLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface DailyHealthLogService {
    Page<DailyHealthLogResponse> getLogs(Long patientId, Pageable pageable);
    DailyHealthLogResponse getLogById(Long id);
    DailyHealthLogResponse createLog(Long patientId, DailyHealthLogRequest request);
    DailyHealthLogResponse updateLog(Long id, DailyHealthLogRequest request);
    void deleteLog(Long id);
    List<DailyHealthLogResponse> getChartData(Long patientId, LocalDate from, LocalDate to);
    void assessWeeklyRisk(Long patientId);
    Map<String, Object> calculateDynamicRisk(Long patientId, LocalDate from, LocalDate to);
    Map<String, Object> calculateDynamicHeartRisk(Long patientId, LocalDate from, LocalDate to);
}
