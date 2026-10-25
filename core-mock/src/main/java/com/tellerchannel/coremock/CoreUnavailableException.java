package com.tellerchannel.coremock;

/** Lỗi kỹ thuật giả lập: core quá tải. Khác lỗi nghiệp vụ, thử lại sau có thể thành công. */
public class CoreUnavailableException extends RuntimeException {
    public CoreUnavailableException() {
        super("Core is temporarily unavailable (simulated)");
    }
}
