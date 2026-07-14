package fpt.swp391.GlucoTrackAlert.service.impl;

import fpt.swp391.GlucoTrackAlert.dto.AssignmentRequest;
import fpt.swp391.GlucoTrackAlert.enums.WorkShift;
import fpt.swp391.GlucoTrackAlert.doctor.Doctor;
import fpt.swp391.GlucoTrackAlert.model.DoctorPatientAssignment;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import java.util.Map;
import java.util.HashMap;
import fpt.swp391.GlucoTrackAlert.repository.DoctorPatientAssignmentRepository;
import fpt.swp391.GlucoTrackAlert.doctor.DoctorRepository;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.service.NotificationService;
import fpt.swp391.GlucoTrackAlert.service.register.EmailService;
import jakarta.annotation.PreDestroy;

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
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

    private static final LocalTime WORK_START = WorkShift.START;
    private static final LocalTime WORK_END = WorkShift.END;
    private static final ScheduledExecutorService scheduler
      = Executors.newSingleThreadScheduledExecutor();

    public static final int MAX_PATIENTS_PER_DOCTOR = 5;

    @PreDestroy
    public void shutdownScheduler() {
        scheduler.shutdown();
    }

    public DoctorPatientAssignment assignDoctor(AssignmentRequest request) {
        DoctorPatientAssignment assignment = new DoctorPatientAssignment();
        if (request.getDoctorId() != null) {
            Doctor doctor = doctorRepository.findById(request.getDoctorId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy bác sĩ ID " + request.getDoctorId()));
            if (!"active".equals(doctor.getStatus())) {
                throw new RuntimeException("Bác sĩ này chưa được kích hoạt, không thể phân công.");
            }
            assignment.setDoctor(doctor);
        }
        if (request.getPatientId() != null) {
            Patient patient = patientRepository.findById(request.getPatientId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy bệnh nhân ID " + request.getPatientId()));
            assignment.setPatient(patient);
        }
        assignment.setNote(request.getNote());
        return assignDoctor(assignment);
    }

    public DoctorPatientAssignment assignDoctor(DoctorPatientAssignment assignment) {
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

        if (assignment.getPatient() != null
                && assignmentRepository.existsByPatientIdAndStatus(
                        assignment.getPatient().getId(), "active")) {
            throw new RuntimeException("Trùng bệnh nhân: Bệnh nhân ID " + assignment.getPatient().getId() + " đã được phân công cho một bác sĩ khác đang hoạt động. Vui lòng hủy phân công cũ trước.");
        }

        // Bệnh nhân đã tự đề xuất trước (pending) -> admin không được "vượt mặt"
        // tạo phân công trực tiếp khác. Phải xử lý xong (duyệt/từ chối) đề xuất đó trước.
        if (assignment.getPatient() != null
                && assignmentRepository.existsByPatientIdAndStatus(
                        assignment.getPatient().getId(), "pending")) {
            throw new RuntimeException("Bệnh nhân ID " + assignment.getPatient().getId()
                    + " đang có một đề xuất chờ duyệt. Vui lòng duyệt hoặc từ chối đề xuất đó trước khi phân công trực tiếp.");
        }

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
                sendAssignmentNotification(saved);
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

        try {
            if (inWorkHours) {
                emailService.sendSimpleMessage(toEmail, subject, body);
            } else {
                LocalDateTime next8am = LocalDate.now().plusDays(1).atTime(WORK_START);
                long delaySec = java.time.Duration.between(LocalDateTime.now(), next8am).getSeconds();
                final String dest = toEmail;
                scheduler.schedule(() -> {
                    try {
                        emailService.sendSimpleMessage(dest, subject, body);
                    } catch (Exception ex) {
                        System.err.println("Gửi email thông báo phân công (hẹn giờ) thất bại: " + ex.getMessage());
                    }
                }, delaySec, TimeUnit.SECONDS);
            }
        } catch (Exception ex) {
            // Không để lỗi gửi email (vd. sai/hết hạn SMTP app-password) làm fail
            // toàn bộ request thêm phân công — phân công đã lưu DB thành công rồi.
            System.err.println("Gửi email thông báo phân công thất bại: " + ex.getMessage());
        }
    }

    // Gửi thông báo in-app cho bệnh nhân khi phân công của họ bị hủy/từ chối
    private void notifyPatientCancelled(DoctorPatientAssignment a, String title, String reason) {
        if (a.getPatient() == null || a.getPatient().getUser() == null) {
            return;
        }
        Long userId = a.getPatient().getUser().getId();
        String doctorName = a.getDoctor() != null ? a.getDoctor().getFullName() : "bác sĩ";
        String message = "Đề xuất/phân công với " + doctorName
                + (reason != null && !reason.isBlank() ? " đã bị hủy. Lý do: " + reason : " đã bị hủy.");
        notificationService.createNotification(userId, title, message, "ASSIGNMENT_CANCELLED");
    }

    public List<DoctorPatientAssignment> getAllAssignments() {
        return assignmentRepository.findAll();
    }

    public DoctorPatientAssignment updateAssignment(Long id, AssignmentRequest request) {
        DoctorPatientAssignment updatedAssignment = new DoctorPatientAssignment();
        if (request.getDoctorId() != null) {
            Doctor doctor = doctorRepository.findById(request.getDoctorId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy bác sĩ ID " + request.getDoctorId()));
            if (!"active".equals(doctor.getStatus())) {
                throw new RuntimeException("Bác sĩ này chưa được kích hoạt, không thể phân công.");
            }
            updatedAssignment.setDoctor(doctor);
        }
        if (request.getPatientId() != null) {
            Patient patient = patientRepository.findById(request.getPatientId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy bệnh nhân ID " + request.getPatientId()));
            updatedAssignment.setPatient(patient);
        }
        updatedAssignment.setNote(request.getNote());
        updatedAssignment.setStatus(request.getStatus());
        return updateAssignment(id, updatedAssignment);
    }

    public DoctorPatientAssignment updateAssignment(Long id, DoctorPatientAssignment updatedAssignment) {
        DoctorPatientAssignment assignment
                = assignmentRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Assignment not found"));

        Long targetPatientId = updatedAssignment.getPatient() != null
                ? updatedAssignment.getPatient().getId()
                : (assignment.getPatient() != null ? assignment.getPatient().getId() : null);

        boolean patientChanged = updatedAssignment.getPatient() != null
                && !updatedAssignment.getPatient().getId().equals(
                        assignment.getPatient() != null ? assignment.getPatient().getId() : null);

        boolean becomingActive = "active".equals(updatedAssignment.getStatus())
                && !"active".equals(assignment.getStatus());

        if ((patientChanged || becomingActive) && targetPatientId != null) {
            boolean conflict = assignmentRepository
                    .findByPatientIdAndStatus(targetPatientId, "active")
                    .stream()
                    .anyMatch(a -> !a.getId().equals(id));
            if (conflict) {
                throw new RuntimeException("Bệnh nhân này đang được phân công cho một bác sĩ khác đang hoạt động. Hãy hủy phân công đó trước.");
            }
        }


        if (updatedAssignment.getDoctor() != null) {
            assignment.setDoctor(updatedAssignment.getDoctor());
        }
        if (updatedAssignment.getPatient() != null) {
            assignment.setPatient(updatedAssignment.getPatient());
        }
        assignment.setNote(updatedAssignment.getNote());
        if (updatedAssignment.getStatus() != null) {
            assignment.setStatus(updatedAssignment.getStatus());
        }

        assignment.setDoctor(updatedAssignment.getDoctor());
        assignment.setPatient(updatedAssignment.getPatient());
        assignment.setNote(updatedAssignment.getNote());
        assignment.setStatus(updatedAssignment.getStatus());
        return assignmentRepository.save(assignment);
    }

    public void deleteAssignment(Long id) {
        DoctorPatientAssignment assignment
                = assignmentRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Assignment not found"));
        assignment.setStatus("inactive");
        assignmentRepository.save(assignment);
        notifyPatientCancelled(assignment, "Phân công bác sĩ đồng hành đã bị hủy", assignment.getCancelReason());
    }

    public List<Map<String, Object>> getPatientsByDoctor(Long doctorId) {
        return assignmentRepository
                .findByDoctorIdAndStatus(doctorId, "active")
                .stream()
                .map(a -> {
                    Patient p = a.getPatient();
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", p.getId());
                    map.put("fullName", p.getFullName());
                    map.put("phone", p.getPhone());
                    map.put("gender", p.getGender());
                    map.put("age", p.getAge());
                    map.put("address", p.getAddress());
                    map.put("status", p.getStatus());
                    map.put("email", p.getUser() != null ? p.getUser().getEmail() : null);
                    return map;
                })
                .toList();
    }

    public void hardDeleteAssignment(Long id) {
        DoctorPatientAssignment assignment
                = assignmentRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Assignment not found"));
        if (!"inactive".equals(assignment.getStatus())) {
            throw new RuntimeException("Chỉ có thể xóa vĩnh viễn các phân công đã hủy.");
        }
        assignmentRepository.delete(assignment);
    }

    // =====================================================================
    // NGHIỆP VỤ: Bệnh nhân đề xuất bác sĩ đồng hành -> Admin xét duyệt
    // =====================================================================

    /**
     * Bệnh nhân tạo đề xuất bác sĩ đồng hành (status = pending).
     * - Chặn ngay nếu bác sĩ đã full chỗ (danh sách chọn ở FE cũng đã lọc trước,
     *   đây là lớp chặn thứ 2 phòng trường hợp gọi thẳng API / dữ liệu FE cũ).
     *   Vẫn kiểm tra lại lần nữa lúc Admin duyệt vì có thể đã đầy thêm trong lúc chờ.
     * - Không chặn nếu bệnh nhân đang có bác sĩ active khác (cho phép đổi bác sĩ,
     *   bác sĩ cũ sẽ bị hủy kèm lý do khi đề xuất mới được duyệt).
     * - Chặn nếu bệnh nhân đang có 1 đề xuất pending khác chưa xử lý.
     */
    public DoctorPatientAssignment proposeAssignment(Long patientId, Long doctorId, String note) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ bệnh nhân"));

        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bác sĩ ID " + doctorId));
        if (!"active".equals(doctor.getStatus())) {
            throw new RuntimeException("Bác sĩ này hiện chưa thể nhận đề xuất.");
        }

        long activeCountAtPropose = assignmentRepository.countByDoctorIdAndStatus(doctorId, "active");
        if (activeCountAtPropose >= MAX_PATIENTS_PER_DOCTOR) {
            throw new RuntimeException("Bác sĩ này đã đủ số lượng bệnh nhân tối đa, vui lòng chọn bác sĩ khác.");
        }

        boolean hasPending = assignmentRepository
                .findFirstByPatientIdAndStatus(patientId, "pending")
                .isPresent();
        if (hasPending) {
            throw new RuntimeException("Bạn đang có một đề xuất chờ duyệt. Vui lòng hủy đề xuất đó trước khi tạo đề xuất mới.");
        }

        // Unique key (doctor_id, patient_id) trong DB chỉ cho phép 1 dòng / cặp bác sĩ-bệnh nhân.
        // Nếu bệnh nhân từng đề xuất (hoặc từng active) với đúng bác sĩ này rồi (VD: đã bị rejected/inactive),
        // phải cập nhật lại dòng cũ thay vì insert dòng mới để tránh Duplicate entry.
        java.util.Optional<DoctorPatientAssignment> existing
                = assignmentRepository.findByDoctorIdAndPatientId(doctorId, patientId);
        if (existing.isPresent()) {
            DoctorPatientAssignment old = existing.get();
            if ("active".equals(old.getStatus()) || "pending".equals(old.getStatus())) {
                throw new RuntimeException("Bạn đã có đề xuất/phân công với bác sĩ này rồi.");
            }
            old.setNote(note);
            old.setStatus("pending");
            old.setRejectReason(null);
            old.setCancelReason(null);
            old.setAssignedAt(LocalDateTime.now());
            return assignmentRepository.save(old);
        }

        DoctorPatientAssignment assignment = new DoctorPatientAssignment();
        assignment.setPatient(patient);
        assignment.setDoctor(doctor);
        assignment.setNote(note);
        assignment.setStatus("pending");
        assignment.setAssignedAt(LocalDateTime.now());
        return assignmentRepository.save(assignment);
    }

    /**
     * Bệnh nhân tự hủy đề xuất đang pending của chính mình (chưa được Admin xử lý).
     */
    public void cancelPendingAssignment(Long patientId, Long assignmentId) {
        DoctorPatientAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đề xuất"));

        if (assignment.getPatient() == null || !assignment.getPatient().getId().equals(patientId)) {
            throw new RuntimeException("Bạn không có quyền hủy đề xuất này.");
        }
        if (!"pending".equals(assignment.getStatus())) {
            throw new RuntimeException("Chỉ có thể hủy đề xuất đang chờ duyệt.");
        }
        assignment.setStatus("cancelled");
        assignmentRepository.save(assignment);
    }

    public List<DoctorPatientAssignment> getPendingAssignments() {
        return assignmentRepository.findByStatus("pending");
    }

    /**
     * Admin duyệt đề xuất: kiểm tra lại giới hạn 5 bệnh nhân/bác sĩ ngay tại thời điểm
     * duyệt (vì có thể đã đầy kể từ lúc bệnh nhân đề xuất). Nếu bệnh nhân đang có
     * một phân công active khác, tự động hủy phân công cũ kèm lý do.
     */
    public DoctorPatientAssignment approveAssignment(Long assignmentId) {
        DoctorPatientAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đề xuất"));
        if (!"pending".equals(assignment.getStatus())) {
            throw new RuntimeException("Đề xuất này đã được xử lý trước đó.");
        }

        Long doctorId = assignment.getDoctor().getId();
        long activeCount = assignmentRepository.countByDoctorIdAndStatus(doctorId, "active");
        if (activeCount >= MAX_PATIENTS_PER_DOCTOR) {
            throw new RuntimeException(
                    "Bác sĩ đã đạt giới hạn " + MAX_PATIENTS_PER_DOCTOR
                    + " bệnh nhân tại thời điểm duyệt. Không thể duyệt đề xuất này.");
        }

        // Nếu bệnh nhân đang có bác sĩ active khác -> hủy phân công cũ
        assignmentRepository.findFirstByPatientIdAndStatus(assignment.getPatient().getId(), "active")
                .ifPresent(old -> {
                    old.setStatus("inactive");
                    old.setCancelReason("Bệnh nhân đã được duyệt chuyển sang bác sĩ khác");
                    assignmentRepository.save(old);
                    notifyPatientCancelled(old, "Bác sĩ đồng hành đã được chuyển", old.getCancelReason());
                });

        assignment.setStatus("active");
        assignment.setAssignedAt(LocalDateTime.now());
        DoctorPatientAssignment saved = assignmentRepository.save(assignment);
        sendAssignmentNotification(saved);
        return saved;
    }

    /**
     * Admin từ chối đề xuất, bắt buộc có lý do để bệnh nhân biết vì sao bị từ chối.
     * Bệnh nhân vẫn được phép đề xuất lại sau đó (tạo bản ghi pending mới).
     */
    public DoctorPatientAssignment rejectAssignment(Long assignmentId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new RuntimeException("Vui lòng nhập lý do từ chối.");
        }
        DoctorPatientAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đề xuất"));
        if (!"pending".equals(assignment.getStatus())) {
            throw new RuntimeException("Đề xuất này đã được xử lý trước đó.");
        }
        assignment.setStatus("rejected");
        assignment.setRejectReason(reason);
        DoctorPatientAssignment saved = assignmentRepository.save(assignment);
        notifyPatientCancelled(saved, "Đề xuất bác sĩ đồng hành bị từ chối", reason);
        return saved;
    }
}