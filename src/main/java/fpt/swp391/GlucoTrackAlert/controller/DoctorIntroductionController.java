package fpt.swp391.GlucoTrackAlert.controller;

import fpt.swp391.GlucoTrackAlert.model.DoctorIntroduction;
import fpt.swp391.GlucoTrackAlert.repository.DoctorIntroductionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/doctor-introductions")
@RequiredArgsConstructor
public class DoctorIntroductionController {

    private final DoctorIntroductionRepository repo;

    @GetMapping
    public ResponseEntity<List<DoctorIntroduction>> getAll() {
        return ResponseEntity.ok(repo.findAll()
                .stream()
                .sorted((a, b) -> Integer.compare(
                        a.getDisplayOrder() == null ? 0 : a.getDisplayOrder(),
                        b.getDisplayOrder() == null ? 0 : b.getDisplayOrder()))
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return repo.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<DoctorIntroduction> create(@RequestBody DoctorIntroduction body) {
        return ResponseEntity.ok(repo.save(body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody DoctorIntroduction body) {
        return repo.findById(id).map(existing -> {
            if (body.getDisplayName() != null)  existing.setDisplayName(body.getDisplayName());
            if (body.getTitle() != null)         existing.setTitle(body.getTitle());
            if (body.getIntroduction() != null)  existing.setIntroduction(body.getIntroduction());
            if (body.getAvatarUrl() != null)     existing.setAvatarUrl(body.getAvatarUrl());
            if (body.getDisplayOrder() != null)  existing.setDisplayOrder(body.getDisplayOrder());
            if (body.getStatus() != null)        existing.setStatus(body.getStatus());
            if (body.getDoctorId() != null)      existing.setDoctorId(body.getDoctorId());
            return ResponseEntity.ok(repo.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<?> toggle(@PathVariable Long id) {
        return repo.findById(id).map(existing -> {
            existing.setStatus("active".equalsIgnoreCase(existing.getStatus()) ? "inactive" : "active");
            return ResponseEntity.ok(repo.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.ok("Đã xóa");
    }
}