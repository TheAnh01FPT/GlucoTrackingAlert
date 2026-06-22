package fpt.swp391.GlucoTrackAlert.service.impl;

import fpt.swp391.GlucoTrackAlert.dto.AssignmentRequest;
import fpt.swp391.GlucoTrackAlert.enums.WorkShift;
import fpt.swp391.GlucoTrackAlert.model.Doctor;
import fpt.swp391.GlucoTrackAlert.model.DoctorPatientAssignment;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import java.util.Map;
import java.util.HashMap;
import fpt.swp391.GlucoTrackAlert.repository.DoctorPatientAssignmentRepository;
import fpt.swp391.GlucoTrackAlert.repository.DoctorRepository;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
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

    private static final LocalTime WORK_START = WorkShift.START;
    private static final LocalTime WORK_END = WorkShift.END;
    private static final ScheduledExecutorService scheduler
      = Executors.newSingleThreadScheduledExecutor();

    private static final int MAX_PATIENTS_PER_DOCTOR = 5;

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

        if (inWorkHours) {
            emailService.sendSimpleMessage(toEmail, subject, body);
        } else {
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
}