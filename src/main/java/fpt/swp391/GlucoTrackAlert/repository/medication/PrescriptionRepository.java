package fpt.swp391.GlucoTrackAlert.repository.medication;

import fpt.swp391.GlucoTrackAlert.model.medication.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {
    List<Prescription> findByPatientIdOrderByPrescribedDateDesc(Long patientId);
    List<Prescription> findByPatientIdAndStatusOrderByPrescribedDateDesc(Long patientId, String status);
    List<Prescription> findByDoctorIdOrderByPrescribedDateDesc(Long doctorId);

    @Query("SELECT p FROM Prescription p WHERE p.patient.id = :patientId AND p.status = 'ACTIVE' ORDER BY p.prescribedDate DESC")
    List<Prescription> findActivePrescriptionsByPatient(@Param("patientId") Long patientId);
}