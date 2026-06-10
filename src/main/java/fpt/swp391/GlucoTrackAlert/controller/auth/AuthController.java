package fpt.swp391.GlucoTrackAlert.controller.auth;

import fpt.swp391.GlucoTrackAlert.dto.login.LoginRequest;
import fpt.swp391.GlucoTrackAlert.dto.login.LoginResponse;
import fpt.swp391.GlucoTrackAlert.dto.login.ForgotPasswordRequest;
import fpt.swp391.GlucoTrackAlert.dto.login.ResetPasswordRequest;
import fpt.swp391.GlucoTrackAlert.dto.register.RegisterRequest;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.service.register.UserService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            userService.register(request);
            return ResponseEntity.ok("Mã xác nhận đã được gửi đến email của bạn.");
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestParam("otp") String otp) {
        try {
            userService.activateUser(otp);
            return ResponseEntity.ok("Xác nhận thành công! Bạn có thể đăng nhập.");
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = userService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            userService.forgotPassword(request);
            return ResponseEntity.ok("Mã OTP đặt lại mật khẩu đã được gửi đến email của bạn.");
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            userService.resetPassword(request);
            return ResponseEntity.ok("Mật khẩu của bạn đã được đặt lại thành công. Bạn có thể đăng nhập ngay.");
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            Principal principal,
            @RequestBody Map<String, String> body) {
        try {
            String email = principal.getName();
            String oldPassword = body.get("oldPassword");
            String newPassword = body.get("newPassword");
            if (oldPassword == null || newPassword == null) {
                return ResponseEntity.badRequest().body("Thiếu thông tin mật khẩu");
            }
            userService.changePassword(email, oldPassword, newPassword);
            return ResponseEntity.ok("Đổi mật khẩu thành công");
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }
}