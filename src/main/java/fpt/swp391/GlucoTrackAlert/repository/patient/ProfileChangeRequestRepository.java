package fpt.swp391.GlucoTrackAlert.repository.patient;

import fpt.swp391.GlucoTrackAlert.model.patient.ProfileChangeRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProfileChangeRequestRepository extends JpaRepository<ProfileChangeRequest, Long> {
    
    List<ProfileChangeRequest> findByPatientId(Long patientId);
    
    List<ProfileChangeRequest> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    Page<ProfileChangeRequest> findByPatientId(Long patientId, Pageable pageable);
    
    boolean existsByPatientIdAndFieldNameAndStatus(Long patientId, String fieldName, String status);
    
    List<ProfileChangeRequest> findAllByOrderByCreatedAtDesc();
}
