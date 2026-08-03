package fpt.swp391.GlucoTrackAlert.service.impl.relative;

import fpt.swp391.GlucoTrackAlert.dto.relative.RelativeRequest;
import fpt.swp391.GlucoTrackAlert.dto.relative.RelativeResponse;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.model.relative.Relative;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.repository.relative.RelativeRepository;
import fpt.swp391.GlucoTrackAlert.service.relative.RelativeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RelativeServiceImpl implements RelativeService {

    private final RelativeRepository relativeRepository;
    private final PatientRepository patientRepository;

    @Autowired
    public RelativeServiceImpl(RelativeRepository relativeRepository, PatientRepository patientRepository) {
        this.relativeRepository = relativeRepository;
        this.patientRepository = patientRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelativeResponse> getRelativesByPatientId(Long patientId) {
        List<Relative> relatives = relativeRepository.findByPatientId(patientId);
        List<RelativeResponse> responseList = new ArrayList<>();
        for (Relative rel : relatives) {
            RelativeResponse dto = this.mapToResponse(rel);
            responseList.add(dto);
        }
        return responseList;
    }

    @Override
    @Transactional(readOnly = true)
    public RelativeResponse getRelativeById(Long relativeId) {
        Relative relative = relativeRepository.findById(relativeId)
                .orElseThrow(() -> new RuntimeException("Relative not found with ID: " + relativeId));
        return mapToResponse(relative);
    }

    @Override
    @Transactional
    public RelativeResponse addRelative(RelativeRequest request) {
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient profile not found with ID: " + request.getPatientId()));

        Relative relative = Relative.builder()
                .patient(patient)
                .fullName(request.getFullName())
                .relationship(request.getRelationship())
                .age(request.getAge())
                .phone(request.getPhone())
                .email(request.getEmail())
                .notifyEnabled(request.getNotifyEnabled() != null ? request.getNotifyEnabled() : true)
                .build();

        Relative savedRelative = relativeRepository.save(relative);
        return mapToResponse(savedRelative);
    }

    @Override
    @Transactional
    public RelativeResponse updateRelative(Long relativeId, RelativeRequest request) {
        Relative relative = relativeRepository.findById(relativeId)
                .orElseThrow(() -> new RuntimeException("Relative not found with ID: " + relativeId));

        relative.setFullName(request.getFullName());
        relative.setRelationship(request.getRelationship());
        relative.setAge(request.getAge());
        relative.setPhone(request.getPhone());
        relative.setEmail(request.getEmail());
        if (request.getNotifyEnabled() != null) {
            relative.setNotifyEnabled(request.getNotifyEnabled());
        }

        Relative updatedRelative = relativeRepository.save(relative);
        return mapToResponse(updatedRelative);
    }

    @Override
    @Transactional
    public void deleteRelative(Long relativeId) {
        if (!relativeRepository.existsById(relativeId)) {
            throw new RuntimeException("Relative not found with ID: " + relativeId);
        }
        relativeRepository.deleteById(relativeId);
    }

    @Override
    @Transactional
    public RelativeResponse toggleNotification(Long relativeId, boolean enabled) {
        Relative relative = relativeRepository.findById(relativeId)
                .orElseThrow(() -> new RuntimeException("Relative not found with ID: " + relativeId));

        relative.setNotifyEnabled(enabled);
        Relative updatedRelative = relativeRepository.save(relative);
        return mapToResponse(updatedRelative);
    }

    private RelativeResponse mapToResponse(Relative relative) {
        return RelativeResponse.builder()
                .id(relative.getId())
                .patientId(relative.getPatient().getId())
                .patientName(relative.getPatient().getFullName())
                .fullName(relative.getFullName())
                .relationship(relative.getRelationship())
                .age(relative.getAge())
                .phone(relative.getPhone())
                .email(relative.getEmail())
                .notifyEnabled(relative.getNotifyEnabled())
                .createdAt(relative.getCreatedAt())
                .updatedAt(relative.getUpdatedAt())
                .build();
    }
}
