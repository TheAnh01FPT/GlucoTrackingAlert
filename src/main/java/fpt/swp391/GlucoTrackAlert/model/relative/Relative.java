package fpt.swp391.GlucoTrackAlert.model.relative;

import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "relatives")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Relative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 50)
    private String relationship;

    private Integer age;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "notify_enabled")
    @Builder.Default
    private Boolean notifyEnabled = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
