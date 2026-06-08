package fpt.swp391.GlucoTrackAlert.service.impl;

import fpt.swp391.GlucoTrackAlert.dto.healthlog.DailyHealthLogRequest;
import fpt.swp391.GlucoTrackAlert.dto.healthlog.DailyHealthLogResponse;
import fpt.swp391.GlucoTrackAlert.model.DailyHealthLog;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.repository.DailyHealthLogRepository;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.service.DailyHealthLogService;
import fpt.swp391.GlucoTrackAlert.util.BloodSugarThreshold;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyHealthLogServiceImpl implements DailyHealthLogService {

    private final DailyHealthLogRepository dailyHealthLogRepository;
    private final PatientRepository patientRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<DailyHealthLogResponse> getLogs(Long patientId, Pageable pageable) {
        return dailyHealthLogRepository.findByPatientIdOrderByLogDateDesc(patientId, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public DailyHealthLogResponse getLogById(Long id) {
        DailyHealthLog log = dailyHealthLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhật ký sức khỏe có mã số ID: " + id));
        return toResponse(log);
    }

    @Override
    @Transactional
    public DailyHealthLogResponse createLog(Long patientId, DailyHealthLogRequest request) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bệnh nhân có mã số ID: " + patientId));
        DailyHealthLog log = toEntity(request);
        log.setPatient(patient);
        DailyHealthLog savedLog = dailyHealthLogRepository.save(log);
        return toResponse(savedLog);
    }

    @Override
    @Transactional
    public DailyHealthLogResponse updateLog(Long id, DailyHealthLogRequest request) {
        DailyHealthLog log = dailyHealthLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhật ký sức khỏe có mã số ID: " + id));
        updateEntity(log, request);
        DailyHealthLog updatedLog = dailyHealthLogRepository.save(log);
        return toResponse(updatedLog);
    }

    @Override
    @Transactional
    public void deleteLog(Long id) {
        DailyHealthLog log = dailyHealthLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhật ký sức khỏe có mã số ID: " + id));
        dailyHealthLogRepository.delete(log);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyHealthLogResponse> getChartData(Long patientId, LocalDate from, LocalDate to) {
        return dailyHealthLogRepository.findByPatientIdAndLogDateBetweenOrderByLogDate(patientId, from, to)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // === Helper Methods ===

    private DailyHealthLogResponse toResponse(DailyHealthLog log) {
        if (log == null) {
            return null;
        }
        return DailyHealthLogResponse.builder()
                .id(log.getId())
                .patientId(log.getPatient() != null ? log.getPatient().getId() : null)
                .userId(log.getPatient() != null && log.getPatient().getUser() != null
                    ? log.getPatient().getUser().getId() : null)
                .patientName(log.getPatient() != null ? log.getPatient().getFullName() : null)
                .logDate(log.getLogDate())
                .bloodSugar(log.getBloodSugar())
                .systolic(log.getSystolic())
                .diastolic(log.getDiastolic())
                .sleepHours(log.getSleepHours())
                .waterMl(log.getWaterMl())
                .sugarConsumptionLevel(log.getSugarConsumptionLevel())
                .symptoms(log.getSymptoms())
                .note(log.getNote())
                .createdAt(log.getCreatedAt())
                .updatedAt(log.getUpdatedAt())
                .patientType(log.getPatient() != null ? log.getPatient().getPatientType() : null)
                .bloodSugarStatus(BloodSugarThreshold.evaluate(log.getBloodSugar(),
                        log.getPatient() != null ? log.getPatient().getPatientType() : null))
                .build();
    }

    private DailyHealthLog toEntity(DailyHealthLogRequest request) {
        if (request == null) {
            return null;
        }
        return DailyHealthLog.builder()
                .logDate(request.getLogDate())
                .bloodSugar(request.getBloodSugar())
                .systolic(request.getSystolic())
                .diastolic(request.getDiastolic())
                .sleepHours(request.getSleepHours())
                .waterMl(request.getWaterMl())
                .sugarConsumptionLevel(request.getSugarConsumptionLevel())
                .symptoms(request.getSymptoms())
                .note(request.getNote())
                .build();
    }

    private void updateEntity(DailyHealthLog entity, DailyHealthLogRequest request) {
        if (entity == null || request == null) {
            return;
        }
        entity.setLogDate(request.getLogDate());
        entity.setBloodSugar(request.getBloodSugar());
        entity.setSystolic(request.getSystolic());
        entity.setDiastolic(request.getDiastolic());
        entity.setSleepHours(request.getSleepHours());
        entity.setWaterMl(request.getWaterMl());
        entity.setSugarConsumptionLevel(request.getSugarConsumptionLevel());
        entity.setSymptoms(request.getSymptoms());
        entity.setNote(request.getNote());
    }
}
