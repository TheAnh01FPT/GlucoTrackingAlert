package fpt.swp391.GlucoTrackAlert.model;

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
}