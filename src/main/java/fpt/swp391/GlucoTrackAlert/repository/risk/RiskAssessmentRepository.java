package fpt.swp391.GlucoTrackAlert.repository.risk;

import fpt.swp391.GlucoTrackAlert.model.risk.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {
    Optional<RiskAssessment> findTopByPatient_IdAndAssessmentTypeOrderByAssessedAtDesc(
        Long patientId, String assessmentType);
}