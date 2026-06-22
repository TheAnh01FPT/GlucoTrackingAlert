package fpt.swp391.GlucoTrackAlert.controller.patient;

import fpt.swp391.GlucoTrackAlert.model.patient.ProfileChangeRequest;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.patient.ProfileChangeRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;

@Controller
public class ProfileChangeRequestController {

    private final ProfileChangeRequestService requestService;
    private final UserRepository userRepository;

    @Autowired
    public ProfileChangeRequestController(ProfileChangeRequestService requestService, UserRepository userRepository) {
        this.requestService = requestService;
        this.userRepository = userRepository;
    }

    private User getLoggedInUser() {
        String email = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Tài khoản chưa đăng nhập hoặc không tồn tại"));
    }

    @PostMapping("/patient/profile/request-change")
    public String submitChangeRequest(@RequestParam("fieldName") String fieldName,
                                      @RequestParam("reason") String reason,
                                      @RequestParam("evidenceFile") MultipartFile evidenceFile,
                                      RedirectAttributes redirectAttributes) {
        try {
            User loggedInUser = getLoggedInUser();
            requestService.createRequest(loggedInUser.getId(), fieldName, reason, evidenceFile);
            redirectAttributes.addFlashAttribute("successMessage", 
                    "Gửi yêu cầu thay đổi thông tin thành công. Yêu cầu đang được chờ Admin phê duyệt.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/patient/profile";
    }

    @GetMapping("/admin/requests")
    public String viewAdminRequests(Model model) {
        try {
            List<ProfileChangeRequest> requests = requestService.getAllRequests();
            model.addAttribute("requests", requests);
            return "admin/requests";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Lỗi tải danh sách yêu cầu: " + e.getMessage());
            return "admin/requests";
        }
    }

    @PostMapping("/admin/requests/{id}/approve")
    public String approveRequest(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            User loggedInUser = getLoggedInUser();
            requestService.approveRequest(id, loggedInUser.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Đã phê duyệt yêu cầu thay đổi thông tin thành công.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Phê duyệt thất bại: " + e.getMessage());
        }
        return "redirect:/admin/requests";
    }

    @PostMapping("/admin/requests/{id}/reject")
    public String rejectRequest(@PathVariable Long id,
                                @RequestParam("rejectionReason") String rejectionReason,
                                RedirectAttributes redirectAttributes) {
        try {
            User loggedInUser = getLoggedInUser();
            requestService.rejectRequest(id, loggedInUser.getId(), rejectionReason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã từ chối yêu cầu thay đổi thông tin.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Từ chối thất bại: " + e.getMessage());
        }
        return "redirect:/admin/requests";
    }
}
