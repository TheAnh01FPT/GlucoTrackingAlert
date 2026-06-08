package fpt.swp391.GlucoTrackAlert.controller.page;

import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileResponse;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.patient.PatientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @Autowired private PatientService patientService;
    @Autowired private UserRepository userRepository;

    @GetMapping("/login")
    public String loginPage() { return "login/login"; }

    @GetMapping("/register")
    public String registerPage() { return "register/register"; }

    @GetMapping("/patient/homepage")
    public String patientDashboard() { return "homepage/homepage"; }

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