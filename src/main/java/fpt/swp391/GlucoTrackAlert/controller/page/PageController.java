package fpt.swp391.GlucoTrackAlert.controller.page;

import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileResponse;
import fpt.swp391.GlucoTrackAlert.dto.relative.RelativeResponse;
import fpt.swp391.GlucoTrackAlert.doctor.Doctor;
import fpt.swp391.GlucoTrackAlert.doctor.DoctorIntroduction;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.doctor.DoctorRepository;
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
    private final DoctorRepository doctorRepository;
    private final fpt.swp391.GlucoTrackAlert.repository.BannerRepository bannerRepository;

    @Autowired
    public PageController(PatientService patientService,
            UserRepository userRepository,
            RelativeService relativeService,
            DoctorRepository doctorRepository,
            fpt.swp391.GlucoTrackAlert.repository.BannerRepository bannerRepository) {
        this.patientService = patientService;
        this.userRepository = userRepository;
        this.relativeService = relativeService;
        this.doctorRepository = doctorRepository;
        this.bannerRepository = bannerRepository;
    }

    private static final int MAX_FEATURED_DOCTORS = 4; // đúng 1 hàng với layout col-lg-3

    /**
     * Trang chủ hiển thị "giới thiệu đội ngũ bác sĩ" tự động lấy từ hồ sơ bác
     * sĩ (Doctor) đang active — không cần admin nhập tay lại lần 2. Ưu tiên bác
     * sĩ nhiều năm kinh nghiệm nhất lên đầu.
     */
    @GetMapping("/")
    public String indexPage(Model model) {
        try {
            List<DoctorIntroduction> doctors = doctorRepository.findByStatus("active").stream()
                    .sorted((a, b) -> Integer.compare(
                    b.getExperienceYears() == null ? 0 : b.getExperienceYears(),
                    a.getExperienceYears() == null ? 0 : a.getExperienceYears()))
                    .limit(MAX_FEATURED_DOCTORS)
                    .map(this::toShowcaseCard)
                    .toList();
            model.addAttribute("doctors", doctors);
        } catch (Exception e) {
            model.addAttribute("doctors", List.of());
        }
        model.addAttribute("banners", bannerRepository.findByStatusOrderByDisplayOrderAsc(true));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        model.addAttribute("isAuthenticated", isAuthenticated);
        if (isAuthenticated) {
            String role = auth.getAuthorities().stream().findFirst().map(a -> a.getAuthority()).orElse("");
            model.addAttribute("userRole", role);
        }

        return "index";
    }

    private DoctorIntroduction toShowcaseCard(Doctor d) {
        DoctorIntroduction card = new DoctorIntroduction();
        card.setDoctorId(d.getId());
        card.setDisplayName(d.getFullName());
        card.setTitle(d.getDegree());
        card.setSpecialization(d.getSpecialization());
        card.setIntroduction(d.getIntroduction());
        card.setAvatarUrl(d.getAvatarUrl());
        return card;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login/login";
    }

    @GetMapping("/oauth2/success")
    public String oauth2Success(Model model, @RequestParam("token") String token,
            @RequestParam("email") String email,
            @RequestParam("role") String role) {
        model.addAttribute("token", token);
        model.addAttribute("email", email);
        model.addAttribute("role", role);
        return "login/oauth2-success";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register/register";
    }

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
                if (user != null) {
                    userId = user.getId();
                }
            }
        }
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

    @GetMapping({"/doctor/homepage", "/doctor/home"})
    public String doctorDashboard() {
        return "doctor/doctor-home";
    }

    @GetMapping("/doctor/my-patients")
    public String doctorMyPatients() {
        return "doctor/my-patients";
    }

    @GetMapping("/doctor/settings")
    public String doctorSettings() {
        return "/doctor/settings";
    }

    @GetMapping("/doctor/prescriptions")
    public String doctorPrescriptions() {
        return "doctor/prescriptions";
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
            // KHÔNG fallback về patientId=1 (bệnh nhân khác) nữa — trước đây làm vậy
            // khiến tài khoản chưa có hồ sơ vô tình gọi API bằng id của người khác,
            // vừa lộ dữ liệu vừa gây lỗi "Không thể tải dữ liệu" phía client.
            // Đưa thẳng người dùng sang trang tạo hồ sơ bệnh nhân.
            return "redirect:/patient/profile/edit?error=Hay tao ho so benh nhan truoc khi dung tinh nang nhac nho.";
        }
        return "health-reminders";
    }

    @GetMapping("/patient/choose-doctor")
    public String chooseDoctorPage(Model model) {
        Long userId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            String email = (String) auth.getPrincipal();
            User user = userRepository.findByEmail(email).orElse(null);
            if (user != null) {
                userId = user.getId();
            }
        }
        model.addAttribute("userId", userId);
        return "patient/choose-doctor";
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
            // Tương tự health-reminders: không fallback về patientId=1 nữa.
            return "redirect:/patient/profile/edit?error=Hay tao ho so benh nhan truoc khi dung tinh nang thuoc.";
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

            model.addAttribute("patientId", profile.getId());
            model.addAttribute("patientName", profile.getFullName());
            model.addAttribute("gender", gender);
            model.addAttribute("ageGroup", ageGroup);
            model.addAttribute("condition", condition);
            model.addAttribute("age", age);
            model.addAttribute("bmi", profile.getBmi() != null ? profile.getBmi().toString() : "—");
            model.addAttribute("relatives", relatives);

        } catch (Exception e) {
            model.addAttribute("patientId", 1L);
            model.addAttribute("patientName", "Bệnh nhân");
            model.addAttribute("gender", "male");
            model.addAttribute("ageGroup", "elder");
            model.addAttribute("condition", "none");
            model.addAttribute("age", 65);
            model.addAttribute("bmi", "—");
            model.addAttribute("relatives", List.of());
        }
        return "meal-logs";
    }
}
