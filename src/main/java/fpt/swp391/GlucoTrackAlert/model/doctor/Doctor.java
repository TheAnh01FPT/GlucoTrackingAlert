package fpt.swp391.GlucoTrackAlert.model.doctor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "doctors")
@Getter
@Setter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // FIX 4: Đổi Integer → Long để nhất quán với các entity khác
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String fullName;

    private String specialization;
    private String degree;
    private Integer experienceYears;
    private String workplace;
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String introduction;

    private String avatarUrl;

    @Column(unique = true)
    private String nationalId;       // Căn cước công dân

    private String practiceLicense;  // Chứng chỉ hành nghề

    @Column(name = "national_id_image_url")
    private String nationalIdImageUrl;      // Ảnh CCCD

    @Column(name = "practice_license_image_url")
    private String practiceLicenseImageUrl; // Ảnh chứng chỉ hành nghề
    private String status = "active";
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
    
    @Column(name = "pending_verification_json" , columnDefinition = "TEXT")
    private String pendingVerificationJson;
}