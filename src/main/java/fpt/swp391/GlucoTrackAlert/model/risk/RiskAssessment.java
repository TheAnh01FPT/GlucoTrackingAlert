package fpt.swp391.GlucoTrackAlert.model.risk;

import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "risk_assessments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "daily_health_log_id")
    private Long dailyHealthLogId;

    @Column(name = "weekly_report_id")
    private Long weeklyReportId;

    @Column(name = "assessment_type")
    private String assessmentType;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "risk_percentage", precision = 5, scale = 2)
    private BigDecimal riskPercentage;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "recommendation", columnDefinition = "TEXT")
    private String recommendation;

    @Column(name = "assessed_at")
    private LocalDateTime assessedAt;

    // Persisted flag to indicate inputs were incomplete/defaulted when assessment ran.
    @Column(name = "low_confidence")
    private Boolean lowConfidence = false;
}
