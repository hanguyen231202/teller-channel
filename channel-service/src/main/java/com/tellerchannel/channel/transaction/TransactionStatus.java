package com.tellerchannel.channel.transaction;

/** Vòng đời giao dịch kênh. Lưu bằng tên (EnumType.STRING), không lưu số thứ tự. */
public enum TransactionStatus {
    /** KHỞI TẠO: GDV vừa nhập. */
    INITIATED,
    /** CHỜ DUYỆT: đợi KSV. */
    PENDING_APPROVAL,
    /** TỪ CHỐI: KSV không duyệt. Kết thúc. */
    REJECTED,
    /** ĐÃ GỬI CORE: đã duyệt, đang/đã gọi core, chưa biết kết quả. */
    SENT_TO_CORE,
    /** THÀNH CÔNG: core xác nhận đã hạch toán. Kết thúc. */
    SUCCESS,
    /** THẤT BẠI: core từ chối rõ ràng, hoặc chắc chắn chưa tới core. Kết thúc. */
    FAILED,
    /** CHƯA RÕ: gửi rồi nhưng không nhận được trả lời. Chờ đối soát. */
    UNKNOWN;

    /** Bảng chuyển trạng thái hợp lệ. Mọi thay đổi trạng thái đều phải qua đây. */
    public boolean canTransitionTo(TransactionStatus next) {
        return switch (this) {
            case INITIATED -> next == PENDING_APPROVAL;
            case PENDING_APPROVAL -> next == SENT_TO_CORE || next == REJECTED;
            case SENT_TO_CORE -> next == SUCCESS || next == FAILED || next == UNKNOWN;
            case UNKNOWN -> next == SUCCESS || next == FAILED;
            case REJECTED, SUCCESS, FAILED -> false;
        };
    }

    public boolean isFinal() {
        return this == REJECTED || this == SUCCESS || this == FAILED;
    }
}
