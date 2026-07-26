package fpt.swp391.GlucoTrackAlert.repository.risk;

import fpt.swp391.GlucoTrackAlert.model.risk.AiAnalysisLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AiAnalysisLogRepository extends JpaRepository<AiAnalysisLog, Long> {

	Optional<AiAnalysisLog> findByWeeklyReportIdAndAnalysisType(Long weeklyReportId, String analysisType);
	/**
	 * Move references from an old weekly report id to a new one.
	 * Deprecated: kept for rollback scripts only. Prefer service-level migration.
	 */
	@Deprecated
	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("update AiAnalysisLog a set a.weeklyReportId = :newId where a.weeklyReportId = :oldId")
	void moveWeeklyReportReferences(@org.springframework.data.repository.query.Param("oldId") Long oldId,
									@org.springframework.data.repository.query.Param("newId") Long newId);
}