package fpt.swp391.GlucoTrackAlert.model.risk;

import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "risk_warnings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RiskWarning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "risk_assessment_id")
    private Long riskAssessmentId;

    @Column(name = "daily_health_log_id")
    private Long dailyHealthLogId;

    @Column(name = "risk_type")
    private String riskType;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "risk_percentage", precision = 5, scale = 2)
    private BigDecimal riskPercentage;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "status")
    @Builder.Default
    private String status = "new";

    @Column(name = "notified")
    @Builder.Default
    private Boolean notified = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}