package fpt.swp391.GlucoTrackAlert.controller.dashboard;
import fpt.swp391.GlucoTrackAlert.dto.user.UserAdminRequest;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.service.role.RoleService;
import fpt.swp391.GlucoTrackAlert.service.user.UserAdminService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class WebViewController {

    private final UserAdminService userAdminService;
    private final RoleService roleService;

    public WebViewController(UserAdminService userAdminService, RoleService roleService) {
        this.userAdminService = userAdminService;
        this.roleService = roleService;
    }

    @GetMapping("/dashboard")
    public String showDashboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        Page<User> userPage = userAdminService.getUsersPaged(page, size);

        model.addAttribute("users", userPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", userPage.getTotalPages());
        model.addAttribute("totalItems", userPage.getTotalElements());
        model.addAttribute("pageSize", size);
        model.addAttribute("roles", roleService.getAllRoles());

        return "user/user-management";
    }

    @GetMapping("/doctors")
    public String showDoctorsPage(Model model) {
        return "admin/doctors";
    }

    @GetMapping("/doctor-introductions")
    public String doctorIntroductionsPage() {
        return "admin/doctor-introductions";
    }

    @GetMapping("/assignments")
    public String showAssignmentsPage(Model model) {
        return "admin/assignments";
    }

    @GetMapping("/patients")
    public String showPatientsPage(Model model) {
        return "my-patients";
    }

    @PostMapping("/users/save")
    public String saveUser(@RequestParam(required = false) Long id,
                           @RequestParam String email,
                           @RequestParam(required = false) String password,
                           @RequestParam String status,
                           @RequestParam(required = false) Boolean emailVerified,
                           @RequestParam String roleName,
                           RedirectAttributes redirectAttributes) {
        try {
            UserAdminRequest request = UserAdminRequest.builder()
                    .email(email.trim())
                    .password(password)
                    .status(status)
                    .emailVerified(emailVerified != null ? emailVerified : false)
                    .roleName(roleName)
                    .build();

            if (id != null) {
                userAdminService.updateUserByAdmin(id, request);
                redirectAttributes.addFlashAttribute("successMessage", "Cập nhật tài khoản thành công!");
            } else {
                userAdminService.createUserByAdmin(request);
                redirectAttributes.addFlashAttribute("successMessage", "Tạo tài khoản thành công!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/dashboard";
    }

}