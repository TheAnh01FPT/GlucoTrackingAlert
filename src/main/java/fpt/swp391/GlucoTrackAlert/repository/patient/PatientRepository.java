package fpt.swp391.GlucoTrackAlert.repository.patient;

import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
<<<<<<< HEAD

import java.util.List;
=======
>>>>>>> a1aaa242d7291ef7cdf29e1bb5acf5dea9d311b0
import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByUserId(Long userId);
<<<<<<< HEAD
    List<Patient> findAllByStatus(String status);
    List<Patient> findAllByStatusAndPatientType(String status, String patientType);
=======
>>>>>>> a1aaa242d7291ef7cdf29e1bb5acf5dea9d311b0
}
