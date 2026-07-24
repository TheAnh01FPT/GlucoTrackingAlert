package fpt.swp391.GlucoTrackAlert.model.doctor;

import fpt.swp391.GlucoTrackAlert.model.doctor.Doctor;
import fpt.swp391.GlucoTrackAlert.enums.RecommendationCategory;
import fpt.swp391.GlucoTrackAlert.enums.RecommendationPriority;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "doctor_recommendations")
@Getter
@Setter
public class DoctorRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String recommendation;

    // active / inactive (soft delete)
    private String status = "active";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecommendationPriority priority = RecommendationPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecommendationCategory category = RecommendationCategory.OTHER;

    // Bệnh nhân đã xem khuyến nghị này chưa
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}