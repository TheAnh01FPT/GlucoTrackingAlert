package fpt.swp391.GlucoTrackAlert.controller;

import fpt.swp391.GlucoTrackAlert.model.Notification;
import fpt.swp391.GlucoTrackAlert.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // Lấy danh sách thông báo theo userId
    @GetMapping
    public ResponseEntity<List<Notification>> getNotifications(@RequestParam Long userId) {
        return ResponseEntity.ok(notificationService.getByUserId(userId));
    }

    // Đánh dấu đã đọc
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }
}