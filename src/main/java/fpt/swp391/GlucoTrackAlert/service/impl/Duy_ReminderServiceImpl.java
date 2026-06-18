package fpt.swp391.GlucoTrackAlert.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fpt.swp391.GlucoTrackAlert.dto.reminder.Duy_ReminderRequest;
import fpt.swp391.GlucoTrackAlert.dto.reminder.Duy_ReminderResponse;
import fpt.swp391.GlucoTrackAlert.model.Duy_HealthReminder;
import fpt.swp391.GlucoTrackAlert.repository.Duy_ReminderRepository;
import fpt.swp391.GlucoTrackAlert.service.Duy_ReminderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class Duy_ReminderServiceImpl implements Duy_ReminderService {

    @Autowired
    private Duy_ReminderRepository repo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== PRIVATE HELPERS ====================
    /**
     * Parse chuỗi thời gian linh hoạt: hỗ trợ ISO format có/không có ms,
     * có/không có 'Z' VD: "2025-06-15T08:00:00", "2025-06-15T08:00:00.000",
     * "2025-06-15T08:00:00Z"
     */
    private static final DateTimeFormatter FLEXIBLE_DT = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
            .optionalStart().appendLiteral('Z').optionalEnd()
            .toFormatter();

    private LocalDateTime parseReminderTime(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Thời gian không được để trống");
        }
        // Bỏ offset nếu có (vd: +07:00)
        String cleaned = raw.replaceAll("[+-]\\d{2}:\\d{2}$", "").replace("Z", "");
        return LocalDateTime.parse(cleaned, FLEXIBLE_DT);
    }

    private Duy_HealthReminder fromRequest(Duy_ReminderRequest req) {
        Duy_HealthReminder r = new Duy_HealthReminder();
        r.setPatientId(req.getPatientId());
        r.setReminderType(req.getReminderType());
        r.setTitle(req.getTitle());
        r.setMessage(req.getMessage());
        r.setReminderTime(parseReminderTime(req.getReminderTime()));
        r.setRepeatType(req.getRepeatType() != null ? req.getRepeatType() : "NONE");
        r.setPrescriptionItemId(req.getPrescriptionItemId());
        r.setEndDate(req.getEndDate());
        r.setStatus("ACTIVE");
        r.setIsSent(false);
        return r;
    }

    // ==================== CRUD ====================
    @Override
    public Duy_ReminderResponse create(Duy_ReminderRequest request) {
        Duy_HealthReminder entity = fromRequest(request);
        Duy_HealthReminder saved = repo.save(entity);

        // Nếu có Google token thì tự động sync
        if (request.getGoogleAccessToken() != null && !request.getGoogleAccessToken().isBlank()) {
            try {
                syncToGoogleCalendar(saved.getId(), request.getGoogleAccessToken());
                saved = repo.findById(saved.getId()).orElse(saved);
            } catch (Exception e) {
                // Không fail nếu GG Calendar lỗi
                System.err.println("[Reminder] Google Calendar sync failed on create: " + e.getMessage());
            }
        }
        return Duy_ReminderResponse.from(saved);
    }

    @Override
    public List<Duy_ReminderResponse> getAllByPatient(Long patientId) {
        return repo.findByPatientIdOrderByReminderTimeAsc(patientId)
                .stream()
                .map(Duy_ReminderResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Duy_ReminderResponse> getById(Long id) {
        return repo.findById(id).map(Duy_ReminderResponse::from);
    }

    @Override
    public Duy_ReminderResponse update(Long id, Duy_ReminderRequest request) {
        Duy_HealthReminder existing = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reminder không tồn tại: " + id));

        if (request.getTitle() != null) {
            existing.setTitle(request.getTitle());
        }
        if (request.getMessage() != null) {
            existing.setMessage(request.getMessage());
        }
        if (request.getReminderType() != null) {
            existing.setReminderType(request.getReminderType());
        }
        if (request.getReminderTime() != null) {
            existing.setReminderTime(parseReminderTime(request.getReminderTime()));
            // BUG FIX: Reset isSent khi user đổi giờ, để scheduler gửi lại đúng giờ mới
            existing.setIsSent(false);
            if ("COMPLETED".equals(existing.getStatus())) {
                existing.setStatus("ACTIVE");
            }
        }
        if (request.getRepeatType() != null) {
            existing.setRepeatType(request.getRepeatType());
        }

        Duy_HealthReminder saved = repo.save(existing);

        // Nếu đã sync GG Calendar thì update event
        if (request.getGoogleAccessToken() != null
                && !request.getGoogleAccessToken().isBlank()
                && saved.getGoogleCalendarEventId() != null) {
            try {
                updateGoogleCalendarEvent(saved, request.getGoogleAccessToken());
            } catch (Exception e) {
                // Throw ra để frontend biết token hết hạn và xử lý re-auth
                throw new RuntimeException("GOOGLE_TOKEN_EXPIRED: " + e.getMessage());
            }
        }

        return Duy_ReminderResponse.from(saved);
    }

    @Override
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new IllegalArgumentException("Reminder không tồn tại: " + id);
        }
        repo.deleteById(id);
    }

    @Override
    public void cancelByPrescriptionItemId(Long prescriptionItemId) {
        if (prescriptionItemId == null) return;
        List<Duy_HealthReminder> reminders = repo.findByPrescriptionItemId(prescriptionItemId);
        for (Duy_HealthReminder r : reminders) {
            r.setStatus("CANCELLED");
        }
        repo.saveAll(reminders);
    }

    // ==================== LỌC ====================
    @Override
    public List<Duy_ReminderResponse> getByPatientAndStatus(Long patientId, String status) {
        return repo.findByPatientIdAndStatusOrderByReminderTimeAsc(patientId, status.toUpperCase())
                .stream().map(Duy_ReminderResponse::from).collect(Collectors.toList());
    }

    @Override
    public List<Duy_ReminderResponse> getByPatientAndType(Long patientId, String type) {
        return repo.findByPatientIdAndReminderTypeOrderByReminderTimeAsc(patientId, type.toUpperCase())
                .stream().map(Duy_ReminderResponse::from).collect(Collectors.toList());
    }

    @Override
    public List<Duy_ReminderResponse> getUpcoming(Long patientId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next24h = now.plusHours(24);
        return repo.findUpcomingReminders(patientId, now, next24h)
                .stream().map(Duy_ReminderResponse::from).collect(Collectors.toList());
    }

    // ==================== HÀNH ĐỘNG NHANH ====================
    @Override
    public Duy_ReminderResponse markComplete(Long id) {
        Duy_HealthReminder r = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reminder không tồn tại: " + id));
        r.setStatus("COMPLETED");
        return Duy_ReminderResponse.from(repo.save(r));
    }

    @Override
    public Duy_ReminderResponse markActive(Long id) {
        Duy_HealthReminder r = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reminder không tồn tại: " + id));
        r.setStatus("ACTIVE");
        r.setIsSent(false);
        return Duy_ReminderResponse.from(repo.save(r));
    }

    @Override
    public long countActive(Long patientId) {
        return repo.countByPatientIdAndStatus(patientId, "ACTIVE");
    }

    // ==================== GOOGLE CALENDAR ====================
    /**
     * Tạo event mới trên Google Calendar rồi lưu event ID vào DB
     */
    @Override
    public Duy_ReminderResponse syncToGoogleCalendar(Long reminderId, String googleAccessToken) {
        Duy_HealthReminder r = repo.findById(reminderId)
                .orElseThrow(() -> new IllegalArgumentException("Reminder không tồn tại: " + reminderId));

        try {
            String eventId = createGoogleCalendarEvent(r, googleAccessToken);
            r.setGoogleCalendarEventId(eventId);
            repo.save(r);
        } catch (Exception e) {
            throw new RuntimeException("Không thể đồng bộ Google Calendar: " + e.getMessage());
        }

        return Duy_ReminderResponse.from(repo.findById(reminderId).orElse(r));
    }

    /**
     * Xoá event trên Google Calendar
     */
    @Override
    public void deleteFromGoogleCalendar(Long reminderId, String googleAccessToken) {
        Duy_HealthReminder r = repo.findById(reminderId)
                .orElseThrow(() -> new IllegalArgumentException("Reminder không tồn tại: " + reminderId));

        if (r.getGoogleCalendarEventId() == null || r.getGoogleCalendarEventId().isBlank()) {
            return; // Chưa sync thì không cần xoá
        }

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.googleapis.com/calendar/v3/calendars/primary/events/"
                            + r.getGoogleCalendarEventId()))
                    .header("Authorization", "Bearer " + googleAccessToken)
                    .DELETE()
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
            r.setGoogleCalendarEventId(null);
            repo.save(r);
        } catch (Exception e) {
            throw new RuntimeException("Không thể xoá sự kiện trên Google Calendar: " + e.getMessage());
        }
    }

    // ==================== PRIVATE: GG CALENDAR REST CALLS ====================
    /**
     * Tạo event trên Google Calendar và trả về eventId
     */
    private String createGoogleCalendarEvent(Duy_HealthReminder r, String token) throws Exception {
        String body = buildEventJson(r);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.googleapis.com/calendar/v3/calendars/primary/events"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            JsonNode json = objectMapper.readTree(response.body());
            return json.get("id").asText();
        } else {
            throw new RuntimeException("Google Calendar API trả lỗi: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * Cập nhật event đã tồn tại trên Google Calendar
     */
    private void updateGoogleCalendarEvent(Duy_HealthReminder r, String token) throws Exception {
        String body = buildEventJson(r);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.googleapis.com/calendar/v3/calendars/primary/events/"
                        + r.getGoogleCalendarEventId()))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Google Calendar update lỗi: " + response.statusCode());
        }
    }

    /**
     * Build JSON body theo Google Calendar API v3 format Docs:
     * https://developers.google.com/calendar/api/v3/reference/events
     */
    private String buildEventJson(Duy_HealthReminder r) throws Exception {
        DateTimeFormatter ggFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        LocalDateTime start = r.getReminderTime();
        LocalDateTime end = start.plusMinutes(1);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("summary", r.getTitle());
        root.put("description",
                (r.getMessage() != null ? r.getMessage() : "")
                + "\n\n[GlucoTrack - " + r.getReminderType() + "]");

        // start
        ObjectNode startNode = objectMapper.createObjectNode();
        startNode.put("dateTime", start.format(ggFmt));
        startNode.put("timeZone", "Asia/Ho_Chi_Minh");
        root.set("start", startNode);

        // end
        ObjectNode endNode = objectMapper.createObjectNode();
        endNode.put("dateTime", end.format(ggFmt));
        endNode.put("timeZone", "Asia/Ho_Chi_Minh");
        root.set("end", endNode);

        // reminders popup 10 phút trước
        ObjectNode reminders = objectMapper.createObjectNode();
        reminders.put("useDefault", false);
        ArrayNode overrideList = objectMapper.createArrayNode();
        ObjectNode popup = objectMapper.createObjectNode();
        popup.put("method", "popup");
        popup.put("minutes", 0);
        overrideList.add(popup);
        reminders.set("overrides", overrideList);
        root.set("reminders", reminders);

        // Lặp lại (recurrence) theo Google Calendar format
        if (r.getRepeatType() != null && !r.getRepeatType().equals("NONE")) {
            ArrayNode recurrence = objectMapper.createArrayNode();
            String rrule = switch (r.getRepeatType()) {
                case "DAILY" ->
                    "RRULE:FREQ=DAILY";
                case "WEEKLY" ->
                    "RRULE:FREQ=WEEKLY";
                case "MONTHLY" ->
                    "RRULE:FREQ=MONTHLY";
                default ->
                    null;
            };
            if (rrule != null) {
                recurrence.add(rrule);
            }
            if (recurrence.size() > 0) {
                root.set("recurrence", recurrence);
            }
        }

        return objectMapper.writeValueAsString(root);
    }
}