package fpt.swp391.GlucoTrackAlert.repository.patient;

import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByUserId(Long userId);
    List<Patient> findAllByStatus(String status);
    List<Patient> findAllByStatusAndPatientType(String status, String patientType);
}
