package fpt.swp391.GlucoTrackAlert.controller.user;

import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.regex.Pattern;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
public class PasswordController {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    // Thay thế @Autowired cũ bằng Constructor Injection sạch đẹp
    public PasswordController(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping({"/doctor/api/change-password", "/patient/api/change-password"})
    public ResponseEntity<?> changePassword(@RequestParam("currentPassword") String currentPassword,
                                            @RequestParam("newPassword") String newPassword) {
        Map<String, String> response = new HashMap<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            response.put("error", "Người dùng chưa đăng nhập.");
            return ResponseEntity.badRequest().body(response);
        }

        String email = auth.getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            response.put("error", "Không tìm thấy người dùng.");
            return ResponseEntity.badRequest().body(response);
        }

        User user = optionalUser.get();

        // ĐÃ SỬA: Thay thế user.getPassword() thành user.getPasswordHash() cho khớp với Model
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            response.put("error", "Mật khẩu hiện tại không đúng.");
            return ResponseEntity.badRequest().body(response);
        }

        if (newPassword == null) {
            response.put("error", "Mật khẩu mới không được để trống.");
            return ResponseEntity.badRequest().body(response);
        }
        // Validate length
        if (newPassword.length() < 6 || newPassword.length() > 32) {
            response.put("error", "Mật khẩu phải từ 6 đến 32 ký tự.");
            return ResponseEntity.badRequest().body(response);
        }
        // Validate pattern: at least one lower, one upper, one digit
        String pattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$";
        if (!Pattern.matches(pattern, newPassword)) {
            response.put("error", "Mật khẩu phải chứa ít nhất 1 chữ hoa, 1 chữ thường và 1 chữ số.");
            return ResponseEntity.badRequest().body(response);
        }

        // ĐÃ SỬA: Thay thế user.setPassword(...) thành user.setPasswordHash(...) cho khớp với Model
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        response.put("success", "Cập nhật mật khẩu thành công!");
        return ResponseEntity.ok(response);
    }
}