package fpt.swp391.GlucoTrackAlert.service.impl.user_impl;

import fpt.swp391.GlucoTrackAlert.dto.user.UserAdminRequest;
import fpt.swp391.GlucoTrackAlert.model.Role;
import fpt.swp391.GlucoTrackAlert.model.User;
import fpt.swp391.GlucoTrackAlert.repository.role.RoleRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.user.UserAdminService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserAdminServiceImpl implements UserAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserAdminServiceImpl(UserRepository userRepository,
                                RoleRepository roleRepository,
                                BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
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
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new Exception("Tài khoản email '" + request.getEmail() + "' đã tồn tại trên hệ thống.");
        }

        // CHỈ CHO PHÉP PATIENT HOẶC DOCTOR
        String inputRole = request.getRoleName().toUpperCase().trim();
        if (!inputRole.equals("PATIENT") && !inputRole.equals("DOCTOR")) {
            throw new Exception("Hệ thống quản trị chỉ cho phép tạo tài khoản với vai trò PATIENT hoặc DOCTOR.");
        }

        Role role = roleRepository.findByName(inputRole)
                .orElseThrow(() -> new Exception("Không tìm thấy cấu hình vai trò: " + inputRole + " trong cơ sở dữ liệu."));

        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new Exception("Mật khẩu khởi tạo không được phép bỏ trống.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword().trim()))
                .status(request.getStatus())
                .emailVerified(request.getEmailVerified() != null ? request.getEmailVerified() : true)
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

        if (!user.getEmail().equalsIgnoreCase(request.getEmail()) &&
                userRepository.existsByEmail(request.getEmail())) {
            throw new Exception("Email mới '" + request.getEmail() + "' đã được sử dụng bởi một tài khoản khác.");
        }

        // CHỈ CHO PHÉP PATIENT HOẶC DOCTOR
        String inputRole = request.getRoleName().toUpperCase().trim();
        if (!inputRole.equals("PATIENT") && !inputRole.equals("DOCTOR")) {
            throw new Exception("Hệ thống quản trị chỉ cho phép cập nhật vai trò sang PATIENT hoặc DOCTOR.");
        }

        Role role = roleRepository.findByName(inputRole)
                .orElseThrow(() -> new Exception("Không tồn tại quyền vai trò hệ thống: " + inputRole));

        user.setEmail(request.getEmail());
        user.setStatus(request.getStatus());
        user.setEmailVerified(request.getEmailVerified() != null ? request.getEmailVerified() : false);
        user.setRole(role);
        user.setUpdatedAt(LocalDateTime.now());

        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword().trim()));
        }

        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void deleteUserByAdmin(Long id) throws Exception {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new Exception("Không tìm thấy người dùng có mã số ID để xóa."));
        try {
            userRepository.delete(user);
        } catch (Exception e) {
            throw new Exception("Không thể thực hiện xóa cứng tài khoản do người dùng đã phát sinh các dữ liệu liên kết y tế (Hồ sơ Patients, Doctors hoặc Nhật ký đo đường huyết).");
        }
    }
}