package fpt.swp391.GlucoTrackAlert.service.impl.register;

import fpt.swp391.GlucoTrackAlert.dto.login.LoginRequest;
import fpt.swp391.GlucoTrackAlert.dto.login.LoginResponse;
import fpt.swp391.GlucoTrackAlert.dto.register.RegisterRequest;
import fpt.swp391.GlucoTrackAlert.model.register.EmailVerificationToken;
import fpt.swp391.GlucoTrackAlert.model.role.Role;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.register.EmailVerificationTokenRepository;
import fpt.swp391.GlucoTrackAlert.repository.role.RoleRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.register.EmailService;
import fpt.swp391.GlucoTrackAlert.service.register.UserService;
import fpt.swp391.GlucoTrackAlert.util.jwt.JwtUtil;
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
    private final BCryptPasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;

    public UserServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           EmailVerificationTokenRepository tokenRepository,
                           BCryptPasswordEncoder passwordEncoder,
                           EmailService emailService,
                           JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.jwtUtil = jwtUtil;
    }

    @Override
    @Transactional
    public User register(RegisterRequest request) throws Exception {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new Exception("Email '" + request.getEmail() + "' đã được đăng ký.");
        }

        Role patientRole = roleRepository.findByName("PATIENT")
                .orElseThrow(() -> new Exception("Không tìm thấy role PATIENT trong hệ thống."));

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(patientRole)
                .status("pending_verification")
                .emailVerified(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);

        // Tạo token xác thực email
        String token = UUID.randomUUID().toString();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .verificationToken(token)
                .expiredAt(LocalDateTime.now().plusHours(24))
                .status("pending")
                .createdAt(LocalDateTime.now())
                .build();
        tokenRepository.save(verificationToken);

        // Gửi email xác thực
        String subject = "Xác nhận đăng ký tài khoản GlucoTrack";
        String htmlContent = "<h3>Xin chào " + request.getFullName() + ",</h3>"
                + "<p>Vui lòng nhấn vào link bên dưới để xác nhận email của bạn:</p>"
                + "<a href='http://localhost:8081/api/auth/verify-otp?otp=" + token + "'>Xác nhận email</a>"
                + "<p>Link có hiệu lực trong 24 giờ.</p>";
        emailService.sendHtmlMessage(request.getEmail(), subject, htmlContent);

        return user;
    }

    @Override
    @Transactional
    public User activateUser(String token) throws Exception {
        EmailVerificationToken verificationToken = tokenRepository.findByVerificationToken(token)
                .orElseThrow(() -> new Exception("Mã xác nhận không hợp lệ."));

        if (verificationToken.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new Exception("Mã xác nhận đã hết hạn. Vui lòng đăng ký lại.");
        }

        if ("verified".equals(verificationToken.getStatus())) {
            throw new Exception("Tài khoản đã được xác nhận trước đó.");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        user.setStatus("active");
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        verificationToken.setStatus("verified");
        verificationToken.setVerifiedAt(LocalDateTime.now());
        tokenRepository.save(verificationToken);

        return user;
    }

    @Override
    public LoginResponse login(LoginRequest request) throws Exception {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new Exception("Email hoặc mật khẩu không đúng."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new Exception("Email hoặc mật khẩu không đúng.");
        }

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new Exception("Tài khoản chưa được xác nhận email. Vui lòng kiểm tra hộp thư.");
        }

        if (!"active".equals(user.getStatus())) {
            throw new Exception("Tài khoản đã bị khóa hoặc chưa được kích hoạt.");
        }

        String roleName = user.getRole() != null ? user.getRole().getName() : "PATIENT";
        String token = jwtUtil.generateToken(user.getEmail(), roleName);

        return LoginResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role(roleName)
                .message("Đăng nhập thành công.")
                .build();
    }
}