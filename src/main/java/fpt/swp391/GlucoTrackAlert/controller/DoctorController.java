package fpt.swp391.GlucoTrackAlert.controller;

import fpt.swp391.GlucoTrackAlert.dto.AdminCreateDoctorRequest;
import fpt.swp391.GlucoTrackAlert.dto.DoctorRequest;
import fpt.swp391.GlucoTrackAlert.dto.DoctorResponse;
import fpt.swp391.GlucoTrackAlert.enums.WorkShift;
import fpt.swp391.GlucoTrackAlert.service.DoctorService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     * [ADMIN] Ngừng hoạt động bác sĩ (soft-delete). Tự động hủy hết các phân
     * công bệnh nhân đang active. Bác sĩ vẫn còn trong DB và có thể được khôi
     * phục bằng cách cập nhật status = active qua PUT /{id}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deactivateDoctor(@PathVariable Integer id) {
        try {
            doctorService.deactivateDoctor(id);
            return ResponseEntity.ok("Bác sĩ đã được ngừng hoạt động và toàn bộ phân công active đã được hủy.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * [ADMIN] Xóa vĩnh viễn bác sĩ khỏi hệ thống. Yêu cầu bác sĩ phải ở trạng
     * thái inactive trước. Xóa toàn bộ: Doctor profile + User (tài khoản đăng
     * nhập) + tất cả assignment. Hành động này KHÔNG THỂ hoàn tác.
     */
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<String> hardDeleteDoctor(@PathVariable Integer id) {
        try {
            doctorService.hardDeleteDoctor(id);
            return ResponseEntity.ok("Bác sĩ đã được xóa vĩnh viễn khỏi hệ thống.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
