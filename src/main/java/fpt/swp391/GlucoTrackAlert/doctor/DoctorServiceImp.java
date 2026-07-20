package fpt.swp391.GlucoTrackAlert.doctor;

import fpt.swp391.GlucoTrackAlert.doctor.Doctor;
import fpt.swp391.GlucoTrackAlert.model.DoctorPatientAssignment;
import fpt.swp391.GlucoTrackAlert.model.role.Role;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.DoctorPatientAssignmentRepository;
import fpt.swp391.GlucoTrackAlert.repository.role.RoleRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.doctor.DoctorService;
import fpt.swp391.GlucoTrackAlert.service.register.EmailService;
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
        doctor.setStatus("pending_verification"); // chờ bác sĩ upload CCCD + chứng chỉ + avatar
        applyAdminFields(doctor, request);
        doctorRepository.save(doctor);

        // 4. Gửi email thông báo tài khoản cho bác sĩ
        sendAccountEmail(user.getEmail(), plainPassword, doctor.getFullName());

        return DoctorResponse.from(doctor);
    }

    // ── Admin sửa hồ sơ (bao gồm phone) ──────────────────────────────────────
    @Override
    public DoctorResponse updateDoctor(Long id, DoctorRequest request) {
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
    public DoctorResponse getDoctorById(Long id) {
        return DoctorResponse.from(findOrThrow(id));
    }

    // ── Soft-delete (ngừng hoạt động) ────────────────────────────────────────
    @Override
    @Transactional
    public void deactivateDoctor(Long id) {
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

    // ── Bác sĩ upload ảnh CCCD + chứng chỉ + avatar + nhập số ───────────────
    @Override
    @Transactional
    public DoctorResponse uploadVerificationImages(Long doctorId,
            String nationalIdImageUrl,
            String practiceLicenseImageUrl,
            String avatarUrl,
            String nationalId,
            String practiceLicense) {

        Doctor doctor = findOrThrow(doctorId);

        // Kiểm tra số CCCD trùng (nếu có nhập)
        if (nationalId != null && !nationalId.isBlank()) {
            doctorRepository.findByNationalId(nationalId).ifPresent(existing -> {
                if (!existing.getId().equals(doctorId)) {
                    throw new RuntimeException("Số CCCD \"" + nationalId + "\" đã được đăng ký bởi bác sĩ khác");
                }
            });
            doctor.setNationalId(nationalId);
        }

        // Kiểm tra số chứng chỉ trùng (nếu có nhập)
        if (practiceLicense != null && !practiceLicense.isBlank()) {
            doctorRepository.findByPracticeLicense(practiceLicense).ifPresent(existing -> {
                if (!existing.getId().equals(doctorId)) {
                    throw new RuntimeException("Số chứng chỉ \"" + practiceLicense + "\" đã được đăng ký bởi bác sĩ khác");
                }
            });
            doctor.setPracticeLicense(practiceLicense);
        }

        if (nationalIdImageUrl != null) {
            doctor.setNationalIdImageUrl(nationalIdImageUrl);
        }
        if (practiceLicenseImageUrl != null) {
            doctor.setPracticeLicenseImageUrl(practiceLicenseImageUrl);
        }
        if (avatarUrl != null) {
            doctor.setAvatarUrl(avatarUrl);
        }

        doctor.setStatus("pending_approval");
        return DoctorResponse.from(doctorRepository.save(doctor));
    }

    // ── Admin lấy danh sách chờ duyệt ────────────────────────────────────────
    @Override
    public List<DoctorResponse> getPendingDoctors() {
        return doctorRepository.findByStatus("pending_approval")
                .stream().map(DoctorResponse::from).toList();
    }

    // ── Admin duyệt ──────────────────────────────────────────────────────────
    @Override
    @Transactional
    public DoctorResponse approveDoctor(Long id) {
        Doctor doctor = findOrThrow(id);
        doctor.setStatus("active");
        doctor.getUser().setStatus("active");
        userRepository.save(doctor.getUser());
        emailService.sendSimpleMessage(
                doctor.getUser().getEmail(),
                "[GlucoTrackAlert] Hồ sơ của bạn đã được duyệt",
                "Xin chào Bác sĩ " + doctor.getFullName() + ",\n\n"
                + "Hồ sơ của bạn đã được Admin xác minh và chấp thuận.\n"
                + "Bạn có thể đăng nhập và sử dụng hệ thống ngay bây giờ.\n\n"
                + "Trân trọng,\nGlucoTrackAlert");
        return DoctorResponse.from(doctorRepository.save(doctor));
    }

    // ── Admin từ chối ─────────────────────────────────────────────────────────
    @Override
    @Transactional
    public DoctorResponse rejectDoctor(Long id, String reason) {
        Doctor doctor = findOrThrow(id);
        doctor.setStatus("rejected");
        String reasonText = (reason != null && !reason.isBlank()) ? reason : "Không đủ điều kiện";
        emailService.sendSimpleMessage(
                doctor.getUser().getEmail(),
                "[GlucoTrackAlert] Hồ sơ của bạn chưa được duyệt",
                "Xin chào Bác sĩ " + doctor.getFullName() + ",\n\n"
                + "Rất tiếc, hồ sơ của bạn chưa được chấp thuận.\n"
                + "Lý do: " + reasonText + "\n\n"
                + "Vui lòng liên hệ Admin để biết thêm thông tin.\n\n"
                + "Trân trọng,\nGlucoTrackAlert");
        return DoctorResponse.from(doctorRepository.save(doctor));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private Doctor findOrThrow(Long id) {
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
        // KHÔNG set nationalId / practiceLicense ở đây: 2 field này bác sĩ tự
        // nhập + upload ảnh sau khi đăng nhập lần đầu (xem uploadVerificationImages).
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
        if (req.getNationalId() != null) {
            doctor.setNationalId(req.getNationalId());
        }
        if (req.getPracticeLicense() != null) {
            doctor.setPracticeLicense(req.getPracticeLicense());
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
                + "Vui lòng đăng nhập và hoàn tất hồ sơ (upload CCCD, chứng chỉ hành nghề, ảnh đại diện)\n"
                + "trước khi có thể sử dụng hệ thống.\n\n"
                + "Lưu ý: Không chia sẻ mật khẩu với bất kỳ ai.\n\n"
                + "Trân trọng,\nGlucoTrackAlert";
        emailService.sendSimpleMessage(email, subject, body);
    }
}