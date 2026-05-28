package fpt.swp391.GlucoTrackAlert.controller;

import fpt.swp391.GlucoTrackAlert.dto.LoginRequest;
import fpt.swp391.GlucoTrackAlert.dto.RegisterRequest;
import fpt.swp391.GlucoTrackAlert.model.User;
import fpt.swp391.GlucoTrackAlert.service.UserService;
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
            User u = userService.register(request);
            return ResponseEntity.ok("Registered. Please check your email to verify.");
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestParam("token") String token) {
        try {
            User u = userService.activateUser(token);
            return ResponseEntity.ok("Email verified. You can login now.");
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        // simple login implementation: authenticate by matching email and password
        // we will implement a basic login here for demonstration; for production use JWT
        return ResponseEntity.status(501).body("Login not implemented yet. Use /api/auth/login to implement authentication (JWT)");
    }
}

