package fpt.swp391.GlucoTrackAlert.repository.register;

import fpt.swp391.GlucoTrackAlert.model.register.EmailVerificationToken;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import fpt.swp391.GlucoTrackAlert.model.user.User;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByVerificationToken(String token);

    Optional<EmailVerificationToken> findFirstByVerificationTokenEndingWithAndStatus(String suffix, String status);

    List<EmailVerificationToken> findByUser(User user);
}