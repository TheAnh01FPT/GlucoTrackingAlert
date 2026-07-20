package fpt.swp391.GlucoTrackAlert.controller.feedback;

import fpt.swp391.GlucoTrackAlert.model.Feedback;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.DoctorRecommendationRepository;
import fpt.swp391.GlucoTrackAlert.doctor.Doctor;
import fpt.swp391.GlucoTrackAlert.doctor.DoctorRepository;
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

        boolean hasReceivedAdvice = !recommendationRepository.findByPatientIdOrderByCreatedAtDesc(patient.getId())
                .isEmpty();
        List<Doctor> doctors = doctorRepository.findAll();
        model.addAttribute("doctors", doctors);
        model.addAttribute("canFeedback", hasReceivedAdvice);

        List<Feedback> feedbacks = feedbackRepository.findByPatientOrderByCreatedAtDesc(patient);
        model.addAttribute("feedbacks", feedbacks);

        return "feedback/patient-feedback";
    }

    // --- PATIENT: SUBMIT FEEDBACK ---
    @PostMapping("/patient/feedbacks/submit")
    public String submitFeedback(@RequestParam("content") String content,
                                 @RequestParam("doctorId") Long doctorId,
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

        if (recommendationRepository.findByPatientIdOrderByCreatedAtDesc(patient.getId()).isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Bạn chỉ được gửi đánh giá sau khi nhận lời khuyên từ bác sĩ.");
            return "redirect:/patient/feedbacks";
        }

        String filteredContent = filterBadWords(content);

        Doctor doctor = doctorRepository.findById(doctorId).orElse(null);
        if (doctor == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bác sĩ không tồn tại.");
            return "redirect:/patient/feedbacks";
        }

        Feedback feedback = new Feedback();
        feedback.setPatient(patient);
        feedback.setDoctor(doctor);
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
    public String adminFeedbacks(Model model) {
        List<Feedback> feedbacks = feedbackRepository.findAllByOrderByCreatedAtDesc();
        model.addAttribute("feedbacks", feedbacks);
        return "feedback/admin-feedback";
    }

}
