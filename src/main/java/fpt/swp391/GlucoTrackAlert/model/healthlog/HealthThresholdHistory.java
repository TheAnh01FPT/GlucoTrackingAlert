package fpt.swp391.GlucoTrackAlert.model.healthlog;

import fpt.swp391.GlucoTrackAlert.enums.MetricType;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "health_threshold_history")
@Getter
@Setter
public class HealthThresholdHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "threshold_id")
    private HealthThreshold threshold;

    @Column(name = "patient_type")
    private String patientType;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type")
    private MetricType metricType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "old_normal_min")
    private BigDecimal oldNormalMin;
    @Column(name = "old_normal_max")
    private BigDecimal oldNormalMax;
    @Column(name = "old_warning_min")
    private BigDecimal oldWarningMin;
    @Column(name = "old_warning_max")
    private BigDecimal oldWarningMax;
    @Column(name = "old_description")
    private String oldDescription;

    @Column(name = "new_normal_min")
    private BigDecimal newNormalMin;
    @Column(name = "new_normal_max")
    private BigDecimal newNormalMax;
    @Column(name = "new_warning_min")
    private BigDecimal newWarningMin;
    @Column(name = "new_warning_max")
    private BigDecimal newWarningMax;
    @Column(name = "new_description")
    private String newDescription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(name = "changed_at")
    private LocalDateTime changedAt;

    @Column(name = "change_note")
    private String changeNote;
}
