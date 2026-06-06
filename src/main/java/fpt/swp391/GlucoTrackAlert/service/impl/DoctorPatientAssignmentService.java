package fpt.swp391.GlucoTrackAlert.service.impl;

import fpt.swp391.GlucoTrackAlert.enums.WorkShift;
import fpt.swp391.GlucoTrackAlert.model.DoctorPatientAssignment;
import fpt.swp391.GlucoTrackAlert.repository.DoctorPatientAssignmentRepository;
import fpt.swp391.GlucoTrackAlert.service.EmailService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DoctorPatientAssignmentService {

    private final DoctorPatientAssignmentRepository assignmentRepository;
    private final EmailService emailService;

    // Giờ làm việc lấy từ WorkShift – single source of truth cho toàn hệ thống
    private static final LocalTime WORK_START = WorkShift.START;
    private static final LocalTime WORK_END = WorkShift.END;
    private static final ScheduledExecutorService scheduler
            = Executors.newSingleThreadScheduledExecutor();

    /**
     * Số bệnh nhân active tối đa mà 1 bác sĩ được phép chăm sóc
     */
    private static final int MAX_PATIENTS_PER_DOCTOR = 5;

    public DoctorPatientAssignment assignDoctor(DoctorPatientAssignment assignment) {
        // Kiểm tra giới hạn 5 bệnh nhân/bác sĩ
        if (assignment.getDoctor() != null) {
            long activeCount = assignmentRepository.countByDoctorIdAndStatus(
                    assignment.getDoctor().getId(), "active");
            if (activeCount >= MAX_PATIENTS_PER_DOCTOR) {
                throw new RuntimeException(
                        "Bác sĩ ID " + assignment.getDoctor().getId()
                        + " đã đạt giới hạn " + MAX_PATIENTS_PER_DOCTOR
                        + " bệnh nhân. Vui lòng hủy phân công một bệnh nhân trước khi thêm mới.");
            }
        }

        // Kiểm tra bệnh nhân đã có bác sĩ active chưa
        if (assignment.getPatient() != null
                && assignmentRepository.existsByPatientIdAndStatus(
                        assignment.getPatient().getId(), "active")) {
            throw new RuntimeException("Trùng bệnh nhân: Bệnh nhân ID " + assignment.getPatient().getId() + " đã được phân công cho một bác sĩ khác đang hoạt động. Vui lòng hủy phân công cũ trước.");
        }

        // Nếu đã từng có record (inactive) cho cặp bác sĩ - bệnh nhân này
        // → reactivate thay vì INSERT mới (tránh duplicate key trên unique index)
        if (assignment.getDoctor() != null && assignment.getPatient() != null) {
            java.util.Optional<DoctorPatientAssignment> existing
                    = assignmentRepository.findByDoctorIdAndPatientId(
                            assignment.getDoctor().getId(), assignment.getPatient().getId());
            if (existing.isPresent()) {
                DoctorPatientAssignment old = existing.get();
                old.setStatus("active");
                old.setAssignedAt(LocalDateTime.now());
                old.setNote(assignment.getNote());
                DoctorPatientAssignment saved = assignmentRepository.save(old);
                sendAssignmentNotification(saved); // gửi email khi reactivate
                return saved;
            }
        }

        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setStatus("active");
        DoctorPatientAssignment saved = assignmentRepository.save(assignment);
        sendAssignmentNotification(saved);
        return saved;
    }

    private void sendAssignmentNotification(DoctorPatientAssignment a) {
        if (a.getDoctor() == null || a.getPatient() == null) {
            return;
        }
        String toEmail = a.getDoctor().getUser() != null ? a.getDoctor().getUser().getEmail() : null;
        if (toEmail == null) {
            return;
        }

        String subject = "[GlucoTrackAlert] Bạn có bệnh nhân mới được phân công";
        String body = "Xin chào Bác sĩ " + a.getDoctor().getFullName() + ",\n\n"
                + "Bệnh nhân mới được phân công cho bạn:\n"
                + "  Bệnh nhân : " + a.getPatient().getFullName() + "\n"
                + "  Thời điểm   : " + a.getAssignedAt() + "\n\n"
                + "Vui lòng đăng nhập GlucoTrackAlert để xem thông tin chi tiết.\n\n"
                + "Trân trọng,\nGlucoTrackAlert";

        LocalTime now = LocalTime.now();
        boolean inWorkHours = !now.isBefore(WORK_START) && now.isBefore(WORK_END);

        if (inWorkHours) {
            emailService.sendSimpleMessage(toEmail, subject, body);
        } else {
            // Delay đến 08:00 sáng hôm sau
            LocalDateTime next8am = LocalDate.now().plusDays(1).atTime(WORK_START);
            long delaySec = java.time.Duration.between(LocalDateTime.now(), next8am).getSeconds();
            final String dest = toEmail;
            scheduler.schedule(() -> emailService.sendSimpleMessage(dest, subject, body),
                    delaySec, TimeUnit.SECONDS);
        }
    }

    public List<DoctorPatientAssignment> getAllAssignments() {
        return assignmentRepository.findAll();
    }

    public DoctorPatientAssignment updateAssignment(Integer id, DoctorPatientAssignment updatedAssignment) {
        DoctorPatientAssignment assignment
                = assignmentRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Assignment not found"));

        Integer targetPatientId = updatedAssignment.getPatient() != null
                ? updatedAssignment.getPatient().getId()
                : (assignment.getPatient() != null ? assignment.getPatient().getId() : null);

        boolean patientChanged = updatedAssignment.getPatient() != null
                && !updatedAssignment.getPatient().getId().equals(
                        assignment.getPatient() != null ? assignment.getPatient().getId() : null);

        boolean becomingActive = "active".equals(updatedAssignment.getStatus())
                && !"active".equals(assignment.getStatus());

        // Kiểm tra trùng nếu: đổi sang bệnh nhân khác, HOẶC reactivate lại record đã hủy
        if ((patientChanged || becomingActive) && targetPatientId != null) {
            // Tìm record active khác (không phải chính record này)
            boolean conflict = assignmentRepository
                    .findByPatientIdAndStatus(targetPatientId, "active")
                    .stream()
                    .anyMatch(a -> !a.getId().equals(id));
            if (conflict) {
                throw new RuntimeException("Bệnh nhân này đang được phân công cho một bác sĩ khác đang hoạt động. Hãy hủy phân công đó trước.");
            }
        }

        assignment.setDoctor(updatedAssignment.getDoctor());
        assignment.setPatient(updatedAssignment.getPatient());
        assignment.setNote(updatedAssignment.getNote());
        assignment.setStatus(updatedAssignment.getStatus());
        return assignmentRepository.save(assignment);
    }

    public void deleteAssignment(Integer id) {
        DoctorPatientAssignment assignment
                = assignmentRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Assignment not found"));
        assignment.setStatus("inactive");
        assignmentRepository.save(assignment);
    }

    public void hardDeleteAssignment(Integer id) {
        DoctorPatientAssignment assignment
                = assignmentRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Assignment not found"));
        if (!"inactive".equals(assignment.getStatus())) {
            throw new RuntimeException("Chỉ có thể xóa vĩnh viễn các phân công đã hủy.");
        }
        assignmentRepository.delete(assignment);
    }
}
