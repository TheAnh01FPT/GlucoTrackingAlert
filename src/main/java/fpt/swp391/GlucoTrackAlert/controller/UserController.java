package fpt.swp391.GlucoTrackAlert.controller;

import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    /**
     * Admin dùng khi tạo profile bác sĩ: resolve userId từ email
     * (tránh admin phải biết ID thô).
     */
    @GetMapping("/by-email")
    public ResponseEntity<?> findByEmail(@RequestParam String email) {
        return userRepository.findByEmail(email)
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(Map.of(
                        "id",    u.getId(),
                        "email", u.getEmail()
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}