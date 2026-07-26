package fpt.swp391.GlucoTrackAlert.repository.healthlog;

import fpt.swp391.GlucoTrackAlert.model.healthlog.HealthThreshold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface HealthThresholdRepository extends JpaRepository<HealthThreshold, Long> {
    Optional<HealthThreshold> findByPatientTypeAndMetricType(String patientType, fpt.swp391.GlucoTrackAlert.enums.MetricType metricType);
    List<HealthThreshold> findAllByPatientType(String patientType);
    List<HealthThreshold> findAllByOrderByPatientTypeAscMetricTypeAsc();

    // Ngưỡng riêng của 1 bệnh nhân cho 1 metricType
    Optional<HealthThreshold> findByPatientIdAndMetricType(Long patientId, fpt.swp391.GlucoTrackAlert.enums.MetricType metricType);

    // Tất cả ngưỡng riêng của 1 bệnh nhân
    List<HealthThreshold> findAllByPatientId(Long patientId);

    // Ngưỡng mặc định (patient = null) theo patientType và metricType
    Optional<HealthThreshold> findByPatientTypeAndMetricTypeAndPatientIsNull(String patientType, fpt.swp391.GlucoTrackAlert.enums.MetricType metricType);

    // Tất cả ngưỡng mặc định (patient = null)
    List<HealthThreshold> findAllByPatientIsNull();
}