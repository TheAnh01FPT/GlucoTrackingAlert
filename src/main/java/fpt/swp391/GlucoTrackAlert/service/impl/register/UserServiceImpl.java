package fpt.swp391.GlucoTrackAlert.service.impl.register;

import fpt.swp391.GlucoTrackAlert.dto.register.RegisterRequest;
import fpt.swp391.GlucoTrackAlert.model.EmailVerificationToken;
import fpt.swp391.GlucoTrackAlert.model.Role;
import fpt.swp391.GlucoTrackAlert.model.User;
import fpt.swp391.GlucoTrackAlert.repository.EmailVerificationTokenRepository;
import fpt.swp391.GlucoTrackAlert.repository.role.RoleRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.UserService;
import fpt.swp391.GlucoTrackAlert.service.EmailService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailService emailService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserServiceImpl(UserRepository userRepository, RoleRepository roleRepository, EmailVerificationTokenRepository tokenRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public User register(RegisterRequest request) throws Exception {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new Exception("Email already in use");
        }
        Role role = roleRepository.findByName(request.getRole()).orElseThrow(() -> new Exception("Role not found"));
        User u = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .status("pending_verification")
                .emailVerified(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        User saved = userRepository.save(u);

        // create verification token
        String token = UUID.randomUUID().toString();
        EmailVerificationToken ev = EmailVerificationToken.builder()
                .user(saved)
                .verificationToken(token)
                .expiredAt(LocalDateTime.now().plusDays(1))
                .status("pending")
                .createdAt(LocalDateTime.now())
                .build();
        tokenRepository.save(ev);

        // send email
        String link = "http://localhost:8080/api/auth/verify?token=" + token;
        String body = "Please verify your account by clicking: " + link;
        emailService.sendSimpleMessage(saved.getEmail(), "Verify your account", body);

        return saved;
    }

    @Override
    @Transactional
    public User activateUser(String token) throws Exception {
        EmailVerificationToken ev = tokenRepository.findByVerificationToken(token).orElseThrow(() -> new Exception("Invalid token"));
        if (ev.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new Exception("Token expired");
        }
        User u = ev.getUser();
        u.setEmailVerified(true);
        u.setStatus("active");
        u.setUpdatedAt(LocalDateTime.now());
        userRepository.save(u);
        ev.setVerifiedAt(LocalDateTime.now());
        ev.setStatus("verified");
        tokenRepository.save(ev);
        return u;
    }
}

