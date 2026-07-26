package fpt.swp391.GlucoTrackAlert.controller.contact;

import fpt.swp391.GlucoTrackAlert.dto.contact.ContactRequestDTO;
import fpt.swp391.GlucoTrackAlert.dto.contact.ContactResponseDTO;
import fpt.swp391.GlucoTrackAlert.service.contact.ContactRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contact-requests")
@RequiredArgsConstructor
public class ContactRequestController {

    private final ContactRequestService contactRequestService;

    @PostMapping
    public ResponseEntity<ContactResponseDTO> submitContactRequest(@Valid @RequestBody ContactRequestDTO requestDTO) {
        return ResponseEntity.ok(contactRequestService.saveContactRequest(requestDTO));
    }
}
