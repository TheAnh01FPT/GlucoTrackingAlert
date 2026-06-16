package fpt.swp391.GlucoTrackAlert.repository.register;

import fpt.swp391.GlucoTrackAlert.model.register.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByVerificationToken(String token);

    Optional<EmailVerificationToken> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE EmailVerificationToken t SET t.status = 'expired' WHERE t.user.id = :userId AND t.status = 'pending'")
    void expireAllPendingByUserId(@Param("userId") Long userId);
}