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

    // Chỉ lấy log của các đơn thuốc CHƯA bị huỷ (CANCELLED)
    // để bệnh nhân không thấy lịch uống của đơn mà bác sĩ đã cancel
    @Query("SELECT m FROM MedicationLog m " +
           "JOIN m.prescriptionItem pi " +
           "JOIN pi.prescription p " +
           "WHERE m.patient.id = :patientId " +
           "AND m.scheduledTime BETWEEN :start AND :end " +
           "AND p.status <> 'CANCELLED' " +
           "ORDER BY m.scheduledTime ASC")
    List<MedicationLog> findByPatientAndDateRange(
            @Param("patientId") Long patientId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    long countByPatientIdAndStatus(Long patientId, String status);
    long countByPatientId(Long patientId);
    List<MedicationLog> findByPrescriptionItemIdOrderByScheduledTimeAsc(Long prescriptionItemId);
}