package fpt.swp391.GlucoTrackAlert.doctor;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<?> create(@RequestBody DoctorIntroduction body) {
        // Chặn giới thiệu trùng: 1 bác sĩ chỉ được xuất hiện 1 lần trong danh sách giới thiệu
        if (body.getDoctorId() != null && repo.existsByDoctorId(body.getDoctorId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Bác sĩ này đã được giới thiệu rồi, không thể thêm trùng.");
        }
        // Chặn trùng thứ tự hiển thị
        if (body.getDisplayOrder() != null && repo.existsByDisplayOrder(body.getDisplayOrder())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Thứ tự hiển thị " + body.getDisplayOrder() + " đã được dùng cho bác sĩ khác.");
        }
        return ResponseEntity.ok(repo.save(body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody DoctorIntroduction body) {
        return repo.findById(id).map(existing -> {
            // Chặn đổi sang một bác sĩ đã được giới thiệu ở bản ghi khác
            if (body.getDoctorId() != null
                    && repo.existsByDoctorIdAndIdNot(body.getDoctorId(), id)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Bác sĩ này đã được giới thiệu ở một mục khác rồi.");
            }
            // Chặn đổi sang thứ tự hiển thị đang được bản ghi khác sử dụng
            if (body.getDisplayOrder() != null
                    && repo.existsByDisplayOrderAndIdNot(body.getDisplayOrder(), id)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Thứ tự hiển thị " + body.getDisplayOrder() + " đã được dùng cho bác sĩ khác.");
            }

            if (body.getDisplayName() != null)  existing.setDisplayName(body.getDisplayName());
            if (body.getTitle() != null)         existing.setTitle(body.getTitle());
            if (body.getSpecialization() != null) existing.setSpecialization(body.getSpecialization());
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