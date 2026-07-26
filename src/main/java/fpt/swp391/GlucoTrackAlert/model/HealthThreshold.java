package fpt.swp391.GlucoTrackAlert.model;

import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "health_thresholds",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"patient_id", "patient_type", "metric_type"}))
@Getter
@Setter
public class HealthThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "patient_type", nullable = false)
    private String patientType;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false)
    private fpt.swp391.GlucoTrackAlert.enums.MetricType metricType;

    @Column(nullable = false)
    private BigDecimal normalMin;

    @Column(nullable = false)
    private BigDecimal normalMax;

    @Column(nullable = false)
    private BigDecimal warningMin;

    @Column(nullable = false)
    private BigDecimal warningMax;

    private String description;
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient; // null = ngưỡng mặc định, có giá trị = ngưỡng riêng của bệnh nhân

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}