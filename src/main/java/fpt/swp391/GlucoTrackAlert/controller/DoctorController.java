package fpt.swp391.GlucoTrackAlert.controller;

import fpt.swp391.GlucoTrackAlert.dto.AdminCreateDoctorRequest;
import fpt.swp391.GlucoTrackAlert.dto.DoctorRequest;
import fpt.swp391.GlucoTrackAlert.dto.DoctorResponse;
import fpt.swp391.GlucoTrackAlert.enums.WorkShift;
import fpt.swp391.GlucoTrackAlert.model.Doctor;
import fpt.swp391.GlucoTrackAlert.repository.DoctorRepository;
import fpt.swp391.GlucoTrackAlert.service.DoctorService;
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

@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;
    private final DoctorRepository doctorRepository;

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
     * [ADMIN] Tạo tài khoản bác sĩ mới
     */
    @PostMapping("/admin-create")
    public ResponseEntity<?> adminCreateDoctor(@RequestBody AdminCreateDoctorRequest request) {
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
    public ResponseEntity<DoctorResponse> getDoctorById(@PathVariable Integer id) {
        return ResponseEntity.ok(doctorService.getDoctorById(id));
    }

    /**
     * [ADMIN] Cập nhật thông tin bác sĩ
     */
    @PutMapping("/{id}")
    public ResponseEntity<DoctorResponse> updateDoctor(
            @PathVariable Integer id,
            @RequestBody DoctorRequest request) {
        return ResponseEntity.ok(doctorService.updateDoctor(id, request));
    }

    /**
     * [DOCTOR] Upload ảnh CCCD, chứng chỉ hành nghề, avatar + nhập số CCCD & số
     * chứng chỉ. Bác sĩ phải hoàn tất bước này trước khi được phân công và khám
     * bệnh. Sau khi submit, status chuyển sang pending_approval để admin duyệt.
     */
    @PostMapping(value = "/{id}/upload-verification", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadVerification(
            @PathVariable Integer id,
            @RequestParam(value = "nationalIdImage", required = false) MultipartFile nationalIdImage,
            @RequestParam(value = "practiceLicenseImage", required = false) MultipartFile practiceLicenseImage,
            @RequestParam(value = "avatar", required = false) MultipartFile avatar,
            @RequestParam(value = "nationalId", required = false) String nationalId,
            @RequestParam(value = "practiceLicense", required = false) String practiceLicense) {
        try {
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
                String filename = "cccd_" + UUID.randomUUID() + "_" + nationalIdImage.getOriginalFilename();
                Path path = Paths.get(uploadDir + filename);
                Files.write(path, nationalIdImage.getBytes());
                nationalIdImageUrl = "/" + uploadDir + filename;
            }

            if (practiceLicenseImage != null && !practiceLicenseImage.isEmpty()) {
                String filename = "chungchi_" + UUID.randomUUID() + "_" + practiceLicenseImage.getOriginalFilename();
                Path path = Paths.get(uploadDir + filename);
                Files.write(path, practiceLicenseImage.getBytes());
                practiceLicenseImageUrl = "/" + uploadDir + filename;
            }

            if (avatar != null && !avatar.isEmpty()) {
                String filename = "avatar_" + UUID.randomUUID() + "_" + avatar.getOriginalFilename();
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
    public ResponseEntity<?> approveDoctor(@PathVariable Integer id) {
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
            @PathVariable Integer id,
            @RequestParam(value = "reason", required = false) String reason) {
        try {
            return ResponseEntity.ok(doctorService.rejectDoctor(id, reason));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deactivateDoctor(@PathVariable Integer id) {
        try {
            doctorService.deactivateDoctor(id);
            return ResponseEntity.ok("Bác sĩ đã được ngừng hoạt động và toàn bộ phân công active đã được hủy.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
