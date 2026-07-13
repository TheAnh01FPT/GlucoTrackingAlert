package fpt.swp391.GlucoTrackAlert.service.medication;

import fpt.swp391.GlucoTrackAlert.dto.medication.*;
import fpt.swp391.GlucoTrackAlert.dto.reminder.Duy_ReminderRequest;
import fpt.swp391.GlucoTrackAlert.doctor.Doctor;
import fpt.swp391.GlucoTrackAlert.model.medication.*;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.doctor.DoctorRepository;
import fpt.swp391.GlucoTrackAlert.repository.medication.*;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.service.Duy_ReminderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MedicationServiceImpl implements MedicationService {

    @Autowired private PrescriptionRepository prescriptionRepo;
    @Autowired private PrescriptionItemRepository itemRepo;
    @Autowired private MedicationLogRepository logRepo;
    @Autowired private PatientRepository patientRepo;
    @Autowired private DoctorRepository doctorRepo;
    @Autowired private Duy_ReminderService reminderService;

    @Override
    @Transactional
    public PrescriptionResponse createPrescription(PrescriptionRequest req) {
        Patient patient = patientRepo.findById(req.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bệnh nhân ID: " + req.getPatientId()));
        Doctor doctor = doctorRepo.findById(req.getDoctorId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bác sĩ ID: " + req.getDoctorId()));

        Prescription prescription = Prescription.builder()
                .patient(patient)
                .doctor(doctor)
                .prescribedDate(req.getPrescribedDate() != null ? req.getPrescribedDate() : LocalDate.now())
                .note(req.getNote())
                .status("ACTIVE")
                .build();
        prescription = prescriptionRepo.save(prescription);

        List<PrescriptionItem> savedItems = new ArrayList<>();
        if (req.getItems() != null) {
            for (var itemReq : req.getItems()) {
                LocalDate startDate = itemReq.getStartDate() != null ? itemReq.getStartDate() : LocalDate.now();
                // durationDays giờ là bắt buộc (đã validate @NotNull @Min(1) ở DTO),
                // nên endDate luôn được tính rõ ràng, không còn rơi vào case
                // endDate = startDate (chỉ sinh log 1 ngày) khi bác sĩ quên điền.
                LocalDate endDate = startDate.plusDays(itemReq.getDurationDays() - 1);

                PrescriptionItem item = PrescriptionItem.builder()
                        .prescription(prescription)
                        .medicineName(itemReq.getMedicineName())
                        .dosage(itemReq.getDosage())
                        .frequency(itemReq.getFrequency())
                        .timeOfDay(itemReq.getTimeOfDay())
                        .durationDays(itemReq.getDurationDays())
                        .instructions(itemReq.getInstructions())
                        .startDate(startDate)
                        .endDate(endDate)
                        .build();
                item = itemRepo.save(item);
                savedItems.add(item);

                generateLogs(item, patient, startDate, endDate);
                createMedicationReminder(item, patient.getId());
            }
        }
        return toResponse(prescription, savedItems);
    }

    private void generateLogs(PrescriptionItem item, Patient patient, LocalDate startDate, LocalDate endDate) {
        if (item.getTimeOfDay() == null || item.getTimeOfDay().isBlank()) return;
        String[] times = item.getTimeOfDay().split(",");
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            for (String timeStr : times) {
                try {
                    LocalTime localTime = LocalTime.parse(timeStr.trim(), DateTimeFormatter.ofPattern("HH:mm"));
                    MedicationLog log = MedicationLog.builder()
                            .prescriptionItem(item)
                            .patient(patient)
                            .scheduledTime(LocalDateTime.of(current, localTime))
                            .status("PENDING")
                            .build();
                    logRepo.save(log);
                } catch (Exception ignored) {}
            }
            current = current.plusDays(1);
        }
    }

    private void createMedicationReminder(PrescriptionItem item, Long patientId) {
        if (item.getTimeOfDay() == null || item.getTimeOfDay().isBlank()) return;
        String[] times = item.getTimeOfDay().split(",");
        LocalDate reminderDate = item.getStartDate() != null ? item.getStartDate() : LocalDate.now();
        // Tạo MỘT reminder cho MỖI giờ uống thuốc trong timeOfDay (vd "07:00,12:00,19:00"
        // sẽ tạo 3 reminder), giống cách generateLogs() đang sinh log cho từng giờ.
        for (String timeStr : times) {
            try {
                LocalTime localTime = LocalTime.parse(timeStr.trim(), DateTimeFormatter.ofPattern("HH:mm"));
                LocalDateTime reminderDateTime = LocalDateTime.of(reminderDate, localTime);
                if (reminderDateTime.isBefore(LocalDateTime.now())) {
                    reminderDateTime = reminderDateTime.plusDays(1);
                }
                Duy_ReminderRequest reminder = new Duy_ReminderRequest();
                reminder.setPatientId(patientId);
                reminder.setReminderType("MEDICATION");
                reminder.setTitle("Uống thuốc: " + item.getMedicineName());
                reminder.setMessage(
                    (item.getDosage() != null ? item.getDosage() : "") +
                    (item.getInstructions() != null ? " - " + item.getInstructions() : "")
                );
                reminder.setReminderTime(reminderDateTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
                reminder.setRepeatType("DAILY");
                // Gắn endDate của đợt thuốc để scheduler tự dừng lặp lại sau ngày này,
                // và gắn prescriptionItemId để có thể huỷ reminder khi đơn thuốc bị CANCELLED.
                reminder.setEndDate(item.getEndDate());
                reminder.setPrescriptionItemId(item.getId());
                reminderService.create(reminder);
            } catch (Exception e) {
                System.err.println("[createMedicationReminder] Lỗi tạo reminder cho " + item.getMedicineName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Override
    public List<PrescriptionResponse> getPrescriptionsByPatient(Long patientId) {
        return prescriptionRepo.findByPatientIdOrderByPrescribedDateDesc(patientId)
                .stream()
                .map(p -> toResponse(p, itemRepo.findByPrescriptionId(p.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public List<MedicationLogResponse> getDailyLogs(Long patientId, LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        return logRepo.findByPatientAndDateRange(patientId, target.atStartOfDay(), target.atTime(23, 59, 59))
                .stream().map(this::toLogResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MedicationLogResponse markTaken(Long logId) {
        MedicationLog log = logRepo.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy log ID: " + logId));
        log.setStatus("TAKEN");
        log.setTakenAt(LocalDateTime.now());
        return toLogResponse(logRepo.save(log));
    }

    @Override
    public Map<String, Object> getAdherenceStat(Long patientId) {
        long total = logRepo.countByPatientId(patientId);
        long taken = logRepo.countByPatientIdAndStatus(patientId, "TAKEN");
        long missed = logRepo.countByPatientIdAndStatus(patientId, "MISSED");
        double pct = total > 0 ? Math.round(taken * 100.0 / total * 10) / 10.0 : 0;
        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("total", total);
        stat.put("taken", taken);
        stat.put("missed", missed);
        stat.put("adherencePct", pct);
        return stat;
    }

    @Override
    @Transactional
    public void cancelPrescription(Long prescriptionId) {
        Prescription p = prescriptionRepo.findById(prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn ID: " + prescriptionId));
        p.setStatus("CANCELLED");
        prescriptionRepo.save(p);

        // Huỷ luôn các reminder MEDICATION được sinh ra từ các PrescriptionItem của đơn này,
        // tránh trường hợp bác sĩ huỷ đơn nhưng bệnh nhân vẫn tiếp tục nhận email nhắc uống thuốc.
        List<PrescriptionItem> items = itemRepo.findByPrescriptionId(prescriptionId);
        for (PrescriptionItem item : items) {
            reminderService.cancelByPrescriptionItemId(item.getId());
        }
    }

    private PrescriptionResponse toResponse(Prescription p, List<PrescriptionItem> items) {
        List<PrescriptionItemResponse> itemResponses = items.stream().map(i ->
                PrescriptionItemResponse.builder()
                        .id(i.getId()).medicineName(i.getMedicineName()).dosage(i.getDosage())
                        .frequency(i.getFrequency()).timeOfDay(i.getTimeOfDay())
                        .durationDays(i.getDurationDays()).instructions(i.getInstructions())
                        .startDate(i.getStartDate()).endDate(i.getEndDate()).build()
        ).collect(Collectors.toList());

        return PrescriptionResponse.builder()
                .id(p.getId()).patientId(p.getPatient().getId()).patientName(p.getPatient().getFullName())
                .doctorId(p.getDoctor().getId()).doctorName(p.getDoctor().getFullName())
                .prescribedDate(p.getPrescribedDate()).note(p.getNote()).status(p.getStatus())
                .items(itemResponses).build();
    }

    private MedicationLogResponse toLogResponse(MedicationLog log) {
        PrescriptionItem item = log.getPrescriptionItem();
        return MedicationLogResponse.builder()
                .id(log.getId()).prescriptionItemId(item.getId())
                .medicineName(item.getMedicineName()).dosage(item.getDosage())
                .instructions(item.getInstructions())
                .scheduledTime(log.getScheduledTime() != null
                        ? log.getScheduledTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null)
                .takenAt(log.getTakenAt() != null
                        ? log.getTakenAt().format(DateTimeFormatter.ofPattern("HH:mm dd/MM")) : null)
                .status(log.getStatus()).note(log.getNote()).build();
    }
}