package fpt.swp391.GlucoTrackAlert.service.contact;

import fpt.swp391.GlucoTrackAlert.dto.contact.ContactRequestDTO;
import fpt.swp391.GlucoTrackAlert.dto.contact.ContactResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ContactRequestService {
    
    ContactResponseDTO saveContactRequest(ContactRequestDTO requestDTO);
    
    Page<ContactResponseDTO> getContactRequestsByStatus(String status, Pageable pageable);
    
    ContactResponseDTO getContactRequestById(Long id);
    
    ContactResponseDTO replyToContactRequest(Long id, String replyContent, String adminEmail);
}
