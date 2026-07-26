package fpt.swp391.GlucoTrackAlert.model.healthlog;

import jakarta.persistence.*;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_health_logs", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "patient_id", "log_date" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyHealthLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "blood_sugar", precision = 6, scale = 2)
    private BigDecimal bloodSugar; // mmol/L

    @Column(name = "systolic")
    private Integer systolic;

    @Column(name = "diastolic")
    private Integer diastolic;

    @Column(name = "sleep_hours", precision = 4, scale = 2)
    private BigDecimal sleepHours;

    @Column(name = "water_ml")
    private Integer waterMl;

    @Column(name = "sugar_consumption_level", length = 20)
    private String sugarConsumptionLevel;

    @Column(name = "symptoms", columnDefinition = "TEXT")
    private String symptoms;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "physical_activity")
    private Integer physicalActivity = 0; // Định nghĩa thuộc tính để Lombok tự sinh code Getter/Setter

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
