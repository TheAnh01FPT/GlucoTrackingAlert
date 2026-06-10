package fpt.swp391.GlucoTrackAlert.controller.auth;

import fpt.swp391.GlucoTrackAlert.dto.login.LoginRequest;
import fpt.swp391.GlucoTrackAlert.dto.login.LoginResponse;
import fpt.swp391.GlucoTrackAlert.dto.register.RegisterRequest;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.service.register.UserService;
import jakarta.validation.Valid;
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

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestParam("email") String email) {
        try {
            userService.resendOtp(email);
            return ResponseEntity.ok("Mã xác nhận mới đã được gửi đến email của bạn.");
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
}