package fpt.swp391.GlucoTrackAlert.controller;

import fpt.swp391.GlucoTrackAlert.dto.AdminCreateDoctorRequest;
import fpt.swp391.GlucoTrackAlert.dto.DoctorRequest;
import fpt.swp391.GlucoTrackAlert.dto.DoctorResponse;
import fpt.swp391.GlucoTrackAlert.enums.WorkShift;
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
import org.springframework.http.ResponseEntity;
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

    private final DoctorService doctorService;

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
     * [ADMIN] Cập nhật thông tin bác sĩ. Admin có thể sửa mọi trường bao gồm:
     * fullName, phone, specialization, degree, workplace, introduction,
     * avatarUrl, status.
     */
    @PutMapping("/{id}")
    public ResponseEntity<DoctorResponse> updateDoctor(
            @PathVariable Integer id,
            @RequestBody DoctorRequest request) {
        return ResponseEntity.ok(doctorService.updateDoctor(id, request));
    }

    /**
     * [DOCTOR] Upload ảnh CCCD và chứng chỉ hành nghề.
     * Sau khi upload, status bác sĩ chuyển sang pending_approval để admin duyệt.
     */
    @PostMapping("/{id}/upload-verification")
    public ResponseEntity<?> uploadVerification(
            @PathVariable Integer id,
            @RequestParam(value = "nationalIdImage", required = false) MultipartFile nationalIdImage,
            @RequestParam(value = "practiceLicenseImage", required = false) MultipartFile practiceLicenseImage) {
        try {
            String uploadDir = "uploads/doctors/" + id + "/";
            Files.createDirectories(Paths.get(uploadDir));

            String nationalIdImageUrl = null;
            String practiceLicenseImageUrl = null;

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

            DoctorResponse response = doctorService.uploadVerificationImages(id, nationalIdImageUrl, practiceLicenseImageUrl);
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
     * [ADMIN] Duyệt bác sĩ → status = active, gửi email thông báo
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
     * [ADMIN] Từ chối bác sĩ → status = rejected, gửi email kèm lý do
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