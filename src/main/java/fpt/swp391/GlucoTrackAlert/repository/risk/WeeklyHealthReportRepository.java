package fpt.swp391.GlucoTrackAlert.repository.risk;

import fpt.swp391.GlucoTrackAlert.model.risk.WeeklyHealthReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeeklyHealthReportRepository extends JpaRepository<WeeklyHealthReport, Long> {
    boolean existsByPatientIdAndWeekStart(Long patientId, LocalDate weekStart);
    Optional<WeeklyHealthReport> findByPatientIdAndWeekStart(Long patientId, LocalDate weekStart);
    List<WeeklyHealthReport> findByPatientIdOrderByWeekStartDesc(Long patientId);
    List<WeeklyHealthReport> findByWeekStartOrderByPatientId(LocalDate weekStart);
}
