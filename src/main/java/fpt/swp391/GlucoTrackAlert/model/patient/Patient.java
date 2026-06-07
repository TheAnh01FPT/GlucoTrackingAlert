package fpt.swp391.GlucoTrackAlert.model.patient;

import fpt.swp391.GlucoTrackAlert.model.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "patients")
<<<<<<< HEAD
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
=======
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
>>>>>>> a1aaa242d7291ef7cdf29e1bb5acf5dea9d311b0
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

<<<<<<< HEAD
    @Column(name = "patient_type", length = 20)
    private String patientType;

=======
>>>>>>> a1aaa242d7291ef7cdf29e1bb5acf5dea9d311b0
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
<<<<<<< HEAD
        updatedAt = LocalDateTime.now();
=======
>>>>>>> a1aaa242d7291ef7cdf29e1bb5acf5dea9d311b0
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
