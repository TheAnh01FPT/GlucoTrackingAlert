package fpt.swp391.GlucoTrackAlert.repository.user;

import fpt.swp391.GlucoTrackAlert.model.user.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByResetToken(String resetToken);
}
