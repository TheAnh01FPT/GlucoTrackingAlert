package fpt.swp391.GlucoTrackAlert.controller.relative;

import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileResponse;
import fpt.swp391.GlucoTrackAlert.dto.relative.RelativeRequest;
import fpt.swp391.GlucoTrackAlert.dto.relative.RelativeResponse;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.patient.PatientService;
import fpt.swp391.GlucoTrackAlert.service.relative.RelativeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/patient/relatives")
public class RelativeWebController {

    private final RelativeService relativeService;
    private final PatientService patientService;
    private final UserRepository userRepository;

    @Autowired
    public RelativeWebController(RelativeService relativeService, PatientService patientService, UserRepository userRepository) {
        this.relativeService = relativeService;
        this.patientService = patientService;
        this.userRepository = userRepository;
    }

    private User getLoggedInUser() {
        String email = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Tài khoản chưa đăng nhập hoặc không tồn tại"));
    }

    private PatientProfileResponse getLoggedInPatientProfile() {
        User loggedInUser = getLoggedInUser();
        return patientService.getProfileByUserId(loggedInUser.getId());
    }

    @GetMapping
    public String viewRelativesList(Model model) {
        try {
            User loggedInUser = getLoggedInUser();
            PatientProfileResponse patient = patientService.getProfileByUserId(loggedInUser.getId());
            List<RelativeResponse> relatives = relativeService.getRelativesByPatientId(patient.getId());
            model.addAttribute("relatives", relatives);
            model.addAttribute("patient", patient);
            model.addAttribute("userId", loggedInUser.getId());
            return "patient/relatives";
        } catch (Exception e) {
            return "redirect:/patient/profile/edit?error=Hay khoi tao ho so benh nhan truoc khi quan ly nguoi than.";
        }
    }

    @GetMapping("/new")
    public String showAddForm(Model model) {
        try {
            User loggedInUser = getLoggedInUser();
            PatientProfileResponse patient = patientService.getProfileByUserId(loggedInUser.getId());
            RelativeRequest request = RelativeRequest.builder()
                    .patientId(patient.getId())
                    .notifyEnabled(true)
                    .build();
            model.addAttribute("relativeForm", request);
            model.addAttribute("isNew", true);
            model.addAttribute("userId", loggedInUser.getId());
            return "patient/relative-form";
        } catch (Exception e) {
            return "redirect:/patient/relatives?error=" + e.getMessage();
        }
    }

    @GetMapping("/edit/{relativeId}")
    public String showEditForm(@PathVariable Long relativeId, Model model) {
        try {
            User loggedInUser = getLoggedInUser();
            PatientProfileResponse patient = patientService.getProfileByUserId(loggedInUser.getId());
            RelativeResponse relative = relativeService.getRelativeById(relativeId);

            // Access check: Ensure the relative belongs to the logged-in patient
            if (!relative.getPatientId().equals(patient.getId())) {
                throw new RuntimeException("Bạn không có quyền chỉnh sửa thông tin người thân của bệnh nhân khác!");
            }

            RelativeRequest request = RelativeRequest.builder()
                    .patientId(relative.getPatientId())
                    .fullName(relative.getFullName())
                    .relationship(relative.getRelationship())
                    .age(relative.getAge())
                    .phone(relative.getPhone())
                    .email(relative.getEmail())
                    .notifyEnabled(relative.getNotifyEnabled())
                    .build();
            model.addAttribute("relativeForm", request);
            model.addAttribute("relativeId", relativeId);
            model.addAttribute("isNew", false);
            model.addAttribute("userId", loggedInUser.getId());
            return "patient/relative-form";
        } catch (Exception e) {
            return "redirect:/patient/relatives?error=" + e.getMessage();
        }
    }

    @PostMapping("/save")
    public String saveRelative(@Valid @ModelAttribute("relativeForm") RelativeRequest request,
                               BindingResult result,
                               @RequestParam("isNew") boolean isNew,
                               @RequestParam(value = "relativeId", required = false) Long relativeId,
                               Model model) {
        User loggedInUser = getLoggedInUser();
        PatientProfileResponse patient = patientService.getProfileByUserId(loggedInUser.getId());
        
        // Enforce safety: Bind the correct patientId to prevent parameter spoofing
        request.setPatientId(patient.getId());

        if (result.hasErrors()) {
            model.addAttribute("isNew", isNew);
            model.addAttribute("relativeId", relativeId);
            model.addAttribute("userId", loggedInUser.getId());
            return "patient/relative-form";
        }
        try {
            if (isNew) {
                relativeService.addRelative(request);
            } else {
                // Access check before updating existing relative
                RelativeResponse relative = relativeService.getRelativeById(relativeId);
                if (!relative.getPatientId().equals(patient.getId())) {
                    throw new RuntimeException("Bạn không có quyền chỉnh sửa thông tin người thân của bệnh nhân khác!");
                }
                relativeService.updateRelative(relativeId, request);
            }
            return "redirect:/patient/relatives";
        } catch (Exception e) {
            model.addAttribute("isNew", isNew);
            model.addAttribute("relativeId", relativeId);
            model.addAttribute("userId", loggedInUser.getId());
            model.addAttribute("errorMessage", e.getMessage());
            return "patient/relative-form";
        }
    }

    @GetMapping("/delete/{relativeId}")
    public String deleteRelative(@PathVariable Long relativeId) {
        try {
            PatientProfileResponse patient = getLoggedInPatientProfile();
            RelativeResponse relative = relativeService.getRelativeById(relativeId);

            // Access check: Ensure the relative belongs to the logged-in patient
            if (relative.getPatientId().equals(patient.getId())) {
                relativeService.deleteRelative(relativeId);
            }
        } catch (Exception e) {
            // Fail silently or handle
        }
        return "redirect:/patient/relatives";
    }

    @GetMapping("/toggle/{relativeId}")
    public String toggleNotify(@PathVariable Long relativeId, @RequestParam("enabled") boolean enabled) {
        try {
            PatientProfileResponse patient = getLoggedInPatientProfile();
            RelativeResponse relative = relativeService.getRelativeById(relativeId);

            // Access check: Ensure the relative belongs to the logged-in patient
            if (relative.getPatientId().equals(patient.getId())) {
                relativeService.toggleNotification(relativeId, enabled);
            }
        } catch (Exception e) {
            // Fail silently or handle
        }
        return "redirect:/patient/relatives";
    }
}
