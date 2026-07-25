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
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request, org.springframework.validation.BindingResult bindingResult) {
        // Nếu có bất kỳ lỗi Validate nào từ Validation Annotations
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getFieldError() != null ? bindingResult.getFieldError().getDefaultMessage() : "Dữ liệu nhập không hợp lệ";
            return ResponseEntity.badRequest().body(Map.of("message", errorMessage));
        }

        try {
            userService.register(request);
            return ResponseEntity.ok(Map.of("message", "Mã xác nhận đã được gửi đến email của bạn."));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestParam("otp") String otp) {
        try {
            userService.activateUser(otp);
            return ResponseEntity.ok(Map.of("message", "Xác nhận thành công! Bạn có thể đăng nhập."));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestParam("email") String email) {
        try {
            userService.resendOtp(email);
            return ResponseEntity.ok(Map.of("message", "Mã xác nhận mới đã được gửi đến email của bạn."));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = userService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            userService.forgotPassword(request);
            return ResponseEntity.ok(Map.of("message", "Mã OTP đặt lại mật khẩu đã được gửi đến email của bạn."));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            userService.resetPassword(request);
            return ResponseEntity.ok(Map.of("message", "Mật khẩu của bạn đã được đặt lại thành công. Bạn có thể đăng nhập ngay."));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
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
                return ResponseEntity.badRequest().body(Map.of("message", "Thiếu thông tin mật khẩu"));
            }
            // Validate new password length
            if (newPassword.length() < 6 || newPassword.length() > 32) {
                return ResponseEntity.badRequest().body(Map.of("message", "Mật khẩu phải từ 6 đến 32 ký tự"));
            }
            // Validate pattern: at least one lower, one upper, one digit
            String pattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$";
            if (!Pattern.matches(pattern, newPassword)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Mật khẩu phải chứa ít nhất 1 chữ hoa, 1 chữ thường và 1 chữ số"));
            }
            userService.changePassword(email, oldPassword, newPassword);
            return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công"));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}