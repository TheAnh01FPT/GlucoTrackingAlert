package fpt.swp391.GlucoTrackAlert.model.register;

import fpt.swp391.GlucoTrackAlert.model.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "email_verifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, unique = true)
    private String verificationToken;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    private LocalDateTime verifiedAt;

    private String status = "pending";

    private LocalDateTime createdAt = LocalDateTime.now();
}

