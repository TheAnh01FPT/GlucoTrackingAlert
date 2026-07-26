package fpt.swp391.GlucoTrackAlert.model.patient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    private Integer age;

    @Column(length = 20)
    private String gender;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(name = "height_cm", precision = 5, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(precision = 5, scale = 2)
    private BigDecimal bmi;

    @Column(length = 30)
    @Builder.Default
    private String status = "active";

    @Column(length = 500)
    private String avatar;

    @Column(name = "patient_type", length = 20)
    private String patientType;

    @Column(name = "identity_card", length = 20)
    private String identityCard;

    @Column(name = "identity_card_image", length = 500)
    private String identityCardImage;

    @Column(name = "identity_card_status", length = 20)
    @Builder.Default
    private String identityCardStatus = "UNVERIFIED";

    @Column(name = "insurance_number", length = 50)
    private String insuranceNumber;

    @Column(name = "insurance_number_image", length = 500)
    private String insuranceNumberImage;

    @Column(name = "insurance_card_status", length = 20)
    @Builder.Default
    private String insuranceCardStatus = "UNVERIFIED";

    @Column(name = "is_pregnant")
    @Builder.Default
    private Boolean isPregnant = false;

    @Column(name = "hypertension")
    @Builder.Default
    private Boolean hypertension = false;

    @Column(name = "heart_disease")
    @Builder.Default
    private Boolean heartDisease = false;

    @Column(name = "ever_married", length = 20)
    @Builder.Default
    private String everMarried = "No";

    @Column(name = "work_type", length = 50)
    private String workType;

    @Column(name = "residence_type", length = 20)
    private String residenceType;

    @Column(name = "smoking_status", length = 50)
    @Builder.Default
    private String smokingStatus = "Unknown";

    // --- CÁC TRƯỜNG THÓI QUEN SINH HOẠT VÀ NỀN DỊCH TỄ BIẾN ĐỔI ---
    private Integer cholesterol;

    private Integer smoke; // 0 hoặc 1, được đồng bộ tự động từ smokingStatus phục vụ Cardio AI

    private Integer alco;

    private Integer active;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
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