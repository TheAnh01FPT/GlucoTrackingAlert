package fpt.swp391.GlucoTrackAlert.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ✅ Thêm handler này
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDuplicateEntry(DataIntegrityViolationException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "Conflict");
        String message = "Dữ liệu bị trùng lặp.";
        String detailed = null;
        if (ex.getMostSpecificCause() != null && ex.getMostSpecificCause().getMessage() != null) {
            detailed = ex.getMostSpecificCause().getMessage();
        } else if (ex.getMessage() != null) {
            detailed = ex.getMessage();
        }
        if (detailed != null) {
            if (detailed.contains("idx_patient_log_date")) {
                message = "Bạn đã nhập nhật ký sức khỏe cho ngày này rồi. Vui lòng chỉnh sửa thay vì tạo mới.";
            } else if (detailed.contains("fk_ai_analysis_logs_daily_log_id") || detailed.contains("ai_analysis_logs") ) {
                message = "Không thể xóa nhật ký: tồn tại bản ghi phân tích liên quan (AI). Vui lòng xóa các bản ghi phụ trước.";
            } else if (detailed.contains("users.email")) {
                message = "Email này đã được sử dụng trong hệ thống.";
            } else if (detailed.contains("users.phone")) {
                message = "Số điện thoại này đã được sử dụng trong hệ thống.";
            }
        }
        body.put("message", message);
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGlobalException(Exception ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
        body.put("message", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Error");

        String errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        body.put("message", errors);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }
}