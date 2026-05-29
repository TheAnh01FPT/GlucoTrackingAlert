package fpt.swp391.GlucoTrackAlert.util.role;

import fpt.swp391.GlucoTrackAlert.model.Role;
import fpt.swp391.GlucoTrackAlert.repository.role.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {
    private final RoleRepository roleRepository;

    public DataLoader(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        String[] defaultRoles = {"ADMIN","GUEST","PATIENT","DOCTOR","RELATIVE","PATIENT_RISK_AI","DOCTOR_SUPPORT_AI","GMAIL_GATEWAY"};
        for (String r : defaultRoles) {
            roleRepository.findByName(r).orElseGet(() -> roleRepository.save(Role.builder().name(r).description(r.toLowerCase()).build()));
        }
    }
}

