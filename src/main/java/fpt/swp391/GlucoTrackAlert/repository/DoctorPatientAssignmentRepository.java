package fpt.swp391.GlucoTrackAlert.repository;

import fpt.swp391.GlucoTrackAlert.model.DoctorPatientAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DoctorPatientAssignmentRepository
        extends JpaRepository<DoctorPatientAssignment, Integer> {

    List<DoctorPatientAssignment> findByDoctorIdAndStatus(Integer doctorId, String status);

    List<DoctorPatientAssignment> findByDoctorId(Integer doctorId);

    boolean existsByPatientIdAndStatus(Long patientId, String status);

    List<DoctorPatientAssignment> findByPatientIdAndStatus(Long patientId, String status);

    Optional<DoctorPatientAssignment> findByDoctorIdAndPatientId(Integer doctorId, Long patientId);

    long countByDoctorIdAndStatus(Integer doctorId, String status);
}