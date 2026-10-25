# Bảng mã lỗi

Mọi lỗi trả về dạng `ProblemDetail` (`application/problem+json`) với thuộc tính `code`.
Channel dựa vào **code** (không dựa vào câu chữ) để quyết định có retry hay không.

## core-mock

| HTTP | code | Loại | Channel xử lý |
|---|---|---|---|
| 400 | `INVALID_REQUEST` | Nghiệp vụ | Không retry → `FAILED` |
| 404 | `ACCT_NOT_FOUND` | Nghiệp vụ | Không retry → `FAILED` |
| 404 | `POSTING_NOT_FOUND` | Trả lời tra cứu | Đối soát → `FAILED` (chưa hạch toán) |
| 409 | `DUPLICATE_REFERENCE` | Nghiệp vụ | Cùng mã tham chiếu nhưng nội dung khác. Không retry → `FAILED` |
| 422 | `ACCT_BLOCKED` | Nghiệp vụ | Không retry → `FAILED` |
| 422 | `INSUFFICIENT_FUNDS` | Nghiệp vụ | Không retry → `FAILED` |
| 422 | `CURRENCY_MISMATCH` | Nghiệp vụ | Không retry → `FAILED` |
| 503 | `CORE_UNAVAILABLE` | Kỹ thuật | Hạch toán: `UNKNOWN` rồi đối soát. Tra cứu: retry |
| — | (timeout, mất kết nối) | Kỹ thuật | Hạch toán: `UNKNOWN`. Không kết nối được (chắc chắn chưa gửi): retry, hết lượt → `FAILED` `CORE_UNREACHABLE` |

## channel-service

| HTTP | code | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Dữ liệu đầu vào sai |
| 400 | `MISSING_IDEMPOTENCY_KEY` | Thiếu header `Idempotency-Key` |
| 401 | `BAD_CREDENTIALS` | Sai tên đăng nhập / mật khẩu |
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai token |
| 403 | `FORBIDDEN` | Sai vai trò |
| 403 | `SELF_APPROVAL_NOT_ALLOWED` | KSV duyệt giao dịch do chính mình lập |
| 403 | `NOT_TRANSACTION_MAKER` | GDV gửi duyệt giao dịch của người khác |
| 404 | `TXN_NOT_FOUND` | Không có giao dịch |
| 404 | `ACCT_NOT_FOUND` | Core báo không có tài khoản (khi tra cứu) |
| 409 | `INVALID_STATE` | Thao tác không hợp lệ với trạng thái hiện tại |
| 409 | `IDEMPOTENCY_KEY_REUSED` | Dùng lại key cũ cho nội dung khác |
| 409 | `CONCURRENT_UPDATE` | Hai người sửa cùng lúc (optimistic lock) |
| 503 | `CORE_UNAVAILABLE` | Tra cứu tài khoản nhưng core không trả lời |
