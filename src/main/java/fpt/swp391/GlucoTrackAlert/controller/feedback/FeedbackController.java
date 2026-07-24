package fpt.swp391.GlucoTrackAlert.controller.feedback;

import fpt.swp391.GlucoTrackAlert.model.Feedback;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.doctor.DoctorRecommendationRepository;
import fpt.swp391.GlucoTrackAlert.model.doctor.Doctor;
import fpt.swp391.GlucoTrackAlert.repository.doctor.DoctorRepository;
import fpt.swp391.GlucoTrackAlert.repository.FeedbackRepository;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
public class FeedbackController {

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DoctorRecommendationRepository recommendationRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    private static final String[] BAD_WORDS = {
            "địt", "đụ", "cứt", "ngu", "lồn", "cặc", "chó", "đĩ", "đm", "đkm", "vcl"
    };

    private String filterBadWords(String input) {
        if (input == null)
            return null;
        String filtered = input;
        for (String word : BAD_WORDS) {
            filtered = filtered.replaceAll("(?i)" + word, "***");
        }
        return filtered;
    }

    // --- PATIENT: VIEW FEEDBACKS ---
    @GetMapping("/patient/feedbacks")
    public String patientFeedbacks(Model model, Authentication authentication, RedirectAttributes redirectAttributes) {
        String email = (String) authentication.getPrincipal();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null)
            return "redirect:/login";

        Patient patient = patientRepository.findByUserId(user.getId()).orElse(null);
        if (patient == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Bạn cần tạo hồ sơ y tế trước khi có thể gửi đánh giá.");
            return "redirect:/patient/profile";
        }

        List<Feedback> feedbacks = feedbackRepository.findByPatientOrderByCreatedAtDesc(patient);
        model.addAttribute("feedbacks", feedbacks);
        return "feedback/patient-feedback";
    }

    // --- PATIENT: SUBMIT FEEDBACK ---
    @PostMapping("/patient/feedbacks/submit")
    public String submitFeedback(@RequestParam("content") String content,
                                 @RequestParam("rating") Integer rating,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        String email = (String) authentication.getPrincipal();
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null)
            return "redirect:/login";

        Patient patient = patientRepository.findByUserId(user.getId()).orElse(null);
        if (patient == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Bạn cần tạo hồ sơ y tế trước khi có thể gửi đánh giá.");
            return "redirect:/patient/profile";
        }

        String filteredContent = filterBadWords(content);

        Feedback feedback = new Feedback();
        feedback.setPatient(patient);
        feedback.setRating(rating);
        feedback.setContent(filteredContent);
        feedbackRepository.save(feedback);

        redirectAttributes.addFlashAttribute("successMessage", "Đã gửi đánh giá thành công.");
        return "redirect:/patient/feedbacks";
    }


    // --- PATIENT: UPDATE FEEDBACK ---
    @PostMapping("/patient/feedbacks/update")
    public String updateFeedback(@RequestParam("feedbackId") Long feedbackId,
                                 @RequestParam("content") String content,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        String email = (String) authentication.getPrincipal();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return "redirect:/login";

        Patient patient = patientRepository.findByUserId(user.getId()).orElse(null);
        if (patient == null) return "redirect:/patient/profile";

        Feedback feedback = feedbackRepository.findById(feedbackId).orElse(null);
        if (feedback != null && feedback.getPatient().getId().equals(patient.getId())) {
            feedback.setContent(filterBadWords(content));
            feedbackRepository.save(feedback);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật đánh giá thành công.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể cập nhật đánh giá này.");
        }
        return "redirect:/patient/feedbacks";
    }

    // --- PATIENT: DELETE FEEDBACK ---
    @PostMapping("/patient/feedbacks/delete")
    public String deleteFeedback(@RequestParam("feedbackId") Long feedbackId,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        String email = (String) authentication.getPrincipal();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return "redirect:/login";

        Patient patient = patientRepository.findByUserId(user.getId()).orElse(null);
        if (patient == null) return "redirect:/patient/profile";

        Feedback feedback = feedbackRepository.findById(feedbackId).orElse(null);
        if (feedback != null && feedback.getPatient().getId().equals(patient.getId())) {
            feedbackRepository.delete(feedback);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa đánh giá thành công.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa đánh giá này.");
        }
        return "redirect:/patient/feedbacks";
    }

    // --- ADMIN: VIEW FEEDBACKS ---
    @GetMapping("/admin/feedbacks")
    public String adminFeedbacks(
            @RequestParam(value = "rating", required = false) Integer rating,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Model model) {
        List<Feedback> allFeedbacks = feedbackRepository.findAllByOrderByCreatedAtDesc();
        
        long totalReviews = allFeedbacks.size();
        double averageRating = 0.0;
        long[] starCounts = new long[6]; // index 1-5 will be used
        
        if (totalReviews > 0) {
            double totalStars = 0;
            for (Feedback fb : allFeedbacks) {
                if (fb.getRating() != null) {
                    totalStars += fb.getRating();
                    if (fb.getRating() >= 1 && fb.getRating() <= 5) {
                        starCounts[fb.getRating()]++;
                    }
                }
            }
            averageRating = totalStars / totalReviews;
        }

        List<Feedback> feedbacksToDisplay = allFeedbacks;
        if (rating != null) {
            feedbacksToDisplay = allFeedbacks.stream()
                .filter(fb -> rating.equals(fb.getRating()))
                .collect(java.util.stream.Collectors.toList());
        }

        // Pagination Logic
        int totalItems = feedbacksToDisplay.size();
        int totalPages = (int) Math.ceil((double) totalItems / size);
        if (totalPages == 0) totalPages = 1;
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int startIndex = (page - 1) * size;
        int endIndex = Math.min(startIndex + size, totalItems);
        List<Feedback> pagedFeedbacks = feedbacksToDisplay.subList(startIndex, endIndex);

        model.addAttribute("feedbacks", pagedFeedbacks);
        model.addAttribute("currentFilter", rating);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);

        model.addAttribute("totalReviews", totalReviews);
        model.addAttribute("averageRating", String.format("%.1f", averageRating));
        model.addAttribute("star5", starCounts[5]);
        model.addAttribute("star4", starCounts[4]);
        model.addAttribute("star3", starCounts[3]);
        model.addAttribute("star2", starCounts[2]);
        model.addAttribute("star1", starCounts[1]);

        return "feedback/admin-feedback";
    }

}
