package fpt.swp391.GlucoTrackAlert.controller.patient;

import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileRequest;
import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileResponse;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.patient.PatientService;
import fpt.swp391.GlucoTrackAlert.model.patient.ProfileChangeRequest;
import fpt.swp391.GlucoTrackAlert.service.patient.ProfileChangeRequestService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import fpt.swp391.GlucoTrackAlert.service.CloudinaryService;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.ArrayList;

@Controller
@RequestMapping("/patient")
public class PatientWebController {

    private final PatientService patientService;
    private final UserRepository userRepository;
    private final ProfileChangeRequestService requestService;
    private final CloudinaryService cloudinaryService;

    @Autowired
    public PatientWebController(PatientService patientService, UserRepository userRepository, ProfileChangeRequestService requestService, CloudinaryService cloudinaryService) {
        this.patientService = patientService;
        this.userRepository = userRepository;
        this.requestService = requestService;
        this.cloudinaryService = cloudinaryService;
    }

    private User getLoggedInUser() {
        // Dùng getName() thay vì cast getPrincipal() sang String
        // vì getPrincipal() trả về UserDetails object, không phải String
        // → cast thẳng sẽ throw ClassCastException khi chạy thật
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
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

            boolean pendingHypertension = requestService.hasPendingRequest(profile.getId(), "hypertension");
            boolean pendingHeartDisease = requestService.hasPendingRequest(profile.getId(), "heartDisease");
            model.addAttribute("pendingHypertension", pendingHypertension);
            model.addAttribute("pendingHeartDisease", pendingHeartDisease);

            return "patient/profile";
        } catch (Exception e) {
            return "redirect:/patient/profile/edit";
        }
    }

    @GetMapping("/profile/requests")
    public String viewProfileRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            Model model) {
        User loggedInUser = getLoggedInUser();
        Long userId = loggedInUser.getId();
        try {
            PatientProfileResponse profile = patientService.getProfileByUserId(userId);
            Page<ProfileChangeRequest> requestsPage = requestService.getRequestsByPatientPaged(profile.getId(), page, size);

            model.addAttribute("requestsPage", requestsPage);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", requestsPage.getTotalPages());
            model.addAttribute("totalItems", requestsPage.getTotalElements());
            model.addAttribute("pageSize", size);

            return "patient/requests";
        } catch (Exception e) {
            return "redirect:/patient/profile";
        }
    }

    @GetMapping("/profile/edit")
    public String showEditForm(Model model) {
        User loggedInUser = getLoggedInUser();
        Long userId = loggedInUser.getId();
        boolean exists = patientService.existsByUserId(userId);
        if (exists) {
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
                        .identityCardImage(profile.getIdentityCardImage())
                        .identityCardStatus(profile.getIdentityCardStatus())
                        .insuranceNumber(profile.getInsuranceNumber())
                        .insuranceNumberImage(profile.getInsuranceNumberImage())
                        .insuranceCardStatus(profile.getInsuranceCardStatus())
                        .avatar(profile.getAvatar())
                        .isPregnant(profile.getIsPregnant())
                        .hypertension(profile.getHypertension())
                        .heartDisease(profile.getHeartDisease())
                        .everMarried(profile.getEverMarried())
                        .workType(profile.getWorkType())
                        .residenceType(profile.getResidenceType())
                        .smokingStatus(profile.getSmokingStatus())
                        // GIỮ LẠI CHOLESTEROL ở Profile DTO
                        .cholesterol(profile.getCholesterol())
                        // Các trường lối sống sinh hoạt
                        .smoke(profile.getSmoke())
                        .alco(profile.getAlco())
                        .active(profile.getActive())
                        .build();
                model.addAttribute("profileForm", request);
            } catch (Exception e) {
                PatientProfileRequest request = PatientProfileRequest.builder()
                        .userId(userId)
                        .build();
                model.addAttribute("profileForm", request);
                model.addAttribute("errorMessage", "Lỗi tải thông tin chi tiết: " + e.getMessage());
            }
            model.addAttribute("isNew", false);
        } else {
            PatientProfileRequest request = PatientProfileRequest.builder()
                    .userId(userId)
                    .fullName(loggedInUser.getFullName())
                    .phone(loggedInUser.getPhone())
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
                              @RequestParam(name = "identityCardFiles", required = false) List<MultipartFile> identityCardFiles,
                              @RequestParam(name = "insuranceNumberFiles", required = false) List<MultipartFile> insuranceNumberFiles,
                              @RequestParam(name = "avatarFile", required = false) MultipartFile avatarFile,
                              @RequestParam(name = "isNew", defaultValue = "false") boolean isNewParam,
                              Model model) {
        User loggedInUser = getLoggedInUser();
        Long userId = loggedInUser.getId();
        request.setUserId(userId);

        if (request.getSmoke() == null) request.setSmoke(0);
        if (request.getAlco() == null) request.setAlco(0);
        if (request.getActive() == null) request.setActive(0);

        boolean exists = patientService.existsByUserId(userId);
        boolean isNew = !exists;

        if (exists) {
            try {
                PatientProfileResponse currentProfile = patientService.getProfileByUserId(userId);
                if (Boolean.TRUE.equals(currentProfile.getHypertension())) {
                    request.setHypertension(true);
                }
                if (Boolean.TRUE.equals(currentProfile.getHeartDisease())) {
                    request.setHeartDisease(true);
                }
                // Retain existing image URLs if no new files uploaded
                if ((identityCardFiles == null || identityCardFiles.isEmpty() || identityCardFiles.get(0).isEmpty()) && request.getIdentityCardImage() == null) {
                    request.setIdentityCardImage(currentProfile.getIdentityCardImage());
                    request.setIdentityCardStatus(currentProfile.getIdentityCardStatus());
                }
                if ((insuranceNumberFiles == null || insuranceNumberFiles.isEmpty() || insuranceNumberFiles.get(0).isEmpty()) && request.getInsuranceNumberImage() == null) {
                    request.setInsuranceNumberImage(currentProfile.getInsuranceNumberImage());
                    request.setInsuranceCardStatus(currentProfile.getInsuranceCardStatus());
                }
                if ((avatarFile == null || avatarFile.isEmpty()) && request.getAvatar() == null) {
                    request.setAvatar(currentProfile.getAvatar());
                }
            } catch (Exception ignored) {}
        }

        // Upload identity card images if provided
        if (identityCardFiles != null && !identityCardFiles.isEmpty() && !identityCardFiles.get(0).isEmpty()) {
            try {
                List<String> urls = new ArrayList<>();
                for (MultipartFile file : identityCardFiles) {
                    if (file != null && !file.isEmpty()) {
                        urls.add(cloudinaryService.uploadFile(file, "patient_identity_cards"));
                    }
                }
                if (!urls.isEmpty()) {
                    request.setIdentityCardImage(String.join(",", urls));
                    request.setIdentityCardStatus("UNVERIFIED");
                }
            } catch (Exception e) {
                System.err.println("⚠️ Lỗi upload ảnh CCCD: " + e.getMessage());
            }
        }

        // Upload health insurance images if provided
        if (insuranceNumberFiles != null && !insuranceNumberFiles.isEmpty() && !insuranceNumberFiles.get(0).isEmpty()) {
            try {
                List<String> urls = new ArrayList<>();
                for (MultipartFile file : insuranceNumberFiles) {
                    if (file != null && !file.isEmpty()) {
                        urls.add(cloudinaryService.uploadFile(file, "patient_insurance_cards"));
                    }
                }
                if (!urls.isEmpty()) {
                    request.setInsuranceNumberImage(String.join(",", urls));
                    request.setInsuranceCardStatus("UNVERIFIED");
                }
            } catch (Exception e) {
                System.err.println("⚠️ Lỗi upload ảnh BHYT: " + e.getMessage());
            }
        }

        // Upload avatar image if provided
        if (avatarFile != null && !avatarFile.isEmpty()) {
            try {
                String avatarUrl = cloudinaryService.uploadFile(avatarFile, "patient_avatars");
                request.setAvatar(avatarUrl);
            } catch (Exception e) {
                System.err.println("⚠️ Lỗi upload ảnh đại diện: " + e.getMessage());
            }
        }

        if (result.hasErrors()) {
            model.addAttribute("isNew", isNew);
            model.addAttribute("userId", userId);
            return "patient/edit";
        }

        try {
            if (isNew) {
                patientService.createProfile(request);
            } else {
                patientService.updateProfile(userId, request);
            }
            return "redirect:/patient/profile";
        } catch (Exception e) {
            model.addAttribute("isNew", isNew);
            model.addAttribute("userId", userId);
            model.addAttribute("errorMessage", e.getMessage());
            return "patient/edit";
        }
    }
}