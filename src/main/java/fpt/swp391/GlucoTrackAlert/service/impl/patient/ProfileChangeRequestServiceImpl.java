package fpt.swp391.GlucoTrackAlert.service.impl.patient;

import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.model.patient.ProfileChangeRequest;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.repository.patient.ProfileChangeRequestRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.CloudinaryService;
import fpt.swp391.GlucoTrackAlert.service.patient.ProfileChangeRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ProfileChangeRequestServiceImpl implements ProfileChangeRequestService {

    private final ProfileChangeRequestRepository requestRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;

    @Autowired
    public ProfileChangeRequestServiceImpl(ProfileChangeRequestRepository requestRepository,
                                           PatientRepository patientRepository,
                                           UserRepository userRepository,
                                           CloudinaryService cloudinaryService) {
        this.requestRepository = requestRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.cloudinaryService = cloudinaryService;
    }

    @Override
    @Transactional
    public ProfileChangeRequest createRequest(Long userId, String fieldName, String reason, MultipartFile evidenceFile) {
        Patient patient = patientRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ bệnh nhân cho User ID: " + userId));

        if (!"hypertension".equalsIgnoreCase(fieldName) && !"heartDisease".equalsIgnoreCase(fieldName)) {
            throw new IllegalArgumentException("Trường yêu cầu thay đổi không hợp lệ: " + fieldName);
        }

        // Check if there is already a pending request for this field
        boolean hasPending = requestRepository.existsByPatientIdAndFieldNameAndStatus(patient.getId(), fieldName, "PENDING");
        if (hasPending) {
            throw new RuntimeException("Bạn đã có một yêu cầu thay đổi đang chờ xử lý cho mục này.");
        }

        // Validate evidence file
        if (evidenceFile == null || evidenceFile.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng tải lên tài liệu hoặc ảnh minh chứng y tế.");
        }

        // Handle file storage via Cloudinary
        String evidenceUrl;
        try {
            evidenceUrl = cloudinaryService.uploadFile(evidenceFile, "patient_evidences");
        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi lưu trữ file minh chứng y tế lên Cloudinary: " + e.getMessage(), e);
        }

        // Get current values to record in log
        String oldValue = "false";
        if ("hypertension".equalsIgnoreCase(fieldName)) {
            oldValue = String.valueOf(patient.getHypertension());
        } else if ("heartDisease".equalsIgnoreCase(fieldName)) {
            oldValue = String.valueOf(patient.getHeartDisease());
        }

        ProfileChangeRequest request = ProfileChangeRequest.builder()
                .patient(patient)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue("false") // One-way lock from true to false
                .reason(reason)
                .evidenceUrl(evidenceUrl)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return requestRepository.save(request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfileChangeRequest> getRequestsByPatient(Long patientId) {
        return requestRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProfileChangeRequest> getRequestsByPatientPaged(Long patientId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return requestRepository.findByPatientId(patientId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfileChangeRequest> getAllRequests() {
        return requestRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional
    public ProfileChangeRequest approveRequest(Long requestId, Long adminUserId) {
        ProfileChangeRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu thay đổi với ID: " + requestId));

        if (!"PENDING".equals(request.getStatus())) {
            throw new RuntimeException("Yêu cầu này đã được xử lý trước đó.");
        }

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản Admin với ID: " + adminUserId));

        // Update Patient fields
        Patient patient = request.getPatient();
        if ("hypertension".equalsIgnoreCase(request.getFieldName())) {
            patient.setHypertension(false);
        } else if ("heartDisease".equalsIgnoreCase(request.getFieldName())) {
            patient.setHeartDisease(false);
        }
        patientRepository.save(patient);

        // Update request status
        request.setStatus("APPROVED");
        request.setApprovedBy(admin);
        request.setUpdatedAt(LocalDateTime.now());

        return requestRepository.save(request);
    }

    @Override
    @Transactional
    public ProfileChangeRequest rejectRequest(Long requestId, Long adminUserId, String rejectionReason) {
        if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
            throw new IllegalArgumentException("Lý do từ chối không được để trống.");
        }

        ProfileChangeRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu thay đổi với ID: " + requestId));

        if (!"PENDING".equals(request.getStatus())) {
            throw new RuntimeException("Yêu cầu này đã được xử lý trước đó.");
        }

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản Admin với ID: " + adminUserId));

        // Update request status
        request.setStatus("REJECTED");
        request.setRejectionReason(rejectionReason);
        request.setApprovedBy(admin);
        request.setUpdatedAt(LocalDateTime.now());

        return requestRepository.save(request);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPendingRequest(Long patientId, String fieldName) {
        return requestRepository.existsByPatientIdAndFieldNameAndStatus(patientId, fieldName, "PENDING");
    }
}
