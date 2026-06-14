package fpt.swp391.GlucoTrackAlert.repository;

import fpt.swp391.GlucoTrackAlert.model.DoctorPatientAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
// FIX 4: Đổi generic type từ Integer → Long
public interface DoctorPatientAssignmentRepository
        extends JpaRepository<DoctorPatientAssignment, Long> {

    List<DoctorPatientAssignment> findByDoctorIdAndStatus(Long doctorId, String status);

    List<DoctorPatientAssignment> findByDoctorId(Long doctorId);

    boolean existsByPatientIdAndStatus(Long patientId, String status);

    List<DoctorPatientAssignment> findByPatientIdAndStatus(Long patientId, String status);

    Optional<DoctorPatientAssignment> findByDoctorIdAndPatientId(Long doctorId, Long patientId);

    long countByDoctorIdAndStatus(Long doctorId, String status);
}