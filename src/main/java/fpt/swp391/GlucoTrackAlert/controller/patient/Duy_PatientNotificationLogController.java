package fpt.swp391.GlucoTrackAlert.controller.patient;

import fpt.swp391.GlucoTrackAlert.model.notification.NotificationLog;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.notification.Duy_NotificationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Trang bệnh nhân tự xem lịch sử cảnh báo đã được gửi tới người thân của mình
 * (Nghiệp vụ 2 - notification_logs), chỉ giới hạn trong phạm vi dữ liệu của
 * chính bệnh nhân đang đăng nhập.
 */
@Controller
@RequestMapping("/patient/notification-history")
@RequiredArgsConstructor
public class Duy_PatientNotificationLogController {

    private final Duy_NotificationLogService notificationLogService;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    @GetMapping
    public String myNotificationHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Model model) {

        Long patientId = getCurrentPatientId();
        int size = 10;

        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);

        if (patientId == null) {
            model.addAttribute("logs", Page.empty());
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("noPatientFound", true);
            return "patient/notification-history";
        }

        Page<NotificationLog> logs = notificationLogService.findLogsForPatient(patientId, fromDate, toDate, page, size);

        model.addAttribute("logs", logs);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", logs.getTotalPages());
        model.addAttribute("noPatientFound", false);

        return "patient/notification-history";
    }

    /**
     * Lấy patientId của người đang đăng nhập dựa trên email trong token JWT,
     * theo đúng cách DailyHealthLogController đang dùng để tránh nhầm lẫn giữa
     * userId và patientId.
     */
    private Long getCurrentPatientId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return null;
        }
        Optional<Patient> patientOpt = patientRepository.findByUserId(user.getId());
        return patientOpt.map(Patient::getId).orElse(null);
    }
}
