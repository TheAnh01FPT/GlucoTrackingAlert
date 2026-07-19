package fpt.swp391.GlucoTrackAlert.controller.contact;

import fpt.swp391.GlucoTrackAlert.dto.contact.ContactReplyRequest;
import fpt.swp391.GlucoTrackAlert.dto.contact.ContactResponseDTO;
import fpt.swp391.GlucoTrackAlert.service.contact.ContactRequestService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class ContactRequestAdminController {

    private final ContactRequestService contactRequestService;

    // Trả về trang giao diện Thymeleaf quản trị liên hệ
    @GetMapping("/admin/contact-requests")
    public String showContactRequestsPage() {
        return "admin/contact-requests";
    }

    // REST API lấy danh sách phân trang yêu cầu liên hệ theo trạng thái
    @GetMapping("/api/admin/contact-requests")
    @ResponseBody
    public ResponseEntity<Page<ContactResponseDTO>> getContactRequests(
            @RequestParam(defaultValue = "new") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(contactRequestService.getContactRequestsByStatus(status, pageable));
    }

    // REST API gửi câu trả lời liên hệ
    @PostMapping("/api/admin/contact-requests/{id}/reply")
    @ResponseBody
    public ResponseEntity<ContactResponseDTO> replyToContact(
            @PathVariable Long id,
            @Valid @RequestBody ContactReplyRequest replyRequest,
            Authentication authentication
    ) {
        String adminEmail = authentication.getName();
        return ResponseEntity.ok(contactRequestService.replyToContactRequest(id, replyRequest.getReplyContent(), adminEmail));
    }
}
