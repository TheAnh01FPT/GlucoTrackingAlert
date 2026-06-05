package fpt.swp391.GlucoTrackAlert.controller.relative;

import fpt.swp391.GlucoTrackAlert.dto.relative.RelativeRequest;
import fpt.swp391.GlucoTrackAlert.dto.relative.RelativeResponse;
import fpt.swp391.GlucoTrackAlert.service.relative.RelativeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/patient/relatives")
public class RelativeController {

    private final RelativeService relativeService;

    @Autowired
    public RelativeController(RelativeService relativeService) {
        this.relativeService = relativeService;
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<RelativeResponse>> getRelativesByPatientId(@PathVariable Long patientId) {
        List<RelativeResponse> relatives = relativeService.getRelativesByPatientId(patientId);
        return ResponseEntity.ok(relatives);
    }

    @PostMapping
    public ResponseEntity<?> addRelative(@Valid @RequestBody RelativeRequest request, BindingResult result) {
        if (result.hasErrors()) {
            String errors = result.getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
        }
        try {
            RelativeResponse response = relativeService.addRelative(request);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PutMapping("/{relativeId}")
    public ResponseEntity<?> updateRelative(@PathVariable Long relativeId, 
                                            @Valid @RequestBody RelativeRequest request, 
                                            BindingResult result) {
        if (result.hasErrors()) {
            String errors = result.getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
        }
        try {
            RelativeResponse response = relativeService.updateRelative(relativeId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/{relativeId}")
    public ResponseEntity<?> deleteRelative(@PathVariable Long relativeId) {
        try {
            relativeService.deleteRelative(relativeId);
            return ResponseEntity.ok("Relative deleted successfully");
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PatchMapping("/{relativeId}/toggle-notify")
    public ResponseEntity<?> toggleNotification(@PathVariable Long relativeId, @RequestParam boolean enabled) {
        try {
            RelativeResponse response = relativeService.toggleNotification(relativeId, enabled);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
