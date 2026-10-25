# URD: Nộp/rút tiền tại quầy có maker-checker

> Tài liệu yêu cầu người dùng (User Requirement Document) một trang cho Teller Channel.
> Phiên bản 1.0, Tuần 1 – Ngày 2.

## 1. Mục tiêu

Cho phép giao dịch viên (GDV) lập giao dịch nộp tiền / rút tiền mặt vào tài khoản khách hàng,
kiểm soát viên (KSV) duyệt, sau đó hệ thống kênh gửi lệnh hạch toán sang core banking.
Kênh phải xử lý an toàn khi core chậm, lỗi hoặc không trả lời.

## 2. Người dùng

| Vai trò | Mã | Được làm |
|---|---|---|
| Giao dịch viên | `TELLER` | Tra cứu tài khoản, lập giao dịch, gửi duyệt |
| Kiểm soát viên | `SUPERVISOR` | Tra cứu, xem danh sách chờ duyệt, duyệt / từ chối, yêu cầu đối soát lại |

## 3. Yêu cầu chức năng

| Mã | Yêu cầu |
|---|---|
| FR-01 | Người dùng đăng nhập bằng tên đăng nhập + mật khẩu, nhận token có thời hạn. |
| FR-02 | Tra cứu tài khoản: hiển thị chủ tài khoản, loại tiền, số dư, trạng thái. Số dư lấy **trực tiếp từ core** tại thời điểm tra cứu. |
| FR-03 | GDV lập giao dịch `DEPOSIT` (nộp) hoặc `WITHDRAWAL` (rút): số tài khoản, số tiền (> 0, tối đa 2 số lẻ), loại tiền, diễn giải. |
| FR-04 | Mỗi yêu cầu lập giao dịch có **Idempotency-Key**. Gửi lại cùng key thì trả về giao dịch cũ, không tạo giao dịch mới. |
| FR-05 | GDV gửi duyệt giao dịch do chính mình lập. |
| FR-06 | KSV duyệt hoặc từ chối (bắt buộc ghi lý do). **Người duyệt phải khác người lập.** |
| FR-07 | Sau khi duyệt, kênh gửi lệnh hạch toán sang core kèm mã tham chiếu duy nhất. |
| FR-08 | Kết quả: core xác nhận → `SUCCESS`; core từ chối (không đủ số dư, tài khoản khóa…) → `FAILED`; không nhận được trả lời → `UNKNOWN` (CHƯA RÕ). |
| FR-09 | Job đối soát định kỳ hỏi lại core các giao dịch `UNKNOWN` (và `SENT_TO_CORE` bị treo), chốt `SUCCESS` hoặc `FAILED`. |
| FR-10 | Mọi lần đổi trạng thái đều ghi audit log: ai, lúc nào, từ trạng thái nào sang trạng thái nào. Không sửa, không xóa. |
| FR-11 | Khi giao dịch kết thúc, phát sự kiện để hệ thống khác (SMS, báo cáo) xử lý. |

## 4. Yêu cầu phi chức năng

- **An toàn tiền:** không bao giờ đoán kết quả khi core không trả lời; không gửi lại lệnh hạch toán "mù".
- **Kênh không lưu số dư.** Core là nguồn sự thật.
- **Timeout:** gọi core có connect timeout và read timeout (mặc định 2s / 5s), cấu hình được.
- **Truy vết:** mỗi request có trace ID xuyên suốt kênh → core, xem được trên Zipkin.
- **Bảo mật:** mật khẩu băm BCrypt, token ký số, phân quyền theo vai trò.
- **Tiền dùng `BigDecimal`**, lưu `NUMBER(19,2)`.

## 5. Ngoài phạm vi

Chuyển khoản, giao dịch ngoại tệ có tỷ giá, hạn mức duyệt nhiều cấp, in chứng từ, EOD.

## 6. Tiêu chí chấp nhận (ví dụ)

1. GDV `gdv01` lập rút 1.000.000 VND từ `23122002`, gửi duyệt → trạng thái `PENDING_APPROVAL`.
2. `gdv01` tự duyệt → bị từ chối với mã `SELF_APPROVAL_NOT_ALLOWED`.
3. `ksv01` duyệt → `SUCCESS`, số dư tại core giảm đúng 1.000.000.
4. Core chậm 10s (timeout kênh 5s) → `UNKNOWN`; sau khi đối soát → `SUCCESS`, core chỉ hạch toán **một lần**.
