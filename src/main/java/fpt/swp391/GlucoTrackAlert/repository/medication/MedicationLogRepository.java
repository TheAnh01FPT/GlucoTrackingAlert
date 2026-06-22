package fpt.swp391.GlucoTrackAlert.repository.medication;

import fpt.swp391.GlucoTrackAlert.model.medication.MedicationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MedicationLogRepository extends JpaRepository<MedicationLog, Long> {
    List<MedicationLog> findByPatientIdOrderByScheduledTimeAsc(Long patientId);

    @Query("SELECT m FROM MedicationLog m WHERE m.patient.id = :patientId " +
           "AND m.scheduledTime BETWEEN :start AND :end ORDER BY m.scheduledTime ASC")
    List<MedicationLog> findByPatientAndDateRange(
            @Param("patientId") Long patientId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    long countByPatientIdAndStatus(Long patientId, String status);
    long countByPatientId(Long patientId);
    List<MedicationLog> findByPrescriptionItemIdOrderByScheduledTimeAsc(Long prescriptionItemId);
}