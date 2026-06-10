package fpt.swp391.GlucoTrackAlert.enums;

import java.time.LocalTime;

/**
 * Giờ làm việc CỐ ĐỊNH áp dụng cho TẤT CẢ bác sĩ trong hệ thống.
 * Không lưu DB, không có dropdown chọn – fix cứng tại đây.
 *
 * Để thay đổi giờ làm việc: chỉnh sửa START / END / DAYS trong file này, không cần migration DB.
 *
 * Dùng cho:
 *   - Hiển thị giờ làm việc trên trang quản lý bác sĩ (admin)
 *   - Giới hạn thời gian gửi thông báo cho bệnh nhân (chỉ trong giờ làm)
 *   - Bất kỳ logic nào cần check "có đang trong giờ làm không"
 */
public final class WorkShift {

    private WorkShift() {}

    // ── Cấu hình giờ làm việc ────────────────────────────────────────────
    public static final LocalTime START       = LocalTime.of(8, 0);   // 08:00
    public static final LocalTime END         = LocalTime.of(17, 0);  // 17:00
    public static final String    DAYS        = "Thứ 2 – Chủ nhật";   // tất cả các ngày
    public static final String    DISPLAY     = "08:00 – 17:00";
    public static final String    FULL_LABEL  = "08:00 – 17:00  |  " + DAYS;

    // ── Utility ──────────────────────────────────────────────────────────
    /**
     * Kiểm tra thời điểm hiện tại có đang trong giờ làm việc không.
     * Áp dụng cho tất cả các ngày trong tuần (T2–CN).
     */
    public static boolean isWithinWorkingHours() {
        LocalTime now = LocalTime.now();
        return !now.isBefore(START) && now.isBefore(END);
    }

    /**
     * Kiểm tra một thời điểm bất kỳ có trong giờ làm không.
     */
    public static boolean isWithinWorkingHours(LocalTime time) {
        return !time.isBefore(START) && time.isBefore(END);
    }
}