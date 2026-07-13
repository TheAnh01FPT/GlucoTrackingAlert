package fpt.swp391.GlucoTrackAlert.controller.page;

import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileResponse;
import fpt.swp391.GlucoTrackAlert.dto.relative.RelativeResponse;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.patient.PatientService;
import fpt.swp391.GlucoTrackAlert.service.relative.RelativeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class PageController {

    private final PatientService patientService;
    private final UserRepository userRepository;
    private final RelativeService relativeService;

    @Autowired
    public PageController(PatientService patientService,
                          UserRepository userRepository,
                          RelativeService relativeService) {
        this.patientService = patientService;
        this.userRepository = userRepository;
        this.relativeService = relativeService;
    }

    @GetMapping("/")
    public String indexPage() {
        return "index";
    }

    @GetMapping("/login")
    public String loginPage() { return "login/login"; }

    @GetMapping("/register")
    public String registerPage() { return "register/register"; }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "login/forgot-password";
    }

    @GetMapping({"/patient/homepage", "/patient/home"})
    public String patientDashboard(@RequestParam(value = "userId", required = false) Long userId, Model model) {
        if (userId == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                String email = (String) auth.getPrincipal();
                User user = userRepository.findByEmail(email).orElse(null);
                if (user != null) userId = user.getId();
            }
        }
        if (userId == null) userId = 1L;

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

    @GetMapping({"/doctor/homepage", "/doctor/home"})
    public String doctorDashboard() {
        return "doctor-home";
    }

    @GetMapping("/doctor/my-patients")
    public String doctorMyPatients() {
        return "my-patients";
    }

    @GetMapping("/doctor/settings")
    public String doctorSettings() {
        return "settings";
    }

    @GetMapping("/doctor/prescriptions")
    public String doctorPrescriptions() {
        return "prescriptions";
    }

    @GetMapping("/health-reminders")
    public String healthRemindersPage(Model model) {
        try {
            String email = (String) SecurityContextHolder.getContext()
                    .getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            PatientProfileResponse profile = patientService.getProfileByUserId(user.getId());
            model.addAttribute("patientId", profile.getId());
            model.addAttribute("patientName", profile.getFullName());
        } catch (Exception e) {
            model.addAttribute("patientId", 1L);
            model.addAttribute("patientName", "Bệnh nhân");
        }
        return "health-reminders";
    }

    @GetMapping("/patient/medications")
    public String medicationsPage(Model model) {
        try {
            String email = (String) SecurityContextHolder.getContext()
                    .getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            PatientProfileResponse profile = patientService.getProfileByUserId(user.getId());
            model.addAttribute("patientId", profile.getId());
            model.addAttribute("patientName", profile.getFullName());
        } catch (Exception e) {
            model.addAttribute("patientId", 1L);
            model.addAttribute("patientName", "Bệnh nhân");
        }
        return "patient/medications";
    }

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
            if (Boolean.TRUE.equals(profile.getIsPregnant())) {
                condition = "pregnant";
            }

            String gender = "male";
            if (profile.getGender() != null) {
                String g = profile.getGender().toLowerCase().trim();
                if (g.equals("female") || g.equals("nữ") || g.equals("nu") || g.equals("f")) {
                    gender = "female";
                }
            }

            List<RelativeResponse> relatives = relativeService.getRelativesByPatientId(profile.getId());

            model.addAttribute("patientId",   profile.getId());
            model.addAttribute("patientName", profile.getFullName());
            model.addAttribute("gender",      gender);
            model.addAttribute("ageGroup",    ageGroup);
            model.addAttribute("condition",   condition);
            model.addAttribute("age",         age);
            model.addAttribute("bmi",         profile.getBmi() != null ? profile.getBmi().toString() : "—");
            model.addAttribute("relatives",   relatives);

        } catch (Exception e) {
            model.addAttribute("patientId",   1L);
            model.addAttribute("patientName", "Bệnh nhân");
            model.addAttribute("gender",      "male");
            model.addAttribute("ageGroup",    "elder");
            model.addAttribute("condition",   "none");
            model.addAttribute("age",         65);
            model.addAttribute("bmi",         "—");
            model.addAttribute("relatives",   List.of());
        }
        return "meal-logs";
    }
}