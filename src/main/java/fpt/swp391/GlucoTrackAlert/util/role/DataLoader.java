package fpt.swp391.GlucoTrackAlert.util.role;

import fpt.swp391.GlucoTrackAlert.model.role.Role;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.role.RoleRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataLoader implements CommandLineRunner {
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public DataLoader(RoleRepository roleRepository, UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        String[] defaultRoles = {"ADMIN", "PATIENT", "DOCTOR"};
        for (String r : defaultRoles) {
            roleRepository.findByName(r).orElseGet(() -> roleRepository.save(Role.builder().name(r).description(r.toLowerCase()).build()));
        }

        // Tạo tài khoản admin mặc định nếu chưa có
        if (!userRepository.existsByEmail("admin@gmail.com")) {
            Role adminRole = roleRepository.findByName("ADMIN").orElse(null);
            if (adminRole != null) {
                User admin = User.builder()
                        .email("admin@gmail.com")
                        .passwordHash(passwordEncoder.encode("admin123"))
                        .role(adminRole)
                        .fullName("Super Admin")
                        .status("active")
                        .emailVerified(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                userRepository.save(admin);
            }
        }
    }
}

