package com.tellerchannel.channel.core;

/**
 * Kết quả gửi lệnh hạch toán. sealed: compiler bắt phải xử lý đủ 4 trường hợp trong switch.
 */
public sealed interface PostingOutcome {

    /** Core xác nhận đã hạch toán. → SUCCESS */
    record Posted(CorePosting posting) implements PostingOutcome {
    }

    /** Core từ chối rõ ràng (4xx + code). Gửi lại cũng vậy. → FAILED */
    record Rejected(String code, String message) implements PostingOutcome {
    }

    /** Chắc chắn lệnh CHƯA tới core (không kết nối được), đã retry hết lượt. → FAILED */
    record NotSent(String reason) implements PostingOutcome {
    }

    /** Đã gửi nhưng không biết core xử lý chưa (timeout, 5xx, đứt kết nối giữa chừng). → UNKNOWN */
    record Unknown(String reason) implements PostingOutcome {
    }
}
