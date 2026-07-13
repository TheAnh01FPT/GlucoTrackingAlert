package fpt.swp391.GlucoTrackAlert.doctor;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "doctor_introductions")
@Getter
@Setter
public class DoctorIntroduction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doctor_id")
    private Long doctorId;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "title", length = 150)
    private String title;          // Học vị: BS.CKII, ThS.BS, ...

    @Column(name = "specialization", length = 255)
    private String specialization; // Chuyên khoa: Nội tiết, Tim mạch, ...

    @Column(name = "introduction", columnDefinition = "TEXT")
    private String introduction;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @Column(name = "status", length = 30)
    private String status = "active"; // "active" | "inactive"

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "active";
        if (displayOrder == null) displayOrder = 0;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper cho Thymeleaf / controller
    public boolean isActive() {
        return "active".equalsIgnoreCase(status);
    }
}