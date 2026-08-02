package fpt.swp391.GlucoTrackAlert.service.impl.user_impl;

import fpt.swp391.GlucoTrackAlert.dto.user.UserAdminRequest;
import fpt.swp391.GlucoTrackAlert.model.role.Role;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.role.RoleRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.register.EmailService;
import fpt.swp391.GlucoTrackAlert.service.user.UserAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserAdminServiceImpl implements UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminServiceImpl.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public UserAdminServiceImpl(UserRepository userRepository,
                                RoleRepository roleRepository,
                                BCryptPasswordEncoder passwordEncoder,
                                EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public Page<User> getUsersPaged(int page, int size) {
        List<User> filtered = userRepository.findAll().stream()
                .filter(u -> u.getRole() != null &&
                        u.getRole().getName().equalsIgnoreCase("PATIENT"))
                .collect(Collectors.toList());

        int start = page * size;
        int end = Math.min(start + size, filtered.size());
        List<User> pageContent = (start >= filtered.size()) ? List.of() : filtered.subList(start, end);

        return new PageImpl<>(pageContent, PageRequest.of(page, size), filtered.size());
    }

    @Override
    public Page<User> searchAndFilterUsersPaged(String email, String roleName, String status, int page, int size) {
        List<User> filtered = userRepository.searchAndFilterUsers(email, roleName, status).stream()
                .filter(u -> u.getRole() != null &&
                        u.getRole().getName().equalsIgnoreCase("PATIENT"))
                .collect(Collectors.toList());

        int start = page * size;
        int end = Math.min(start + size, filtered.size());
        List<User> pageContent = (start >= filtered.size()) ? List.of() : filtered.subList(start, end);

        return new PageImpl<>(pageContent, PageRequest.of(page, size), filtered.size());
    }

    @Override
    public long getDoctorCount() {
        return userRepository.countByRole_Name("DOCTOR");
    }

    @Override
    public long getPatientCount() {
        return userRepository.countByRole_Name("PATIENT");
    }

    @Override
    public long getActivePatientCount() {
        return userRepository.countByRole_NameAndStatus("PATIENT", "active");
    }

    @Override
    public long getPendingPatientCount() {
        return userRepository.countByRole_NameAndStatus("PATIENT", "pending_verification");
    }

    @Override
    public long getBannedPatientCount() {
        return userRepository.countByRole_NameAndStatus("PATIENT", "banned");
    }

    @Override
    public List<User> getUsersFilteredByRole(Long roleId) {
        return userRepository.findByRoleId(roleId);
    }


    @Override
    public User getUserById(Long id) throws Exception {
        return userRepository.findById(id)
                .orElseThrow(() -> new Exception("Không tìm thấy người dùng có mã số ID: " + id));
    }

    @Override
    @Transactional
    public User createUserByAdmin(UserAdminRequest request) throws Exception {
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new Exception("Email không được để trống");
        }
        String email = request.getEmail().trim();
        if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw new Exception("Định dạng Email không hợp lệ");
        }
        if (userRepository.existsByEmail(email)) {
            throw new Exception("Tài khoản email '" + email + "' đã tồn tại trên hệ thống.");
        }

        String phone = (request.getPhone() != null && !request.getPhone().trim().isEmpty())
                ? request.getPhone().trim() : null;
        if (phone != null) {
            if (!phone.matches("^[0-9]{10,11}$")) {
                throw new Exception("Số điện thoại không hợp lệ (phải từ 10 đến 11 chữ số)");
            }
            if (userRepository.existsByPhone(phone)) {
                throw new Exception("Số điện thoại '" + phone + "' đã được sử dụng trên hệ thống.");
            }
        }

        String inputRole = "PATIENT";

        Role role = roleRepository.findByName(inputRole)
                .orElseThrow(() -> new Exception("Không tìm thấy cấu hình vai trò: " + inputRole));

        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new Exception("Mật khẩu không được để trống");
        }
        String password = request.getPassword().trim();
        if (password.length() < 6 || password.length() > 32) {
            throw new Exception("Mật khẩu phải từ 6 đến 32 ký tự");
        }
        if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$")) {
            throw new Exception("Mật khẩu phải chứa ít nhất 1 chữ hoa, 1 chữ thường và 1 chữ số");
        }

        String normalizedStatus = (request.getStatus() == null || request.getStatus().trim().isEmpty())
                ? "active"
                : request.getStatus().trim().toLowerCase();

        User user = User.builder()
                .email(email)
                .fullName(request.getFullName() != null ? request.getFullName().trim() : null)
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .passwordHash(passwordEncoder.encode(password))
                .status(normalizedStatus)
                .emailVerified(request.getEmailVerified() != null ? request.getEmailVerified() : "active".equalsIgnoreCase(normalizedStatus))
                .role(role)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User updateUserByAdmin(Long id, UserAdminRequest request) throws Exception {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new Exception("Không tìm thấy dữ liệu người dùng cần cập nhật."));

        if (user.getRole() == null || !"PATIENT".equalsIgnoreCase(user.getRole().getName())) {
            throw new Exception("Chỉ được quản lý tài khoản bệnh nhân bằng tính năng trạng thái và reset mật khẩu.");
        }

        if (request.getStatus() == null || request.getStatus().trim().isEmpty()) {
            throw new Exception("Trạng thái tài khoản không được bỏ trống");
        }

        String normalizedStatus = request.getStatus().trim().toLowerCase();
        if (!"active".equals(normalizedStatus) && !"pending_verification".equals(normalizedStatus) && !"banned".equals(normalizedStatus)) {
            throw new Exception("Trạng thái không hợp lệ. Chỉ chấp nhận: active, pending_verification, banned");
        }

        user.setStatus(normalizedStatus);
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User updateUserStatusByAdmin(Long id, String status) throws Exception {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new Exception("Không tìm thấy dữ liệu người dùng cần cập nhật."));

        if (user.getRole() == null || !"PATIENT".equalsIgnoreCase(user.getRole().getName())) {
            throw new Exception("Chỉ quản lý được trạng thái tài khoản bệnh nhân.");
        }

        if (status == null || status.trim().isEmpty()) {
            throw new Exception("Trạng thái tài khoản không được bỏ trống");
        }

        String normalizedStatus = status.trim().toLowerCase();
        if (!"active".equals(normalizedStatus) && !"pending_verification".equals(normalizedStatus) && !"banned".equals(normalizedStatus)) {
            throw new Exception("Trạng thái không hợp lệ. Chỉ chấp nhận: active, pending_verification, banned");
        }

        String oldStatus = user.getStatus();
        user.setStatus(normalizedStatus);
        if ("active".equals(normalizedStatus)) {
            user.setEmailVerified(true);
        } else if ("pending_verification".equals(normalizedStatus)) {
            user.setEmailVerified(false);
        }
        user.setUpdatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);

        log.info("[AUDIT STATUS CHANGE] Admin updated Patient ID {} (Email: {}) status from '{}' to '{}' at {}", 
                id, user.getEmail(), oldStatus, normalizedStatus, LocalDateTime.now());

        return savedUser;
    }

    @Override
    @Transactional
    public User resetPatientPasswordByAdmin(Long id) throws Exception {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new Exception("Không tìm thấy bệnh nhân cần cấp lại mật khẩu."));

        if (user.getRole() == null || !"PATIENT".equalsIgnoreCase(user.getRole().getName())) {
            throw new Exception("Chỉ hỗ trợ cấp lại mật khẩu cho tài khoản bệnh nhân.");
        }

        String newPassword = generatePassword();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("[AUDIT PASSWORD RESET] Admin triggered password reset for Patient ID {} (Email: {}) at {}", 
                id, user.getEmail(), LocalDateTime.now());

        String subject = "[GlucoTrackAlert] Mật khẩu mới đã được cấp lại";
        String body = "Xin chào bệnh nhân,\n\n"
                + "Admin đã cấp lại mật khẩu cho tài khoản GlucoTrackAlert của bạn.\n\n"
                + "Email đăng nhập: " + user.getEmail() + "\n"
                + "Mật khẩu mới: " + newPassword + "\n\n"
                + "Vui lòng đăng nhập và đổi mật khẩu ngay sau khi đăng nhập thành công.\n\n"
                + "Trân trọng,\n"
                + "GlucoTrackAlert";

        emailService.sendSimpleMessageAsync(user.getEmail(), subject, body);
        return user;
    }

    private String generatePassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
