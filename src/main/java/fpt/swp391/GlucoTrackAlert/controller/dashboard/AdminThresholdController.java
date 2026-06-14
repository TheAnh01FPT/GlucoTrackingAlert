package fpt.swp391.GlucoTrackAlert.controller.dashboard;

import fpt.swp391.GlucoTrackAlert.model.HealthThreshold;
import fpt.swp391.GlucoTrackAlert.service.HealthThresholdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/thresholds")
public class AdminThresholdController {

    @Autowired
    private HealthThresholdService healthThresholdService;

    @GetMapping
    public String listThresholds(Model model) {
        // Admin chỉ xem và sửa ngưỡng mặc định (patient = null)
        model.addAttribute("thresholds", healthThresholdService.findDefaults());
        return "admin/thresholds";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @RequestParam String normalMin,
                       @RequestParam String normalMax,
                       @RequestParam String warningMin,
                       @RequestParam String warningMax,
                       @RequestParam(required = false) String description,
                       RedirectAttributes redirectAttributes) {
        Optional<HealthThreshold> opt = healthThresholdService.findById(id);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy ngưỡng");
            return "redirect:/admin/thresholds";
        }
        HealthThreshold t = opt.get();
        try {
            BigDecimal nMin = new BigDecimal(normalMin);
            BigDecimal nMax = new BigDecimal(normalMax);
            BigDecimal wMin = new BigDecimal(warningMin);
            BigDecimal wMax = new BigDecimal(warningMax);
            healthThresholdService.validateRange(nMin, nMax, wMin, wMax);
            t.setNormalMin(nMin);
            t.setNormalMax(nMax);
            t.setWarningMin(wMin);
            t.setWarningMax(wMax);
            t.setDescription(description);
            healthThresholdService.save(t);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thành công");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Giá trị không hợp lệ: " + ex.getMessage());
        }
        return "redirect:/admin/thresholds";
    }
}
