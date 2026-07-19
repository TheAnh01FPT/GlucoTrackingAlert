package fpt.swp391.GlucoTrackAlert.doctor;

import fpt.swp391.GlucoTrackAlert.enums.WorkShift;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Tất cả endpoint trong controller này chỉ dành cho ADMIN. Bác sĩ KHÔNG có
 * quyền tự sửa bất kỳ thông tin nào của mình. Admin là người quản lý toàn bộ dữ
 * liệu bác sĩ.
 */
@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/jpeg", "image/jpg", "image/png"
    );

    private final DoctorService doctorService;
    private final DoctorRepository doctorRepository;

    /**
     * [PUBLIC] Giờ làm việc cố định của tất cả bác sĩ trong hệ thống. Hardcode
     * trong WorkShift – không có DB, không cần auth. Dùng để hiển thị trên
     * trang admin và làm điều kiện gửi thông báo.
     */

    @GetMapping("/working-hours")
    public ResponseEntity<Map<String, String>> getWorkingHours() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("display", WorkShift.DISPLAY);
        info.put("days", WorkShift.DAYS);
        info.put("fullLabel", WorkShift.FULL_LABEL);
        info.put("start", WorkShift.START.toString());
        info.put("end", WorkShift.END.toString());
        return ResponseEntity.ok(info);
    }

    /**
     * [ADMIN] Tạo tài khoản bác sĩ mới. - Tạo User (email + mật khẩu tạm) +
     * Doctor profile cùng lúc. - Hệ thống tự gửi email cho bác sĩ kèm username
     * & mật khẩu. - Bác sĩ KHÔNG tự đăng ký được qua form thông thường.
     */
    @PostMapping("/admin-create")
    public ResponseEntity<?> adminCreateDoctor(@Valid @RequestBody AdminCreateDoctorRequest request) {
        try {
            DoctorResponse response = doctorService.adminCreateDoctor(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * [ADMIN] Lấy danh sách tất cả bác sĩ
     */
    @GetMapping
    public ResponseEntity<List<DoctorResponse>> getAllDoctors() {
        return ResponseEntity.ok(doctorService.getAllDoctors());
    }

    /**
     * [ADMIN] Lấy thông tin 1 bác sĩ theo id
     */
    @GetMapping("/{id}")
    public ResponseEntity<DoctorResponse> getDoctorById(@PathVariable Long id) {
        return ResponseEntity.ok(doctorService.getDoctorById(id));
    }

    /**
     * [ADMIN] Cập nhật thông tin bác sĩ. Admin có thể sửa mọi trường bao gồm:
     * fullName, phone, specialization, degree, workplace, introduction,
     * avatarUrl, status.
     */
    @PutMapping("/{id}")
    public ResponseEntity<DoctorResponse> updateDoctor(
            @PathVariable Long id,
            @Valid @RequestBody DoctorRequest request) {

        return ResponseEntity.ok(doctorService.updateDoctor(id, request));
    }

    /**

     * [DOCTOR] Upload ảnh CCCD, chứng chỉ hành nghề, avatar + nhập số CCCD & số
     * chứng chỉ. Bác sĩ phải hoàn tất bước này trước khi được phân công và khám
     * bệnh. Sau khi submit, status chuyển sang pending_approval để admin duyệt.
     */
    @PostMapping(value = "/{id}/upload-verification", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadVerification(
            @PathVariable Long id,
            @RequestParam(value = "nationalIdImage", required = false) MultipartFile nationalIdImage,
            @RequestParam(value = "practiceLicenseImage", required = false) MultipartFile practiceLicenseImage,
            @RequestParam(value = "avatar", required = false) MultipartFile avatar,
            @RequestParam(value = "nationalId", required = false) String nationalId,
            @RequestParam(value = "practiceLicense", required = false) String practiceLicense) {
        try {
            // Validate CCCD format nếu có nhập
            if (nationalId != null && !nationalId.isBlank() && !nationalId.matches("^\\d{12}$")) {
                return ResponseEntity.badRequest().body("Số CCCD phải gồm đúng 12 chữ số");
            }

            // Validate chứng chỉ hành nghề không quá dài
            if (practiceLicense != null && practiceLicense.length() > 50) {
                return ResponseEntity.badRequest().body("Số chứng chỉ hành nghề không được vượt quá 50 ký tự");
            }

            // Validate các file upload
            if (nationalIdImage != null && !nationalIdImage.isEmpty()) {
                validateImageFile(nationalIdImage, "Ảnh CCCD");
            }
            if (practiceLicenseImage != null && !practiceLicenseImage.isEmpty()) {
                validateImageFile(practiceLicenseImage, "Ảnh chứng chỉ hành nghề");
            }
            if (avatar != null && !avatar.isEmpty()) {
                validateImageFile(avatar, "Ảnh đại diện");
            }

            // Chỉ cho phép bác sĩ tự upload ảnh của chính mình
            String email = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                    .stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            if (!isAdmin) {
                Doctor doctor = doctorRepository.findByUserEmail(email)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ bác sĩ của bạn"));
                if (!doctor.getId().equals(id)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body("Bạn không có quyền upload ảnh cho bác sĩ khác");
                }
            }

            String uploadDir = "uploads/doctors/" + id + "/";
            Files.createDirectories(Paths.get(uploadDir));

            String nationalIdImageUrl = null;
            String practiceLicenseImageUrl = null;
            String avatarUrl = null;

            if (nationalIdImage != null && !nationalIdImage.isEmpty()) {
                String filename = "cccd_" + UUID.randomUUID() + "_" + sanitizeFilename(nationalIdImage.getOriginalFilename());
                Path path = Paths.get(uploadDir + filename);
                Files.write(path, nationalIdImage.getBytes());
                nationalIdImageUrl = "/" + uploadDir + filename;
            }

            if (practiceLicenseImage != null && !practiceLicenseImage.isEmpty()) {
                String filename = "chungchi_" + UUID.randomUUID() + "_" + sanitizeFilename(practiceLicenseImage.getOriginalFilename());
                Path path = Paths.get(uploadDir + filename);
                Files.write(path, practiceLicenseImage.getBytes());
                practiceLicenseImageUrl = "/" + uploadDir + filename;
            }

            if (avatar != null && !avatar.isEmpty()) {
                String filename = "avatar_" + UUID.randomUUID() + "_" + sanitizeFilename(avatar.getOriginalFilename());
                Path path = Paths.get(uploadDir + filename);
                Files.write(path, avatar.getBytes());
                avatarUrl = "/" + uploadDir + filename;
            }

            DoctorResponse response = doctorService.uploadVerificationImages(
                    id,
                    nationalIdImageUrl,
                    practiceLicenseImageUrl,
                    avatarUrl,
                    nationalId,
                    practiceLicense
            );
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Lỗi khi upload file: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * [ADMIN] Lấy danh sách bác sĩ đang chờ duyệt
     */
    @GetMapping("/pending")
    public ResponseEntity<List<DoctorResponse>> getPendingDoctors() {
        return ResponseEntity.ok(doctorService.getPendingDoctors());
    }

    /**
     * [ADMIN] Duyệt bác sĩ → status = active
     */
    @PutMapping("/{id}/approve")
    public ResponseEntity<?> approveDoctor(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(doctorService.approveDoctor(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * [ADMIN] Từ chối bác sĩ → status = rejected
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectDoctor(
            @PathVariable Long id,
            @RequestParam(value = "reason", required = false) String reason) {
        try {
            return ResponseEntity.ok(doctorService.rejectDoctor(id, reason));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deactivateDoctor(@PathVariable Long id) {
        try {
            doctorService.deactivateDoctor(id);
            return ResponseEntity.ok("Bác sĩ đã được ngừng hoạt động và toàn bộ phân công active đã được hủy.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    /**
     * Validate file upload: chỉ chấp nhận ảnh JPG/PNG và tối đa 5MB
     */
    private void validateImageFile(MultipartFile file, String fieldName) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException(fieldName + " không được vượt quá 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new RuntimeException(fieldName + " chỉ chấp nhận định dạng JPG hoặc PNG");
        }
    }

    /**
     * Làm sạch tên file để tránh path traversal attack
     */
    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null) {
            return "file";
        }
        return originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}