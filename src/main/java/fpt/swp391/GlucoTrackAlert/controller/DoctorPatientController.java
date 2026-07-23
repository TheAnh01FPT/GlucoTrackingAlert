package fpt.swp391.GlucoTrackAlert.controller;

import fpt.swp391.GlucoTrackAlert.doctor.Doctor;
import fpt.swp391.GlucoTrackAlert.doctor.DoctorRepository;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.impl.DoctorPatientAssignmentService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doctor")
@RequiredArgsConstructor
public class DoctorPatientController {

    private final DoctorPatientAssignmentService assignmentService;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;

    @GetMapping("/{doctorId}/patients")
    public ResponseEntity<List<Map<String, Object>>> getPatientsByDoctor(
            @PathVariable Long doctorId
    ) {
        // Chặn IDOR: bác sĩ chỉ được xem danh sách bệnh nhân của chính mình,
        // trừ khi người gọi là ADMIN.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            User currentUser = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new RuntimeException("Không xác định được người dùng hiện tại"));
            Doctor currentDoctor = doctorRepository.findByUserId(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("Tài khoản này chưa có hồ sơ bác sĩ"));
            if (!currentDoctor.getId().equals(doctorId)) {
                throw new RuntimeException("Bạn không có quyền xem danh sách bệnh nhân của bác sĩ khác");
            }
        }

        return ResponseEntity.ok(assignmentService.getPatientsByDoctor(doctorId));
    }
}