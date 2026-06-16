package fpt.swp391.GlucoTrackAlert.repository;

import fpt.swp391.GlucoTrackAlert.model.Duy_HealthReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface Duy_ReminderRepository extends JpaRepository<Duy_HealthReminder, Long> {

    List<Duy_HealthReminder> findByPatientIdOrderByReminderTimeAsc(Long patientId);

    List<Duy_HealthReminder> findByPatientIdAndStatusOrderByReminderTimeAsc(Long patientId, String status);

    List<Duy_HealthReminder> findByPatientIdAndReminderTypeOrderByReminderTimeAsc(Long patientId, String reminderType);

    // Lấy các reminder sắp tới (trong 24h)
    @Query("SELECT r FROM Duy_HealthReminder r WHERE r.patientId = :patientId " +
           "AND r.reminderTime BETWEEN :now AND :next24h " +
           "AND r.status = 'ACTIVE' ORDER BY r.reminderTime ASC")
    List<Duy_HealthReminder> findUpcomingReminders(
            @Param("patientId") Long patientId,
            @Param("now") LocalDateTime now,
            @Param("next24h") LocalDateTime next24h);

    // Đếm reminder active của patient
    long countByPatientIdAndStatus(Long patientId, String status);

    // Tìm reminder chưa được gửi và đã đến giờ
    @Query("SELECT r FROM Duy_HealthReminder r WHERE r.isSent = false " +
           "AND r.status = 'ACTIVE' AND r.reminderTime <= :now")
    List<Duy_HealthReminder> findDueReminders(@Param("now") LocalDateTime now);
}
