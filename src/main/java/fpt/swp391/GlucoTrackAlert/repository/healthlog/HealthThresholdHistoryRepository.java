package fpt.swp391.GlucoTrackAlert.repository.healthlog;

import fpt.swp391.GlucoTrackAlert.model.healthlog.HealthThresholdHistory;
import fpt.swp391.GlucoTrackAlert.enums.MetricType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface HealthThresholdHistoryRepository extends JpaRepository<HealthThresholdHistory, Long> {
    List<HealthThresholdHistory> findByPatientIdAndMetricTypeOrderByChangedAtDesc(Long patientId, MetricType metricType);
    Page<HealthThresholdHistory> findByPatientIdAndMetricTypeOrderByChangedAtDesc(Long patientId, MetricType metricType, Pageable pageable);
}
