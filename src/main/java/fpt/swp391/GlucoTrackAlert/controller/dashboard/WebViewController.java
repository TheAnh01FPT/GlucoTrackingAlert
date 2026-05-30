package fpt.swp391.GlucoTrackAlert.controller.dashboard;

import fpt.swp391.GlucoTrackAlert.dto.role.RoleRequest;
import fpt.swp391.GlucoTrackAlert.dto.user.UserAdminRequest;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.service.role.RoleService;
import fpt.swp391.GlucoTrackAlert.service.user.UserAdminService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class WebViewController {

    private final UserAdminService userAdminService;
    private final RoleService roleService;

    // Inject 2 Service chuẩn của bạn vào
    public WebViewController(UserAdminService userAdminService, RoleService roleService) {
        this.userAdminService = userAdminService;
        this.roleService = roleService;
    }

    // Giao diện tổng hợp: Đổ dữ liệu thật từ Service ra View
    @GetMapping("/dashboard")
    public String showDashboard(Model model) {
        // Chỉ lấy ra danh sách User thuộc nhóm PATIENT hoặc DOCTOR để hiển thị lên bảng
        List<User> allUsers = userAdminService.getAllUsers();
        List<User> filteredUsers = allUsers.stream()
                .filter(user -> user.getRole() != null &&
                        (user.getRole().getName().equalsIgnoreCase("PATIENT") ||
                                user.getRole().getName().equalsIgnoreCase("DOCTOR")))
                .collect(Collectors.toList());

        model.addAttribute("users", filteredUsers);

        // Giữ lại tất cả role để phục vụ việc hiển thị danh sách cấu hình phía dưới nếu cần
        model.addAttribute("roles", roleService.getAllRoles());

        return "user/user-management";
    }
    // ================= XỬ LÝ CRUD USER =================
    @PostMapping("/users/save")
    public String saveUser(@RequestParam(required = false) Long id,
                           @RequestParam String email,
                           @RequestParam(required = false) String password,
                           @RequestParam String status,
                           @RequestParam(required = false) Boolean emailVerified,
                           @RequestParam String roleName) {
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
            } else {
                userAdminService.createUserByAdmin(request);
            }
        } catch (Exception e) {
            e.printStackTrace(); // Bạn có thể bổ sung RedirectAttributes để bắn thông báo lỗi ra UI nếu muốn
        }
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/users/delete/{id}")
    public String deleteUser(@PathVariable Long id) {
        try {
            userAdminService.deleteUserByAdmin(id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/admin/dashboard";
    }

    // ================= XỬ LÝ CRUD ROLE =================
    @PostMapping("/roles/save")
    public String saveRole(@RequestParam(required = false) Long id,
                           @RequestParam String name,
                           @RequestParam String description) {
        try {
            RoleRequest request = RoleRequest.builder()
                    .name(name.trim())
                    .description(description.trim())
                    .build();

            if (id != null) {
                roleService.updateRole(id, request);
            } else {
                roleService.createRole(request);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/roles/delete/{id}")
    public String deleteRole(@PathVariable Long id) {
        try {
            roleService.deleteRole(id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/admin/dashboard";
    }
}