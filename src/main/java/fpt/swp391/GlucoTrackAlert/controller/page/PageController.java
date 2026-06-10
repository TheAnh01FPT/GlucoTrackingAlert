package fpt.swp391.GlucoTrackAlert.controller.page;

import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileResponse;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.patient.PatientService;
import org.springframework.beans.factory.annotation.Autowired;
<<<<<<< HEAD
=======
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
>>>>>>> 63fc967ead7618bdcf459ccfb93361dac43855b4
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

<<<<<<< HEAD
    @Autowired private PatientService patientService;
    @Autowired private UserRepository userRepository;
=======
    private final PatientService patientService;
    private final UserRepository userRepository;

    @Autowired
    public PageController(PatientService patientService, UserRepository userRepository) {
        this.patientService = patientService;
        this.userRepository = userRepository;
    }
>>>>>>> 63fc967ead7618bdcf459ccfb93361dac43855b4

    @GetMapping("/login")
    public String loginPage() { return "login/login"; }

    @GetMapping("/register")
    public String registerPage() { return "register/register"; }

    @GetMapping("/patient/homepage")
<<<<<<< HEAD
<<<<<<< HEAD
    public String patientDashboard() { return "homepage/homepage"; }
=======
    public String patientDashboard() {
=======
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
        
>>>>>>> 63fc967ead7618bdcf459ccfb93361dac43855b4
        return "patient/home";
    }
>>>>>>> c545ee98a670e61bfc41298782e381f13be98b14

    @GetMapping("/doctor/homepage")
    public String doctorDashboard() { return "homepage/homepage"; }

    @GetMapping("/meal-logs")
    public String mealLogsPage(Model model) {
        try {
            String email = (String) SecurityContextHolder.getContext()
                    .getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            PatientProfileResponse profile = patientService.getProfileByUserId(user.getId());

            int age = profile.getAge() != null ? profile.getAge() : 65;
            String ageGroup = age >= 65 ? "elder" : (age >= 41 ? "middle" : "young");

            String condition = "none";
            if (profile.getBmi() != null && profile.getBmi().doubleValue() >= 30) {
                condition = "obese";
            }

            String gender = "male";
            if (profile.getGender() != null) {
                String g = profile.getGender().toLowerCase().trim();
                if (g.equals("female") || g.equals("nữ") || g.equals("nu") || g.equals("f")) {
                    gender = "female";
                }
            }

            model.addAttribute("patientId",   user.getId());
            model.addAttribute("patientName", profile.getFullName());
            model.addAttribute("gender",      gender);
            model.addAttribute("ageGroup",    ageGroup);
            model.addAttribute("condition",   condition);
            model.addAttribute("age",         age);
            model.addAttribute("bmi",         profile.getBmi() != null ? profile.getBmi().toString() : "—");

        } catch (Exception e) {
            model.addAttribute("patientId",   1L);
            model.addAttribute("patientName", "Bệnh nhân");
            model.addAttribute("gender",      "male");
            model.addAttribute("ageGroup",    "elder");
            model.addAttribute("condition",   "none");
            model.addAttribute("age",         65);
            model.addAttribute("bmi",         "—");
        }
        return "meal-logs";
    }
}