package fpt.swp391.GlucoTrackAlert.controller.patient;

import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileRequest;
import fpt.swp391.GlucoTrackAlert.dto.patient.PatientProfileResponse;
import fpt.swp391.GlucoTrackAlert.service.patient.PatientService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/patient")
public class PatientController {

    private final PatientService patientService;

    @Autowired
    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @PostMapping
    public ResponseEntity<?> createProfile(@Valid @RequestBody PatientProfileRequest request, BindingResult result) {
        if (result.hasErrors()) {
            String errors = result.getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
        }
        try {
            PatientProfileResponse response = patientService.createProfile(request);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PutMapping("/{userId}")
    public ResponseEntity<?> updateProfile(@PathVariable Long userId, 
                                           @Valid @RequestBody PatientProfileRequest request, 
                                           BindingResult result) {
        if (result.hasErrors()) {
            String errors = result.getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
        }
        try {
            PatientProfileResponse response = patientService.updateProfile(userId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getProfileByUserId(@PathVariable Long userId) {
        try {
            PatientProfileResponse response = patientService.getProfileByUserId(userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }
}
