package fpt.swp391.GlucoTrackAlert.controller.admin;

import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileResponse;
import fpt.swp391.GlucoTrackAlert.service.patient.PatientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/card-verifications")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCardVerificationController {

    private final PatientService patientService;

    @Autowired
    public AdminCardVerificationController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping
    public String viewCardVerifications(Model model) {
        try {
            List<PatientProfileResponse> patients = patientService.getAllPatients();
            model.addAttribute("patients", patients);
            return "admin/card-verifications";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Lỗi tải danh sách thẻ bệnh nhân: " + e.getMessage());
            return "admin/card-verifications";
        }
    }

    @PostMapping("/{patientId}/verify-cccd")
    public String verifyIdentityCard(@PathVariable Long patientId,
                                     @RequestParam("status") String status,
                                     RedirectAttributes redirectAttributes) {
        try {
            patientService.verifyIdentityCard(patientId, status);
            String statusText = "VERIFIED".equalsIgnoreCase(status) ? "Xác minh chính chủ" : "Báo sai lệch/Từ chối";
            redirectAttributes.addFlashAttribute("successMessage", 
                    "Cập nhật trạng thái CCCD bệnh nhân thành công: " + statusText);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "Lỗi đối soát CCCD: " + e.getMessage());
        }
        return "redirect:/admin/card-verifications";
    }

    @PostMapping("/{patientId}/verify-bhyt")
    public String verifyInsuranceCard(@PathVariable Long patientId,
                                      @RequestParam("status") String status,
                                      RedirectAttributes redirectAttributes) {
        try {
            patientService.verifyInsuranceCard(patientId, status);
            String statusText = "VERIFIED".equalsIgnoreCase(status) ? "Xác minh chính chủ" : "Báo sai lệch/Từ chối";
            redirectAttributes.addFlashAttribute("successMessage", 
                    "Cập nhật trạng thái BHYT bệnh nhân thành công: " + statusText);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "Lỗi đối soát BHYT: " + e.getMessage());
        }
        return "redirect:/admin/card-verifications";
    }
}
