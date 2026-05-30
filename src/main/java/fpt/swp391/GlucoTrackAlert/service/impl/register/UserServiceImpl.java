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
import fpt.swp391.GlucoTrackAlert.service.register.UserService;
import fpt.swp391.GlucoTrackAlert.service.register.EmailService;
import fpt.swp391.GlucoTrackAlert.util.jwt.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
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
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${app.frontend-url:http://localhost:8080}")
    private String frontendUrl;

    public UserServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           EmailVerificationTokenRepository tokenRepository,
                           EmailService emailService,
                           BCryptPasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    @Transactional
    public User register(RegisterRequest request) throws Exception {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new Exception("Email already in use");
        }
        if ("ADMIN".equalsIgnoreCase(request.getRole())) {
            throw new Exception("Registration as ADMIN is not allowed");
        }
        Role role = roleRepository.findByName(request.getRole())
                .orElseThrow(() -> new Exception("Role not found"));

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

        // send email - sử dụng frontendUrl từ config
        String link = frontendUrl + "/api/auth/verify?token=" + token;
        String body = buildVerificationEmail(saved.getEmail(), link);
        emailService.sendHtmlMessage(saved.getEmail(), "Xác nhận tài khoản GlucoTrackAlert", body);

        return saved;
    }

    @Override
    @Transactional
    public User activateUser(String token) throws Exception {
        EmailVerificationToken ev = tokenRepository.findByVerificationToken(token)
                .orElseThrow(() -> new Exception("Invalid token"));
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

    @Override
    public LoginResponse login(LoginRequest request) throws Exception {
        // 1. Tìm user theo email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new Exception("Email không tồn tại trong hệ thống"));

        // 2. Kiểm tra email đã xác thực chưa
        if (!user.getEmailVerified()) {
            throw new Exception("Tài khoản chưa xác thực email. Vui lòng kiểm tra hộp thư.");
        }

        // 3. Kiểm tra tài khoản có active không
        if (!"active".equalsIgnoreCase(user.getStatus())) {
            throw new Exception("Tài khoản đã bị khóa hoặc chưa kích hoạt.");
        }

        // 4. Kiểm tra mật khẩu
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new Exception("Mật khẩu không chính xác");
        }

        // 5. Cập nhật last_login_at
        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // 6. Tạo JWT token
        String roleName = user.getRole() != null ? user.getRole().getName() : "UNKNOWN";
        String token = jwtUtil.generateToken(user.getEmail(), roleName);

        return LoginResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role(roleName)
                .message("Đăng nhập thành công")
                .build();
    }
    private String buildVerificationEmail(String email, String link) {
        return """
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 30px; border: 1px solid #e0e0e0; border-radius: 8px;">
            <h2 style="color: #2c3e50; text-align: center;">GlucoTrackAlert</h2>
            <p style="color: #555;">Xin chào <strong>%s</strong>,</p>
            <p style="color: #555;">Cảm ơn bạn đã đăng ký tài khoản. Vui lòng nhấn nút bên dưới để xác nhận email và kích hoạt tài khoản:</p>
            <div style="text-align: center; margin: 30px 0;">
                <a href="%s"
                   style="background-color: #2ecc71; color: white; padding: 14px 28px;
                          text-decoration: none; border-radius: 5px; font-size: 16px;
                          display: inline-block;">
                    ✅ Xác nhận tài khoản
                </a>
            </div>
            <p style="color: #999; font-size: 13px;">Link này sẽ hết hạn sau <strong>24 giờ</strong>.</p>
            <p style="color: #999; font-size: 13px;">Nếu bạn không đăng ký tài khoản này, hãy bỏ qua email này.</p>
        </div>
        """.formatted(email, link);
    }
}
