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
import fpt.swp391.GlucoTrackAlert.repository.user.PasswordResetTokenRepository;
import fpt.swp391.GlucoTrackAlert.repository.DoctorRepository;
import fpt.swp391.GlucoTrackAlert.model.Doctor;
import fpt.swp391.GlucoTrackAlert.service.register.UserService;
import fpt.swp391.GlucoTrackAlert.service.register.EmailService;
import fpt.swp391.GlucoTrackAlert.model.user.PasswordResetToken;
import fpt.swp391.GlucoTrackAlert.dto.login.ForgotPasswordRequest;
import fpt.swp391.GlucoTrackAlert.dto.login.ResetPasswordRequest;
import fpt.swp391.GlucoTrackAlert.util.jwt.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final DoctorRepository doctorRepository;

    @Value("${app.frontend-url:http://localhost:8081}")
    private String frontendUrl;

    public UserServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           EmailVerificationTokenRepository tokenRepository,
                           PasswordResetTokenRepository passwordResetTokenRepository,
                           EmailService emailService,
                           BCryptPasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil) {
            RoleRepository roleRepository,
            EmailVerificationTokenRepository tokenRepository,
            EmailService emailService,
            BCryptPasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            DoctorRepository doctorRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tokenRepository = tokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.doctorRepository = doctorRepository;
    }

    @Override
    @Transactional
    public User register(RegisterRequest request) throws Exception {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new Exception("Email đã được sử dụng");
        }

        Role role = roleRepository.findByName("PATIENT")
                .orElseThrow(() -> new Exception("Role not found"));

        User u = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(role)
                .status("pending_verification")
                .emailVerified(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        User saved = userRepository.save(u);

        // Tạo OTP 6 số
        String otp = String.format("%06d", new Random().nextInt(999999));
        EmailVerificationToken ev = EmailVerificationToken.builder()
                .user(saved)
                .verificationToken(otp)
                .expiredAt(LocalDateTime.now().plusMinutes(10))
                .status("pending")
                .createdAt(LocalDateTime.now())
                .build();
        tokenRepository.save(ev);

        // Gửi email chứa OTP
        String body = buildOtpEmail(saved.getFullName(), otp);
        emailService.sendHtmlMessage(saved.getEmail(), "Mã xác nhận tài khoản GlucoTrackAlert", body);

        return saved;
    }

    @Override
    @Transactional
    public User activateUser(String otp) throws Exception {
        EmailVerificationToken ev = tokenRepository.findByVerificationToken(otp)
                .orElseThrow(() -> new Exception("Mã OTP không hợp lệ"));
        if (ev.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new Exception("Mã OTP đã hết hạn");
        }
        if (!"pending".equals(ev.getStatus())) {
            throw new Exception("Mã OTP đã được sử dụng");
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
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new Exception("Email không tồn tại trong hệ thống"));

        if (!user.getEmailVerified()) {
            throw new Exception("Tài khoản chưa xác thực email. Vui lòng kiểm tra hộp thư.");
        }

        if (!"active".equalsIgnoreCase(user.getStatus())) {
            throw new Exception("Tài khoản đã bị khóa hoặc chưa kích hoạt.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new Exception("Mật khẩu không chính xác");
        }

        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        String roleName = user.getRole() != null ? user.getRole().getName() : "UNKNOWN";
        String token = jwtUtil.generateToken(user.getEmail(), roleName);

        Long doctorId = null;
        if ("DOCTOR".equals(roleName)) {
            doctorId = doctorRepository.findAll().stream()
                    .filter(d -> d.getUser().getId().equals(user.getId()))
                    .findFirst()
                    .map(d -> d.getId().longValue())
                    .orElse(null);
        }

        return LoginResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role(roleName)
                .doctorId(doctorId)
                .message("Đăng nhập thành công")
                .build();
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) throws Exception {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new Exception("Email không tồn tại trong hệ thống"));

        if (!"active".equalsIgnoreCase(user.getStatus())) {
            throw new Exception("Tài khoản chưa được kích hoạt hoặc đã bị khóa");
        }

        // Tạo OTP 6 số
        String otp = String.format("%06d", new Random().nextInt(999999));
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .resetToken(otp)
                .expiredAt(LocalDateTime.now().plusMinutes(10))
                .status("pending")
                .createdAt(LocalDateTime.now())
                .build();
        passwordResetTokenRepository.save(resetToken);

        // Gửi email
        String body = buildPasswordResetOtpEmail(user.getFullName() != null ? user.getFullName() : "Bạn", otp);
        emailService.sendHtmlMessage(user.getEmail(), "Mã OTP Đặt Lại Mật Khẩu GlucoTrackAlert", body);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) throws Exception {
        PasswordResetToken token = passwordResetTokenRepository.findByResetToken(request.getOtp())
                .orElseThrow(() -> new Exception("Mã OTP không hợp lệ"));

        if (token.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new Exception("Mã OTP đã hết hạn");
        }

        if (!"pending".equals(token.getStatus())) {
            throw new Exception("Mã OTP đã được sử dụng");
        }

        User user = token.getUser();
        if (!user.getEmail().equalsIgnoreCase(request.getEmail())) {
            throw new Exception("Email không khớp với mã OTP");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        token.setStatus("used");
        token.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(token);
    }

    private String buildOtpEmail(String fullName, String otp) {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 30px; border: 1px solid #e0e0e0; border-radius: 8px;">
                <h2 style="color: #2c3e50; text-align: center;">GlucoTrackAlert</h2>
                <p style="color: #555;">Xin chào <strong>%s</strong>,</p>
                <p style="color: #555;">Mã xác nhận tài khoản của bạn là:</p>
                <div style="text-align: center; margin: 30px 0;">
                    <span style="background-color: #f4f7f6; color: #2c3e50; padding: 14px 28px;
                          border-radius: 5px; font-size: 32px; font-weight: bold;
                          letter-spacing: 8px; border: 2px dashed #2ecc71;">
                        %s
                    </span>
                </div>
                <p style="color: #999; font-size: 13px; text-align: center;">Mã này sẽ hết hạn sau <strong>10 phút</strong>.</p>
                <p style="color: #999; font-size: 13px; text-align: center;">Nếu bạn không đăng ký tài khoản này, hãy bỏ qua email này.</p>
            </div>
            """.formatted(fullName, otp);
    }

    private String buildPasswordResetOtpEmail(String fullName, String otp) {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 30px; border: 1px solid #e0e0e0; border-radius: 8px;">
                <h2 style="color: #2c3e50; text-align: center;">GlucoTrackAlert - Đặt lại mật khẩu</h2>
                <p style="color: #555;">Xin chào <strong>%s</strong>,</p>
                <p style="color: #555;">Mã OTP để đặt lại mật khẩu của bạn là:</p>
                <div style="text-align: center; margin: 30px 0;">
                    <span style="background-color: #f4f7f6; color: #2c3e50; padding: 14px 28px;
                          border-radius: 5px; font-size: 32px; font-weight: bold;
                          letter-spacing: 8px; border: 2px dashed #e74c3c;">
                        %s
                    </span>
                </div>
                <p style="color: #999; font-size: 13px; text-align: center;">Mã này sẽ hết hạn sau <strong>10 phút</strong>.</p>
                <p style="color: #999; font-size: 13px; text-align: center;">Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này và bảo mật tài khoản của bạn.</p>
            </div>
            """.formatted(fullName, otp);
    }
}
    @Override
    @Transactional
    public void changePassword(String email, String oldPassword, String newPassword) throws Exception {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new Exception("Không tìm thấy tài khoản"));

        oldPassword = oldPassword.trim();
        newPassword = newPassword.trim();

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new Exception("Mật khẩu hiện tại không đúng");
        }

        if (newPassword.length() < 6) {
            throw new Exception("Mật khẩu mới phải có ít nhất 6 ký tự");
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new Exception("Mật khẩu mới không được trùng mật khẩu hiện tại");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userRepository.save(user);
    }
}
