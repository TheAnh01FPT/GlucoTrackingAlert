package fpt.swp391.GlucoTrackAlert.model.risk;

import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "weekly_health_reports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WeeklyHealthReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "week_start")
    private LocalDate weekStart;

    @Column(name = "week_end")
    private LocalDate weekEnd;

    @Column(name = "average_blood_sugar", precision = 10, scale = 2)
    private BigDecimal averageBloodSugar;

    @Column(name = "average_systolic", precision = 10, scale = 2)
    private BigDecimal averageSystolic;

    @Column(name = "average_diastolic", precision = 10, scale = 2)
    private BigDecimal averageDiastolic;

    @Column(name = "health_status")
    private String healthStatus;

    @Column(name = "recommendation", columnDefinition = "TEXT")
    private String recommendation;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "risk_assessment_id")
    private Long riskAssessmentId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "risk_percentage", precision = 5, scale = 2)
    private BigDecimal riskPercentage;

    @Column(name = "low_confidence")
    private Boolean lowConfidence;
}
