package com.tellerchannel.channel.core;

/** Kết quả hỏi core "mã tham chiếu này đã hạch toán chưa?" (dùng khi đối soát). */
public sealed interface InquiryOutcome {

    record Found(CorePosting posting) implements InquiryOutcome {
    }

    /** Core trả lời chắc chắn: chưa có bút toán nào với mã này. */
    record NotFound() implements InquiryOutcome {
    }

    /** Hỏi không được (core vẫn lỗi). Giữ nguyên trạng thái, lần sau hỏi lại. */
    record Unavailable(String reason) implements InquiryOutcome {
    }
}
