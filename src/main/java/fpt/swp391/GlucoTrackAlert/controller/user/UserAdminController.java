package fpt.swp391.GlucoTrackAlert.controller.user;

import fpt.swp391.GlucoTrackAlert.dto.user.UserAdminRequest;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.service.user.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    // Tích hợp cả lấy tất cả và lọc theo Role ID thông qua Query Parameter (?roleId=...)
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers(@RequestParam(value = "roleId", required = false) Long roleId) {
        if (roleId != null) {
            return ResponseEntity.ok(userAdminService.getUsersFilteredByRole(roleId));
        }
        return ResponseEntity.ok(userAdminService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        try {
            User user = userAdminService.getUserById(id);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createUser(@Valid @RequestBody UserAdminRequest request) {
        try {
            User createdUser = userAdminService.createUserByAdmin(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @Valid @RequestBody UserAdminRequest request) {
        try {
            User updatedUser = userAdminService.updateUserByAdmin(id, request);
            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateUserStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        try {
            String status = payload.get("status");
            User updatedUser = userAdminService.updateUserStatusByAdmin(id, status);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Cập nhật trạng thái tài khoản thành công.",
                    "user", updatedUser
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<?> resetPatientPassword(@PathVariable Long id) {
        try {
            User updatedUser = userAdminService.resetPatientPasswordByAdmin(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Mật khẩu mới đã được gửi tới email bệnh nhân.",
                    "user", updatedUser
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

}