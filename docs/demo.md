# Kịch bản demo (chạy thử bằng tay)

Chạy trong **Git Bash** (cài cùng Git for Windows) hoặc terminal Linux/macOS.
PowerShell cũng chạy được nhưng phải gõ `curl.exe` thay cho `curl` và đổi cách viết dấu nháy.
Cách nhanh hơn: mở http://localhost:8080 để dùng màn hình demo (step 14).

## 0. Khởi động

```bash
docker compose up -d --build        # build 2 app + chạy Oracle, Kafka, Zipkin, core-mock, channel-service
docker compose ps                   # xem container nào đã lên (Oracle cần ~30–60s để healthy)
docker compose logs -f channel-service   # theo dõi log channel (Ctrl+C để thoát xem log)
```

Không dùng Docker: chạy `./mvnw spring-boot:run` trong `core-mock/` rồi trong `channel-service/`
(channel dùng H2 in-memory, không cần Oracle).

## 1. Đăng nhập

```bash
# Lấy token GDV và KSV, lưu vào biến để dùng tiếp (jq: công cụ đọc JSON; không có jq thì copy tay accessToken)
GDV=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"gdv01","password":"Teller@123"}' | jq -r .accessToken)
KSV=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"ksv01","password":"Teller@123"}' | jq -r .accessToken)
```

## 2. Luồng bình thường

```bash
# Tra cứu tài khoản: số dư lấy thẳng từ core
curl -s localhost:8080/api/accounts/23122002 -H "Authorization: Bearer $GDV"

# GDV lập giao dịch rút 100.000 (Idempotency-Key: chạy lại lệnh này với cùng key sẽ KHÔNG tạo giao dịch mới)
curl -s -X POST localhost:8080/api/transactions -H "Authorization: Bearer $GDV" \
  -H 'Idempotency-Key: demo-001' -H 'Content-Type: application/json' \
  -d '{"type":"WITHDRAWAL","accountNo":"23122002","amount":100000,"currency":"VND"}'

# GDV gửi duyệt (thay 1 bằng id vừa nhận)
curl -s -X POST localhost:8080/api/transactions/1/submit -H "Authorization: Bearer $GDV"

# GDV tự duyệt → 403 FORBIDDEN (sai vai trò)
curl -s -X POST localhost:8080/api/transactions/1/approve -H "Authorization: Bearer $GDV"

# KSV xem danh sách chờ duyệt, rồi duyệt → SUCCESS
curl -s "localhost:8080/api/transactions?status=PENDING_APPROVAL" -H "Authorization: Bearer $KSV"
curl -s -X POST localhost:8080/api/transactions/1/approve -H "Authorization: Bearer $KSV"

# Xem nhật ký: ai làm gì, lúc nào
curl -s localhost:8080/api/transactions/1/audit -H "Authorization: Bearer $KSV"
```

## 3. Gây sự cố: core chậm 10 giây (xem RCA-001)

```bash
# Bật chaos ở core-mock: mỗi lệnh hạch toán ngủ 10s trước khi xử lý
curl -s -X PUT localhost:8081/core/admin/chaos -H 'Content-Type: application/json' \
  -d '{"postingDelayMs":10000,"postingFailureRate":0,"inquiryFailureRate":0}'

# Lập + gửi duyệt + duyệt như trên → sau 5s nhận status UNKNOWN; header X-Trace-Id là mã để tra Zipkin
curl -s -i -X POST localhost:8080/api/transactions/2/approve -H "Authorization: Bearer $KSV"

# Chờ ~30–60s rồi xem lại: job đối soát đã chốt SUCCESS (hoặc KSV bấm đối soát ngay)
curl -s -X POST localhost:8080/api/transactions/2/reconcile -H "Authorization: Bearer $KSV"

# Tắt chaos
curl -s -X POST localhost:8081/core/admin/chaos/reset
```

Mở http://localhost:9411, dán trace ID vào ô tìm kiếm để thấy span của channel (5s) và core (10s).

## 4. Các tình huống khác

| Muốn thấy | Cách làm | Kết quả |
|---|---|---|
| Không đủ số dư | Rút 99.000.000 từ `23122002` | `FAILED`, `INSUFFICIENT_FUNDS` |
| Tài khoản phong tỏa | Giao dịch với `88889999` | `FAILED`, `ACCT_BLOCKED` |
| Core quá tải | chaos `postingFailureRate: 1` | `UNKNOWN` (503), đối soát → `FAILED` `NOT_POSTED_AT_CORE` |
| Core tắt hẳn | `docker compose stop core-mock` rồi duyệt | `FAILED`, `CORE_UNREACHABLE` (chắc chắn chưa gửi) |
| Sự kiện Kafka | `docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic teller.transaction.completed --from-beginning` | JSON mỗi giao dịch kết thúc |

## Tài khoản có sẵn ở core-mock

| Số TK | Chủ TK | Số dư | Trạng thái |
|---|---|---|---|
| 23122002 | NGUYEN THANH HA | 1.500.000 | ACTIVE |
| 17012007 | NGUYEN KIEU OANH | 2.000.000 | ACTIVE |
| 12345678 | PHAM THI MAI | 500.000.000 | ACTIVE |
| 88889999 | TRAN VAN KHOA | 10.000.000 | BLOCKED |

User demo: `gdv01`, `gdv02` (GDV), `ksv01`, `ksv02` (KSV), mật khẩu `Teller@123`.
