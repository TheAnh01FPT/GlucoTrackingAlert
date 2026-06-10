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
import java.util.Random;

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

    // Tạo mã OTP 6 chữ số
    private String generateOtp() {
        return String.format("%06d", new Random().nextInt(999999));
    }

    // Gửi email chứa OTP
    private void sendOtpEmail(String toEmail, String fullName, String otp) throws Exception {
        String subject = "Mã xác nhận đăng ký tài khoản GlucoTrack";
        String htmlContent = "<div style='font-family: Arial, sans-serif; max-width: 480px; margin: auto;'>"
                + "<h2 style='color: #e74c3c;'>🩸 GlucoTrack</h2>"
                + "<p>Xin chào <b>" + fullName + "</b>,</p>"
                + "<p>Mã xác nhận của bạn là:</p>"
                + "<div style='font-size: 36px; font-weight: bold; letter-spacing: 12px; "
                + "text-align: center; padding: 20px; background: #f4f7f6; border-radius: 8px; "
                + "color: #2c3e50;'>" + otp + "</div>"
                + "<p style='margin-top: 16px;'>Mã có hiệu lực trong <b>10 phút</b>. "
                + "Không chia sẻ mã này với bất kỳ ai.</p>"
                + "</div>";
        emailService.sendHtmlMessage(toEmail, subject, htmlContent);
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

        // Tạo OTP 6 số
        String otp = generateOtp();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .verificationToken(otp)
                .expiredAt(LocalDateTime.now().plusMinutes(10))
                .status("pending")
                .createdAt(LocalDateTime.now())
                .build();
        tokenRepository.save(verificationToken);

        sendOtpEmail(request.getEmail(), request.getFullName(), otp);

        return user;
    }

    @Override
    @Transactional
    public void resendOtp(String email) throws Exception {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new Exception("Không tìm thấy tài khoản với email này."));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new Exception("Tài khoản này đã được xác nhận rồi.");
        }

        // Hủy tất cả OTP cũ còn pending
        tokenRepository.expireAllPendingByUserId(user.getId());

        // Tạo OTP mới
        String otp = generateOtp();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .verificationToken(otp)
                .expiredAt(LocalDateTime.now().plusMinutes(10))
                .status("pending")
                .createdAt(LocalDateTime.now())
                .build();
        tokenRepository.save(verificationToken);

        sendOtpEmail(email, user.getFullName(), otp);
    }

    @Override
    @Transactional
    public User activateUser(String token) throws Exception {
        EmailVerificationToken verificationToken = tokenRepository.findByVerificationToken(token)
                .orElseThrow(() -> new Exception("Mã xác nhận không đúng."));

        if (verificationToken.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new Exception("Mã xác nhận đã hết hạn. Vui lòng yêu cầu gửi lại.");
        }

        if ("verified".equals(verificationToken.getStatus())) {
            throw new Exception("Mã xác nhận đã được sử dụng rồi.");
        }

        if ("expired".equals(verificationToken.getStatus())) {
            throw new Exception("Mã xác nhận đã hết hạn. Vui lòng yêu cầu gửi lại.");
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