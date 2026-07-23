package fpt.swp391.GlucoTrackAlert.repository.risk;

import fpt.swp391.GlucoTrackAlert.model.risk.RiskWarning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RiskWarningRepository extends JpaRepository<RiskWarning, Long> {
    List<RiskWarning> findByPatient_IdOrderByCreatedAtDesc(Long patientId);

    Optional<RiskWarning> findByRiskAssessmentIdAndRiskType(Long riskAssessmentId, String riskType);
}