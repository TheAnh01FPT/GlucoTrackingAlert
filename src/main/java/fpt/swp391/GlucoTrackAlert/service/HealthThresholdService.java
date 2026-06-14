package fpt.swp391.GlucoTrackAlert.service;

import fpt.swp391.GlucoTrackAlert.model.HealthThreshold;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.repository.HealthThresholdRepository;
import fpt.swp391.GlucoTrackAlert.enums.MetricType;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class HealthThresholdService {

    @Autowired
    private HealthThresholdRepository thresholdRepository;

    public String evaluate(BigDecimal value, Long patientId, String patientType, MetricType metricType) {
        if (value == null) return "unknown";

        Optional<HealthThreshold> opt = Optional.empty();

        // Ưu tiên 1: ngưỡng riêng của bệnh nhân này
        if (patientId != null) {
            opt = thresholdRepository.findByPatientIdAndMetricType(patientId, metricType);
        }

        // Ưu tiên 2: ngưỡng mặc định theo patientType
        if (opt.isEmpty()) {
            String type = (patientType != null && !patientType.isBlank()) ? patientType : "adult";
            opt = thresholdRepository.findByPatientTypeAndMetricTypeAndPatientIsNull(type, metricType);
        }

        // Ưu tiên 3: fallback về ngưỡng mặc định adult
        if (opt.isEmpty()) {
            opt = thresholdRepository.findByPatientTypeAndMetricTypeAndPatientIsNull("adult", metricType);
        }

        if (opt.isEmpty()) return "unknown";

        HealthThreshold t = opt.get();
        double v = value.doubleValue();

        double normalMin = t.getNormalMin().doubleValue();
        double normalMax = t.getNormalMax().doubleValue();
        double warningMin = t.getWarningMin().doubleValue();
        double warningMax = t.getWarningMax().doubleValue();

        // Normal
        if (v >= normalMin && v <= normalMax) return "NORMAL";

        // Low side
        if (v < normalMin) {
            if (v >= warningMin) return "LOW_WARNING"; // in warning band below normal
            return "LOW_DANGER"; // below warning
        }

        // High side
        if (v > normalMax) {
            if (v <= warningMax) return "HIGH_WARNING"; // in warning band above normal
            return "HIGH_DANGER"; // above warning
        }

        return "UNKNOWN";
    }

    public List<HealthThreshold> findAll() {
        return thresholdRepository.findAllByOrderByPatientTypeAscMetricTypeAsc();
    }

    public Optional<HealthThreshold> findById(Long id) {
        return thresholdRepository.findById(id);
    }

    @CacheEvict(value = "defaultThresholds", allEntries = true)
    public HealthThreshold save(HealthThreshold threshold) {
        return thresholdRepository.save(threshold);
    }

    // Lấy tất cả ngưỡng riêng của 1 bệnh nhân
    public List<HealthThreshold> findByPatientId(Long patientId) {
        return thresholdRepository.findAllByPatientId(patientId);
    }

    // Lấy tất cả ngưỡng mặc định (cho admin xem)
    @Cacheable(value = "defaultThresholds")
    public List<HealthThreshold> findDefaults() {
        return thresholdRepository.findAllByPatientIsNull();
    }

    @Cacheable(value = "defaultThresholds", key = "#patientType + '_' + #metricType")
    public Optional<HealthThreshold> findDefaultByPatientTypeAndMetricType(String patientType, MetricType metricType) {
        return thresholdRepository.findByPatientTypeAndMetricTypeAndPatientIsNull(patientType, metricType);
    }

    // Lưu hoặc cập nhật ngưỡng riêng cho bệnh nhân
    public HealthThreshold savePatientThreshold(Long patientId, MetricType metricType,
            BigDecimal normalMin, BigDecimal normalMax,
            BigDecimal warningMin, BigDecimal warningMax,
            String description, Patient patient) {

        validateRange(normalMin, normalMax, warningMin, warningMax);

        HealthThreshold threshold = thresholdRepository
            .findByPatientIdAndMetricType(patientId, metricType)
            .orElse(new HealthThreshold());

        threshold.setPatient(patient);
        if (patient != null) {
            threshold.setPatientType(patient.getPatientType());
        }
        threshold.setMetricType(metricType);
        threshold.setNormalMin(normalMin);
        threshold.setNormalMax(normalMax);
        threshold.setWarningMin(warningMin);
        threshold.setWarningMax(warningMax);
        threshold.setDescription(description);
        return thresholdRepository.save(threshold);
    }

    // Xóa ngưỡng riêng — reset về dùng ngưỡng mặc định
    public void deletePatientThreshold(Long patientId, MetricType metricType) {
        thresholdRepository.findByPatientIdAndMetricType(patientId, metricType)
            .ifPresent(thresholdRepository::delete);
    }

    public void validateRange(BigDecimal normalMin, BigDecimal normalMax, BigDecimal warningMin, BigDecimal warningMax) {
        if (normalMin == null || normalMax == null || warningMin == null || warningMax == null) {
            throw new IllegalArgumentException("Các giá trị không được để trống");
        }
        if (normalMin.signum() < 0 || normalMax.signum() < 0 || warningMin.signum() < 0 || warningMax.signum() < 0) {
            throw new IllegalArgumentException("Các giá trị phải lớn hơn hoặc bằng 0");
        }
        if (normalMin.compareTo(normalMax) >= 0) {
            throw new IllegalArgumentException("normalMin phải nhỏ hơn normalMax");
        }
        if (warningMin.compareTo(warningMax) >= 0) {
            throw new IllegalArgumentException("warningMin phải nhỏ hơn warningMax");
        }
        // Ensure warning covers normal (no gap)
        if (warningMin.compareTo(normalMin) > 0) {
            throw new IllegalArgumentException("warningMin phải <= normalMin để không có khoảng trống giữa normal và warning (phía thấp)");
        }
        if (warningMax.compareTo(normalMax) < 0) {
            throw new IllegalArgumentException("warningMax phải >= normalMax để không có khoảng trống giữa normal và warning (phía cao)");
        }
    }

    // Helper to resolve threshold once (used by bulk mapping to avoid N+1)
    public Optional<HealthThreshold> resolveThreshold(Long patientId, String patientType, MetricType metricType) {
        Optional<HealthThreshold> opt = Optional.empty();
        if (patientId != null) {
            opt = thresholdRepository.findByPatientIdAndMetricType(patientId, metricType);
        }
        if (opt.isEmpty()) {
            String type = (patientType != null && !patientType.isBlank()) ? patientType : "adult";
            opt = findDefaultByPatientTypeAndMetricType(type, metricType);
        }
        if (opt.isEmpty()) {
            opt = findDefaultByPatientTypeAndMetricType("adult", metricType);
        }
        return opt;
    }
}