package fpt.swp391.GlucoTrackAlert.controller.patient;

import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileRequest;
import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileResponse;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.patient.PatientService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/patient")
public class PatientWebController {

    private final PatientService patientService;
    private final UserRepository userRepository;

    @Autowired
    public PatientWebController(PatientService patientService, UserRepository userRepository) {
        this.patientService = patientService;
        this.userRepository = userRepository;
    }

    private User getLoggedInUser() {
        String email = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Tài khoản chưa đăng nhập hoặc không tồn tại"));
    }

    @GetMapping("/profile")
    public String viewProfile(Model model) {
        User loggedInUser = getLoggedInUser();
        Long userId = loggedInUser.getId();
        try {
            PatientProfileResponse profile = patientService.getProfileByUserId(userId);
            model.addAttribute("profile", profile);
            model.addAttribute("userId", userId);
            return "patient/profile";
        } catch (Exception e) {
            return "redirect:/patient/profile/edit";
        }
    }

    @GetMapping("/profile/edit")
    public String showEditForm(Model model) {
        User loggedInUser = getLoggedInUser();
        Long userId = loggedInUser.getId();
        try {
            PatientProfileResponse profile = patientService.getProfileByUserId(userId);
            PatientProfileRequest request = PatientProfileRequest.builder()
                    .userId(userId)
                    .fullName(profile.getFullName())
                    .dateOfBirth(profile.getDateOfBirth())
                    .gender(profile.getGender())
                    .phone(profile.getPhone())
                    .address(profile.getAddress())
                    .heightCm(profile.getHeightCm())
                    .weightKg(profile.getWeightKg())
                    .identityCard(profile.getIdentityCard())
                    .insuranceNumber(profile.getInsuranceNumber())
                    .isPregnant(profile.getIsPregnant())
                    // Map đồng bộ các chỉ số Cleveland chuẩn mới vào Form sửa
                    .cp(profile.getCp())
                    .trestbps(profile.getTrestbps())
                    .fbs(profile.getFbs())
                    .exang(profile.getExang())
                    .chol(profile.getChol())
                    .restecg(profile.getRestecg())
                    .thalach(profile.getThalach())
                    .oldpeak(profile.getOldpeak())
                    .slope(profile.getSlope())
                    .ca(profile.getCa())
                    .thal(profile.getThal())
                    .build();
            model.addAttribute("profileForm", request);
            model.addAttribute("isNew", false);
        } catch (Exception e) {
            PatientProfileRequest request = PatientProfileRequest.builder()
                    .userId(userId)
                    .build();
            model.addAttribute("profileForm", request);
            model.addAttribute("isNew", true);
        }
        model.addAttribute("userId", userId);
        return "patient/edit";
    }

    @PostMapping("/profile/save")
    public String saveProfile(@Valid @ModelAttribute("profileForm") PatientProfileRequest request,
                              BindingResult result,
                              @RequestParam("isNew") boolean isNew,
                              Model model) {
        User loggedInUser = getLoggedInUser();
        request.setUserId(loggedInUser.getId());

        if (result.hasErrors()) {
            model.addAttribute("isNew", isNew);
            model.addAttribute("userId", loggedInUser.getId());
            return "patient/edit";
        }

        try {
            if (isNew) {
                patientService.createProfile(request);
            } else {
                patientService.updateProfile(loggedInUser.getId(), request);
            }
            return "redirect:/patient/profile";
        } catch (Exception e) {
            model.addAttribute("isNew", isNew);
            model.addAttribute("userId", loggedInUser.getId());
            model.addAttribute("errorMessage", e.getMessage());
            return "patient/edit";
        }
    }
}