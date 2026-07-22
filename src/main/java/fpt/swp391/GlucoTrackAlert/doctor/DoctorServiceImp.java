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
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new Exception("Email \"" + email + "\" đã tồn tại trong hệ thống");
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
                .email(email)
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
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            String phone = request.getPhone().trim();
            doctorRepository.findByPhone(phone).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new RuntimeException("Số điện thoại đã được đăng ký với tài khoản khác");
                }
            });
        }
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
    //
    // LUỒNG:
    // 1) Bác sĩ CHƯA từng active (mới tạo tài khoản / đang pending_verification / bị rejected):
    //    → áp thẳng vào field chính (nationalId, nationalIdImageUrl, ...) như cũ,
    //      vì chưa có gì "đang dùng" để mà mất, chuyển status = pending_approval.
    //
    // 2) Bác sĩ ĐANG active (đã từng được duyệt, đang hành nghề):
    //    → KHÔNG ghi đè field chính. Ảnh/số mới được lưu tạm (staging) vào cột
    //      pendingVerificationJson dưới dạng JSON. Bác sĩ vẫn giữ status = active,
    //      vẫn nhận bệnh nhân/khám bình thường trong lúc chờ duyệt. Admin sẽ thấy
    //      badge "có cập nhật đang chờ duyệt" và xem được cả ảnh cũ (đang dùng)
    //      lẫn ảnh mới (đang chờ) để so sánh trước khi quyết định.
    //    → approveDoctor() sẽ áp dữ liệu staging này vào field chính.
    //    → rejectDoctor() sẽ chỉ xoá staging, field chính giữ nguyên, không mất gì.
    //
    // Avatar không cần duyệt lại nên luôn áp thẳng ở cả 2 case.
    @Override
    @Transactional
    public DoctorResponse uploadVerificationImages(Long doctorId,
            String nationalIdImageUrl,
            String practiceLicenseImageUrl,
            String avatarUrl,
            String nationalId,
            String practiceLicense) {

        Doctor doctor = findOrThrow(doctorId);
        boolean hasNewImage = nationalIdImageUrl != null || practiceLicenseImageUrl != null;
        boolean isAlreadyActive = "active".equalsIgnoreCase(doctor.getStatus());

        // Kiểm tra trùng số CCCD / số chứng chỉ với bác sĩ KHÁC (áp dụng cho cả 2 case,
        // để chặn ngay từ lúc submit, không đợi tới lúc admin duyệt mới báo lỗi)
        if (nationalId != null && !nationalId.isBlank()) {
            doctorRepository.findByNationalId(nationalId).ifPresent(existing -> {
                if (!existing.getId().equals(doctorId)) {
                    throw new RuntimeException("Số CCCD \"" + nationalId + "\" đã được đăng ký bởi bác sĩ khác");
                }
            });
        }
        if (practiceLicense != null && !practiceLicense.isBlank()) {
            doctorRepository.findByPracticeLicense(practiceLicense).ifPresent(existing -> {
                if (!existing.getId().equals(doctorId)) {
                    throw new RuntimeException("Số chứng chỉ \"" + practiceLicense + "\" đã được đăng ký bởi bác sĩ khác");
                }
            });
        }

        if (avatarUrl != null) {
            doctor.setAvatarUrl(avatarUrl);
        }

        if (isAlreadyActive && hasNewImage) {
            // ── Case 2: staging, không đụng field chính ──
            String json = buildPendingVerificationJson(nationalId, nationalIdImageUrl,
                    practiceLicense, practiceLicenseImageUrl);
            doctor.setPendingVerificationJson(json);
            // status giữ nguyên "active" — bác sĩ vẫn hành nghề bình thường
        } else if (hasNewImage) {
            // ── Case 1: chưa từng active, áp thẳng như cũ ──
            if (nationalId != null && !nationalId.isBlank()) {
                doctor.setNationalId(nationalId);
            }
            if (practiceLicense != null && !practiceLicense.isBlank()) {
                doctor.setPracticeLicense(practiceLicense);
            }
            if (nationalIdImageUrl != null) {
                doctor.setNationalIdImageUrl(nationalIdImageUrl);
            }
            if (practiceLicenseImageUrl != null) {
                doctor.setPracticeLicenseImageUrl(practiceLicenseImageUrl);
            }
            doctor.setStatus("pending_approval");
        }
        // Nếu chỉ upload avatar (không có ảnh CCCD/chứng chỉ mới) thì không đụng gì
        // tới status/staging cả — đúng như comment gốc "không bắt duyệt lại".

        return DoctorResponse.from(doctorRepository.save(doctor));
    }

    /**
     * Build JSON string lưu tạm dữ liệu xác minh mới của bác sĩ ĐANG active.
     * Chỉ đưa vào những field thực sự có giá trị mới trong lần submit này.
     */
    private String buildPendingVerificationJson(String nationalId, String nationalIdImageUrl,
            String practiceLicense, String practiceLicenseImageUrl) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        if (nationalIdImageUrl != null) {
            sb.append("\"nationalId\":").append(jsonString(nationalId)).append(",");
            sb.append("\"nationalIdImageUrl\":").append(jsonString(nationalIdImageUrl));
            first = false;
        }
        if (practiceLicenseImageUrl != null) {
            if (!first) sb.append(",");
            sb.append("\"practiceLicense\":").append(jsonString(practiceLicense)).append(",");
            sb.append("\"practiceLicenseImageUrl\":").append(jsonString(practiceLicenseImageUrl));
            first = false;
        }
        if (!first) sb.append(",");
        sb.append("\"submittedAt\":").append(jsonString(LocalDateTime.now().toString()));
        sb.append("}");
        return sb.toString();
    }

    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ── Admin lấy danh sách chờ duyệt ────────────────────────────────────────
    // Gồm: bác sĩ mới/pending_approval (chưa từng active) + bác sĩ active có
    // bản xác minh mới đang staging trong pendingVerificationJson.
    @Override
    public List<DoctorResponse> getPendingDoctors() {
        return doctorRepository.findByStatusOrPendingVerificationJsonIsNotNull("pending_approval")
                .stream().map(DoctorResponse::from).toList();
    }

    // ── Admin duyệt ──────────────────────────────────────────────────────────
    // Nếu có bản staging (pendingVerificationJson) → áp dữ liệu đó vào field chính
    // rồi xoá staging. Nếu không có staging (case bác sĩ mới/pending_approval bình
    // thường) → chỉ cần chuyển status = active như cũ.
    @Override
    @Transactional
    public DoctorResponse approveDoctor(Long id) {
        Doctor doctor = findOrThrow(id);

        String pendingJson = doctor.getPendingVerificationJson();
        if (pendingJson != null && !pendingJson.isBlank()) {
            applyPendingVerificationJson(doctor, pendingJson);
            doctor.setPendingVerificationJson(null);
        }

        doctor.setStatus("active");
        doctor.getUser().setStatus("active");
        userRepository.save(doctor.getUser());
        emailService.sendSimpleMessageAsync(
                doctor.getUser().getEmail(),
                "[GlucoTrackAlert] Hồ sơ của bạn đã được duyệt",
                "Xin chào Bác sĩ " + doctor.getFullName() + ",\n\n"
                + "Hồ sơ của bạn đã được Admin xác minh và chấp thuận.\n"
                + "Bạn có thể đăng nhập và sử dụng hệ thống ngay bây giờ.\n\n"
                + "Trân trọng,\nGlucoTrackAlert");
        return DoctorResponse.from(doctorRepository.save(doctor));
    }

    // ── Admin từ chối ─────────────────────────────────────────────────────────
    // Nếu có staging → chỉ xoá staging, field chính (đang dùng) giữ nguyên,
    // bác sĩ vẫn active bình thường, không bị coi là "rejected".
    // Nếu không có staging (case bác sĩ mới/pending_approval bình thường) →
    // chuyển status = rejected như cũ.
    @Override
    @Transactional
    public DoctorResponse rejectDoctor(Long id, String reason) {
        Doctor doctor = findOrThrow(id);
        String reasonText = (reason != null && !reason.isBlank()) ? reason : "Không đủ điều kiện";

        String pendingJson = doctor.getPendingVerificationJson();
        boolean wasStaged = pendingJson != null && !pendingJson.isBlank();

        if (wasStaged) {
            doctor.setPendingVerificationJson(null);
            // KHÔNG đổi status — bác sĩ vẫn active với hồ sơ cũ, chỉ có bản cập nhật bị từ chối
        } else {
            doctor.setStatus("rejected");
        }

        emailService.sendSimpleMessageAsync(
                doctor.getUser().getEmail(),
                "[GlucoTrackAlert] Hồ sơ của bạn chưa được duyệt",
                "Xin chào Bác sĩ " + doctor.getFullName() + ",\n\n"
                + (wasStaged
                        ? "Rất tiếc, bản cập nhật CCCD/chứng chỉ hành nghề mới của bạn chưa được chấp thuận.\n"
                        + "Hồ sơ xác minh trước đó của bạn vẫn giữ nguyên hiệu lực.\n"
                        : "Rất tiếc, hồ sơ của bạn chưa được chấp thuận.\n")
                + "Lý do: " + reasonText + "\n\n"
                + "Vui lòng liên hệ Admin để biết thêm thông tin.\n\n"
                + "Trân trọng,\nGlucoTrackAlert");
        return DoctorResponse.from(doctorRepository.save(doctor));
    }

    /**
     * Parse JSON staging (dạng flat, tự build bằng buildPendingVerificationJson)
     * và áp các field có trong đó vào Doctor entity chính.
     */
    private void applyPendingVerificationJson(Doctor doctor, String json) {
        String nationalId = extractJsonField(json, "nationalId");
        String nationalIdImageUrl = extractJsonField(json, "nationalIdImageUrl");
        String practiceLicense = extractJsonField(json, "practiceLicense");
        String practiceLicenseImageUrl = extractJsonField(json, "practiceLicenseImageUrl");

        if (nationalIdImageUrl != null) {
            doctor.setNationalIdImageUrl(nationalIdImageUrl);
            if (nationalId != null) {
                doctor.setNationalId(nationalId);
            }
        }
        if (practiceLicenseImageUrl != null) {
            doctor.setPracticeLicenseImageUrl(practiceLicenseImageUrl);
            if (practiceLicense != null) {
                doctor.setPracticeLicense(practiceLicense);
            }
        }
    }

    /**
     * Đọc 1 field string từ JSON flat dạng {"key":"value",...} (không có object/array lồng nhau).
     * Trả về null nếu không có field hoặc value là null.
     */
    private String extractJsonField(String json, String key) {
        String needle = "\"" + key + "\":";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            return null;
        }
        int start = idx + needle.length();
        if (json.startsWith("null", start)) {
            return null;
        }
        if (start >= json.length() || json.charAt(start) != '"') {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int i = start + 1;
        while (i < json.length() && json.charAt(i) != '"') {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                i++;
                sb.append(json.charAt(i));
            } else {
                sb.append(c);
            }
            i++;
        }
        return sb.toString();
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
        emailService.sendSimpleMessageAsync(email, subject, body);
    }

}