package fpt.swp391.GlucoTrackAlert.service.impl.contact;

import fpt.swp391.GlucoTrackAlert.dto.contact.ContactRequestDTO;
import fpt.swp391.GlucoTrackAlert.dto.contact.ContactResponseDTO;
import fpt.swp391.GlucoTrackAlert.model.contact.ContactRequest;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.contact.ContactRequestRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.contact.ContactRequestService;
import fpt.swp391.GlucoTrackAlert.service.register.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ContactRequestServiceImpl implements ContactRequestService {

    private final ContactRequestRepository contactRequestRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Override
    @Transactional
    public ContactResponseDTO saveContactRequest(ContactRequestDTO requestDTO) {
        ContactRequest contactRequest = new ContactRequest();
        contactRequest.setFullName(requestDTO.getFullName());
        contactRequest.setEmail(requestDTO.getEmail());
        contactRequest.setPhone(requestDTO.getPhone());
        contactRequest.setSubject(requestDTO.getSubject());
        contactRequest.setMessage(requestDTO.getMessage());
        contactRequest.setStatus("new");

        ContactRequest saved = contactRequestRepository.save(contactRequest);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContactResponseDTO> getContactRequestsByStatus(String status, Pageable pageable) {
        if ("all".equalsIgnoreCase(status) || status == null || status.isEmpty()) {
            return contactRequestRepository.findAll(pageable)
                    .map(this::mapToResponse);
        }
        return contactRequestRepository.findByStatus(status, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ContactResponseDTO getContactRequestById(Long id) {
        ContactRequest contactRequest = contactRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu liên hệ với ID: " + id));
        return mapToResponse(contactRequest);
    }

    @Override
    @Transactional
    public ContactResponseDTO replyToContactRequest(Long id, String replyContent, String adminEmail) {
        ContactRequest contactRequest = contactRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu liên hệ với ID: " + id));

        if ("handled".equalsIgnoreCase(contactRequest.getStatus())) {
            throw new RuntimeException("Yêu cầu liên hệ này đã được xử lý trước đó.");
        }

        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản quản trị viên: " + adminEmail));

        // Cập nhật thông tin xử lý
        contactRequest.setStatus("handled");
        contactRequest.setHandledAt(LocalDateTime.now());
        contactRequest.setHandledBy(admin);
        contactRequest.setReplyContent(replyContent);

        ContactRequest updated = contactRequestRepository.save(contactRequest);

        // Gửi email phản hồi bằng HTML lịch sự
        String emailSubject = "GlucoTrackAlert - Phản hồi liên hệ: " + contactRequest.getSubject();
        String htmlBody = String.format(
                "<div style=\"font-family: 'Segoe UI', Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e5e7eb; border-radius: 8px;\">" +
                "  <h2 style=\"color: #1a6b5a; margin-top: 0;\">Xin chào %s,</h2>" +
                "  <p>Cảm ơn bạn đã liên hệ với hệ thống GlucoTrackAlert. Ban quản trị hệ thống xin phản hồi thắc mắc của bạn như sau:</p>" +
                "  <div style=\"border-left: 4px solid #1a6b5a; padding: 10px 15px; margin: 20px 0; background-color: #f9fafb; font-style: italic; color: #4b5563;\">" +
                "    <strong>Nội dung bạn gửi:</strong><br>%s" +
                "  </div>" +
                "  <div style=\"padding: 15px; background-color: #e8f5f1; border-radius: 6px; margin: 20px 0; border: 1px solid #d1e7dd; color: #134d40;\">" +
                "    <strong>Phản hồi từ Ban quản trị:</strong><br>%s" +
                "  </div>" +
                "  <p style=\"margin-bottom: 0;\">Trân trọng,<br><strong>Đội ngũ vận hành GlucoTrackAlert</strong></p>" +
                "</div>",
                contactRequest.getFullName(),
                contactRequest.getMessage().replace("\n", "<br>"),
                replyContent.replace("\n", "<br>")
        );

        try {
            emailService.sendHtmlMessageAsync(contactRequest.getEmail(), emailSubject, htmlBody);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return mapToResponse(updated);
    }

    private ContactResponseDTO mapToResponse(ContactRequest contactRequest) {
        return ContactResponseDTO.builder()
                .id(contactRequest.getId())
                .fullName(contactRequest.getFullName())
                .email(contactRequest.getEmail())
                .phone(contactRequest.getPhone())
                .subject(contactRequest.getSubject())
                .message(contactRequest.getMessage())
                .status(contactRequest.getStatus())
                .createdAt(contactRequest.getCreatedAt())
                .handledAt(contactRequest.getHandledAt())
                .handledByEmail(contactRequest.getHandledBy() != null ? contactRequest.getHandledBy().getEmail() : null)
                .replyContent(contactRequest.getReplyContent())
                .build();
    }
}
