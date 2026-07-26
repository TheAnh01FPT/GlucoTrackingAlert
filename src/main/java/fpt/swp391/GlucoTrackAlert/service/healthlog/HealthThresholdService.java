package fpt.swp391.GlucoTrackAlert.service.healthlog;

import fpt.swp391.GlucoTrackAlert.model.healthlog.HealthThreshold;
import fpt.swp391.GlucoTrackAlert.model.healthlog.HealthThresholdHistory;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.healthlog.HealthThresholdHistoryRepository;
import fpt.swp391.GlucoTrackAlert.repository.healthlog.HealthThresholdRepository;
import fpt.swp391.GlucoTrackAlert.enums.MetricType;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class HealthThresholdService {

    @Autowired
    private HealthThresholdRepository thresholdRepository;

    @Autowired
    private fpt.swp391.GlucoTrackAlert.repository.healthlog.HealthThresholdHistoryRepository thresholdHistoryRepository;

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
            String description, Patient patient, User changedBy, String changeNote) {

        validateRange(normalMin, normalMax, warningMin, warningMax);

        HealthThreshold existing = thresholdRepository
            .findByPatientIdAndMetricType(patientId, metricType)
            .orElse(null);

        boolean isNew = (existing == null);
        HealthThreshold threshold = isNew ? new HealthThreshold() : existing;

        BigDecimal oldNormalMin = null;
        BigDecimal oldNormalMax = null;
        BigDecimal oldWarningMin = null;
        BigDecimal oldWarningMax = null;
        String oldDescription = null;

        if (isNew) {
            Optional<HealthThreshold> defaultThreshold = resolveThreshold(patientId, patient != null ? patient.getPatientType() : null, metricType);
            if (defaultThreshold.isPresent()) {
                HealthThreshold oldDefault = defaultThreshold.get();
                oldNormalMin = oldDefault.getNormalMin();
                oldNormalMax = oldDefault.getNormalMax();
                oldWarningMin = oldDefault.getWarningMin();
                oldWarningMax = oldDefault.getWarningMax();
                oldDescription = oldDefault.getDescription();
            }
        } else {
            oldNormalMin = existing.getNormalMin();
            oldNormalMax = existing.getNormalMax();
            oldWarningMin = existing.getWarningMin();
            oldWarningMax = existing.getWarningMax();
            oldDescription = existing.getDescription();
        }

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
        threshold.setUpdatedBy(changedBy);

        HealthThreshold saved = thresholdRepository.save(threshold);

        boolean changed = isNew || (
            !Objects.equals(oldNormalMin, normalMin)
            || !Objects.equals(oldNormalMax, normalMax)
            || !Objects.equals(oldWarningMin, warningMin)
            || !Objects.equals(oldWarningMax, warningMax)
            || !Objects.equals(oldDescription, description)
        );

        if (changed) {
            HealthThresholdHistory history = new HealthThresholdHistory();
            history.setThreshold(saved);
            history.setPatientType(saved.getPatientType());
            history.setMetricType(saved.getMetricType());
            history.setPatient(saved.getPatient());

            history.setOldNormalMin(oldNormalMin);
            history.setOldNormalMax(oldNormalMax);
            history.setOldWarningMin(oldWarningMin);
            history.setOldWarningMax(oldWarningMax);
            history.setOldDescription(oldDescription);

            history.setNewNormalMin(normalMin);
            history.setNewNormalMax(normalMax);
            history.setNewWarningMin(warningMin);
            history.setNewWarningMax(warningMax);
            history.setNewDescription(description);

            history.setChangedBy(changedBy);
            history.setChangedAt(LocalDateTime.now());
            history.setChangeNote(changeNote);
            thresholdHistoryRepository.save(history);
        }

        return saved;
    }

    // Xóa ngưỡng riêng — reset về dùng ngưỡng mặc định
    public void deletePatientThreshold(Long patientId, MetricType metricType, User changedBy) {
        thresholdRepository.findByPatientIdAndMetricType(patientId, metricType)
            .ifPresent(threshold -> {
                HealthThresholdHistory history = new HealthThresholdHistory();
                history.setThreshold(threshold);
                history.setPatientType(threshold.getPatientType());
                history.setMetricType(threshold.getMetricType());
                history.setPatient(threshold.getPatient());

                history.setOldNormalMin(threshold.getNormalMin());
                history.setOldNormalMax(threshold.getNormalMax());
                history.setOldWarningMin(threshold.getWarningMin());
                history.setOldWarningMax(threshold.getWarningMax());
                history.setOldDescription(threshold.getDescription());

                history.setNewNormalMin(null);
                history.setNewNormalMax(null);
                history.setNewWarningMin(null);
                history.setNewWarningMax(null);
                history.setNewDescription(null);

                history.setChangedBy(changedBy);
                history.setChangedAt(LocalDateTime.now());
                history.setChangeNote("Reset về ngưỡng mặc định");
                thresholdHistoryRepository.save(history);

                thresholdRepository.delete(threshold);
            });
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