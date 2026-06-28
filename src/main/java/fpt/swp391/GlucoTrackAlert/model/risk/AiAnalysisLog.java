package fpt.swp391.GlucoTrackAlert.model.risk;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_analysis_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiAnalysisLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "daily_health_log_id")
    private Long dailyHealthLogId;

    @Column(name = "weekly_report_id")
    private Long weeklyReportId;

    @Column(name = "analysis_type")
    private String analysisType;

    @Column(name = "input_data", columnDefinition = "TEXT")
    private String inputData;

    @Column(name = "output_result", columnDefinition = "TEXT")
    private String outputResult;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}