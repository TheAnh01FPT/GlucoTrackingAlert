package fpt.swp391.GlucoTrackAlert.repository.risk;

import fpt.swp391.GlucoTrackAlert.model.risk.AiAnalysisLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiAnalysisLogRepository extends JpaRepository<AiAnalysisLog, Long> {
}