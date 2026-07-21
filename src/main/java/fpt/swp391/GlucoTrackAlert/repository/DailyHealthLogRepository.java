package fpt.swp391.GlucoTrackAlert.repository;

import fpt.swp391.GlucoTrackAlert.model.DailyHealthLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyHealthLogRepository extends JpaRepository<DailyHealthLog, Long> {
    Page<DailyHealthLog> findByPatientIdOrderByLogDateDesc(Long patientId, Pageable pageable);
    List<DailyHealthLog> findByPatientIdAndLogDateBetweenOrderByLogDate(Long patientId, LocalDate from, LocalDate to);
    List<DailyHealthLog> findByPatientIdAndLogDateBetween(Long patientId, LocalDate start, LocalDate end);
    DailyHealthLog findFirstByPatientIdOrderByLogDateAsc(Long patientId);
    DailyHealthLog findFirstByPatientIdOrderByLogDateDesc(Long patientId);
    boolean existsByPatientIdAndLogDate(Long patientId, LocalDate logDate);
    boolean existsByPatientIdAndLogDateAndIdNot(Long patientId, LocalDate logDate, Long id);
    long countByPatientIdAndLogDateBetween(Long patientId, LocalDate start, LocalDate end);
}