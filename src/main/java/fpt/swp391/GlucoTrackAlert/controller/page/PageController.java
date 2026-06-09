package fpt.swp391.GlucoTrackAlert.controller.page;

import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileResponse;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.patient.PatientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

    private final PatientService patientService;
    private final UserRepository userRepository;

    @Autowired
    public PageController(PatientService patientService, UserRepository userRepository) {
        this.patientService = patientService;
        this.userRepository = userRepository;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login/login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register/register";
    }

    @GetMapping("/patient/homepage")
    public String patientDashboard(@RequestParam(value = "userId", required = false) Long userId, Model model) {
        // Resolve user dynamically from SecurityContext if not provided in URL
        if (userId == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                String email = (String) auth.getPrincipal();
                User user = userRepository.findByEmail(email).orElse(null);
                if (user != null) {
                    userId = user.getId();
                }
            }
        }
        
        // Fallback default
        if (userId == null) {
            userId = 1L;
        }

        model.addAttribute("userId", userId);
        
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                model.addAttribute("userEmail", user.getEmail());
                
                try {
                    PatientProfileResponse profile = patientService.getProfileByUserId(userId);
                    model.addAttribute("patientName", profile.getFullName());
                } catch (Exception e) {
                    model.addAttribute("patientName", "Chưa tạo hồ sơ");
                }
            } else {
                model.addAttribute("userEmail", "Không tồn tại trong DB");
                model.addAttribute("patientName", "Tài khoản chưa khởi tạo");
            }
        } catch (Exception e) {
            model.addAttribute("userEmail", "Lỗi kết nối");
            model.addAttribute("patientName", "Khách");
        }
        
        return "patient/home";
    }

    @GetMapping("/doctor/homepage")
    public String doctorDashboard() {
        return "doctor/dashboard";
    }
}