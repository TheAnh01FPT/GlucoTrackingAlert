package fpt.swp391.GlucoTrackAlert.service.impl.user_impl;

import fpt.swp391.GlucoTrackAlert.dto.user.UserAdminRequest;
import fpt.swp391.GlucoTrackAlert.model.Doctor;
import fpt.swp391.GlucoTrackAlert.model.role.Role;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.DoctorRepository;
import fpt.swp391.GlucoTrackAlert.repository.role.RoleRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.user.UserAdminService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserAdminServiceImpl implements UserAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final DoctorRepository doctorRepository;

    public UserAdminServiceImpl(UserRepository userRepository,
                                RoleRepository roleRepository,
                                BCryptPasswordEncoder passwordEncoder,
                                DoctorRepository doctorRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.doctorRepository = doctorRepository;
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public Page<User> getUsersPaged(int page, int size) {
        List<User> filtered = userRepository.findAll().stream()
                .filter(u -> u.getRole() != null &&
                        (u.getRole().getName().equalsIgnoreCase("PATIENT") ||
                                u.getRole().getName().equalsIgnoreCase("DOCTOR")))
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

        String inputRole = request.getRoleName().toUpperCase().trim();
        if (!inputRole.equals("PATIENT") && !inputRole.equals("DOCTOR")) {
            throw new Exception("Hệ thống quản trị chỉ cho phép tạo tài khoản với vai trò PATIENT hoặc DOCTOR.");
        }

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

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .status(request.getStatus())
                .emailVerified(request.getEmailVerified() != null ? request.getEmailVerified() : true)
                .role(role)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(user);

        if (inputRole.equals("DOCTOR")) {
            if (!doctorRepository.existsByUserEmail(savedUser.getEmail())) {
                Doctor doctor = new Doctor();
                doctor.setUser(savedUser);
                doctor.setFullName(savedUser.getFullName() != null ? savedUser.getFullName() : savedUser.getEmail());
                doctor.setStatus("active");
                doctorRepository.save(doctor);
            }
        }

        return savedUser;
    }

    @Override
    @Transactional
    public User updateUserByAdmin(Long id, UserAdminRequest request) throws Exception {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new Exception("Không tìm thấy dữ liệu người dùng cần cập nhật."));

        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new Exception("Email không được để trống");
        }
        String email = request.getEmail().trim();
        if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw new Exception("Định dạng Email không hợp lệ");
        }

        if (!user.getEmail().equalsIgnoreCase(email) &&
                userRepository.existsByEmail(email)) {
            throw new Exception("Email mới '" + email + "' đã được sử dụng bởi một tài khoản khác.");
        }

        String inputRole = request.getRoleName().toUpperCase().trim();
        if (!inputRole.equals("PATIENT") && !inputRole.equals("DOCTOR")) {
            throw new Exception("Hệ thống quản trị chỉ cho phép cập nhật vai trò sang PATIENT hoặc DOCTOR.");
        }

        Role role = roleRepository.findByName(inputRole)
                .orElseThrow(() -> new Exception("Không tồn tại quyền vai trò hệ thống: " + inputRole));

        String oldRole = user.getRole() != null ? user.getRole().getName() : "";

        user.setEmail(email);
        user.setStatus(request.getStatus());
        user.setEmailVerified(request.getEmailVerified() != null ? request.getEmailVerified() : false);
        user.setRole(role);
        user.setUpdatedAt(LocalDateTime.now());

        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            String password = request.getPassword().trim();
            if (password.length() < 6 || password.length() > 32) {
                throw new Exception("Mật khẩu phải từ 6 đến 32 ký tự");
            }
            if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$")) {
                throw new Exception("Mật khẩu phải chứa ít nhất 1 chữ hoa, 1 chữ thường và 1 chữ số");
            }
            user.setPasswordHash(passwordEncoder.encode(password));
        }

        User savedUser = userRepository.save(user);

        // Nếu đổi sang DOCTOR và chưa có record trong bảng doctors thì tự tạo
        if (inputRole.equals("DOCTOR") && !oldRole.equalsIgnoreCase("DOCTOR")) {
            if (!doctorRepository.existsByUserEmail(savedUser.getEmail())) {
                Doctor doctor = new Doctor();
                doctor.setUser(savedUser);
                doctor.setFullName(savedUser.getFullName() != null ? savedUser.getFullName() : savedUser.getEmail());
                doctor.setStatus("active");
                doctorRepository.save(doctor);
            }
        }

        return savedUser;
    }
}