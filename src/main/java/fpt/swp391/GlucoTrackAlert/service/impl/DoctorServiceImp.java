package fpt.swp391.GlucoTrackAlert.service.impl;

import fpt.swp391.GlucoTrackAlert.dto.AdminCreateDoctorRequest;
import fpt.swp391.GlucoTrackAlert.dto.DoctorRequest;
import fpt.swp391.GlucoTrackAlert.dto.DoctorResponse;
import fpt.swp391.GlucoTrackAlert.model.Doctor;
import fpt.swp391.GlucoTrackAlert.model.DoctorPatientAssignment;
import fpt.swp391.GlucoTrackAlert.model.Role;
import fpt.swp391.GlucoTrackAlert.model.User;
import fpt.swp391.GlucoTrackAlert.repository.DoctorPatientAssignmentRepository;
import fpt.swp391.GlucoTrackAlert.repository.DoctorRepository;
import fpt.swp391.GlucoTrackAlert.repository.RoleRepository;
import fpt.swp391.GlucoTrackAlert.repository.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.DoctorService;
import fpt.swp391.GlucoTrackAlert.service.EmailService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DoctorServiceImp implements DoctorService {

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DoctorPatientAssignmentRepository assignmentRepository;
    private final EmailService emailService;
    private final BCryptPasswordEncoder passwordEncoder;

    // ── Admin tạo tài khoản + gửi email ────────────────────────────────────────
    @Override
    @Transactional
    public DoctorResponse adminCreateDoctor(AdminCreateDoctorRequest request) throws Exception {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new Exception("Email bác sĩ không được để trống");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new Exception("Email \"" + request.getEmail() + "\" đã tồn tại trong hệ thống");
        }

        // 1. Tạo mật khẩu tạm thời (admin đặt hoặc auto-generate)
        String plainPassword = (request.getTemporaryPassword() != null
                && !request.getTemporaryPassword().isBlank())
                ? request.getTemporaryPassword()
                : generatePassword();

        // 2. Tạo User với role DOCTOR
        Role doctorRole = roleRepository.findByName("DOCTOR")
                .orElseThrow(() -> new Exception("Role DOCTOR chưa được khởi tạo trong DB"));

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(plainPassword))
                .role(doctorRole)
                .status("active") // admin tạo hộ → active ngay, không cần verify email
                .emailVerified(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(user);

        // 3. Tạo Doctor profile
        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setStatus("active");
        applyAdminFields(doctor, request);
        doctorRepository.save(doctor);

        // 4. Gửi email thông báo tài khoản cho bác sĩ
        sendAccountEmail(user.getEmail(), plainPassword, doctor.getFullName());

        return DoctorResponse.from(doctor);
    }

    // ── Admin sửa hồ sơ (bao gồm phone) ──────────────────────────────────────
    @Override
    public DoctorResponse updateDoctor(Integer id, DoctorRequest request) {
        Doctor doctor = findOrThrow(id);
        applyAdminFieldsFromRequest(doctor, request);
        return DoctorResponse.from(doctorRepository.save(doctor));
    }

    // ── Đọc ──────────────────────────────────────────────────────────────────
    @Override
    public List<DoctorResponse> getAllDoctors() {
        return doctorRepository.findAll().stream().map(DoctorResponse::from).toList();
    }

    @Override
    public DoctorResponse getDoctorById(Integer id) {
        return DoctorResponse.from(findOrThrow(id));
    }

    // ── Soft-delete (ngừng hoạt động) ────────────────────────────────────────
    @Override
    @Transactional
    public void deactivateDoctor(Integer id) {
        Doctor doctor = findOrThrow(id);
        doctor.setStatus("inactive");
        doctorRepository.save(doctor);

        List<DoctorPatientAssignment> activeAssignments
                = assignmentRepository.findByDoctorIdAndStatus(id, "active");
        if (!activeAssignments.isEmpty()) {
            activeAssignments.forEach(a -> {
                a.setStatus("inactive");
                a.setNote("Bác sĩ ngừng hoạt động - tự động hủy phân công");
            });
            assignmentRepository.saveAll(activeAssignments);
        }
    }

    // ── Hard-delete (xóa vĩnh viễn) ──────────────────────────────────────────
    @Override
    @Transactional
    public void hardDeleteDoctor(Integer id) {
        Doctor doctor = findOrThrow(id);
        if (!"inactive".equals(doctor.getStatus())) {
            throw new RuntimeException(
                    "Chỉ có thể xóa vĩnh viễn bác sĩ đã ngừng hoạt động. "
                    + "Vui lòng ngừng hoạt động bác sĩ trước.");
        }

        // Xóa toàn bộ assignment liên quan (tất cả status)
        List<DoctorPatientAssignment> allAssignments
                = assignmentRepository.findByDoctorId(id);
        assignmentRepository.deleteAll(allAssignments);

        // Lưu reference tới User trước khi xóa Doctor
        User linkedUser = doctor.getUser();

        // Xóa Doctor trước (FK tới User)
        doctorRepository.delete(doctor);

        // Xóa User liên kết (tài khoản đăng nhập)
        if (linkedUser != null) {
            userRepository.delete(linkedUser);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private Doctor findOrThrow(Integer id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bác sĩ id=" + id));
    }

    /**
     * Áp dụng các field từ AdminCreateDoctorRequest vào Doctor entity
     */
    private void applyAdminFields(Doctor doctor, AdminCreateDoctorRequest req) {
        if (req.getFullName() != null) {
            doctor.setFullName(req.getFullName());
        }
        if (req.getSpecialization() != null) {
            doctor.setSpecialization(req.getSpecialization());
        }
        if (req.getDegree() != null) {
            doctor.setDegree(req.getDegree());
        }
        if (req.getExperienceYears() != null) {
            doctor.setExperienceYears(req.getExperienceYears());
        }
        if (req.getWorkplace() != null) {
            doctor.setWorkplace(req.getWorkplace());
        }
        if (req.getPhone() != null) {
            doctor.setPhone(req.getPhone());
        }
        if (req.getIntroduction() != null) {
            doctor.setIntroduction(req.getIntroduction());
        }
        if (req.getAvatarUrl() != null) {
            doctor.setAvatarUrl(req.getAvatarUrl());
        }
    }

    /**
     * Áp dụng các field từ DoctorRequest (admin update) vào Doctor entity
     */
    private void applyAdminFieldsFromRequest(Doctor doctor, DoctorRequest req) {
        if (req.getFullName() != null) {
            doctor.setFullName(req.getFullName());
        }
        if (req.getSpecialization() != null) {
            doctor.setSpecialization(req.getSpecialization());
        }
        if (req.getDegree() != null) {
            doctor.setDegree(req.getDegree());
        }
        if (req.getExperienceYears() != null) {
            doctor.setExperienceYears(req.getExperienceYears());
        }
        if (req.getWorkplace() != null) {
            doctor.setWorkplace(req.getWorkplace());
        }
        if (req.getPhone() != null) {
            doctor.setPhone(req.getPhone());
        }
        if (req.getIntroduction() != null) {
            doctor.setIntroduction(req.getIntroduction());
        }
        if (req.getAvatarUrl() != null) {
            doctor.setAvatarUrl(req.getAvatarUrl());
        }
        if (req.getStatus() != null) {
            doctor.setStatus(req.getStatus());
        }
    }

    /**
     * Sinh mật khẩu ngẫu nhiên 10 ký tự gồm chữ hoa, chữ thường, số
     */
    private String generatePassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(10);
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Gửi email thông báo tài khoản cho bác sĩ
     */
    private void sendAccountEmail(String email, String plainPassword, String doctorName) {
        String subject = "[GlucoTrackAlert] Tài khoản của bạn đã được tạo";
        String greeting = (doctorName != null && !doctorName.isBlank())
                ? "Bác sĩ " + doctorName
                : "Bác sĩ";
        String body = "Xin chào " + greeting + ",\n\n"
                + "Admin đã tạo tài khoản GlucoTrackAlert cho bạn với thông tin sau:\n\n"
                + "  Email đăng nhập : " + email + "\n"
                + "  Mật khẩu tạm thời: " + plainPassword + "\n\n"
                + "Vui lòng đăng nhập và đổi mật khẩu ngay sau lần đăng nhập đầu tiên.\n\n"
                + "Lưu ý: Thông tin tài khoản có tính bảo mật cao. "
                + "Không chia sẻ mật khẩu với bất kỳ ai.\n\n"
                + "Trân trọng,\nGlucoTrackAlert";
        emailService.sendSimpleMessage(email, subject, body);
    }
}