package fpt.swp391.GlucoTrackAlert.model.article;

/**
 * Enum định nghĩa các trạng thái của bài viết
 */
public enum ArticleStatus {
    DRAFT("draft"),
    PENDING_REVIEW("pending_review"),
    REJECTED("rejected"),
    PUBLISHED("published");

    private final String value;

    ArticleStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Chuyển đổi từ string sang enum
     */
    public static ArticleStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        for (ArticleStatus status : ArticleStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return DRAFT; // Mặc định về DRAFT nếu giá trị lạ
    }
}
