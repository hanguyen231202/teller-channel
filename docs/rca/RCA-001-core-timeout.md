# RCA-001: Giao dịch rút tiền rơi vào CHƯA RÕ do core phản hồi chậm

| | |
|---|---|
| Ngày diễn tập | 25/10/2026 |
| Môi trường | docker compose (Oracle Free, Kafka, Zipkin, core-mock, channel-service) |
| Mức độ | Trung bình: không mất tiền, khách phải chờ ~45 giây |
| Trạng thái | Đã đóng |

> Sự cố được **cố ý gây ra** bằng chaos của core-mock để luyện quy trình xử lý. Số liệu bên dưới lấy từ lần chạy thật.

## 1. Hiện tượng

KSV `ksv01` duyệt giao dịch rút 50.000.000 VND từ tài khoản `12345678`. Màn hình chờ 5 giây rồi báo trạng thái
**UNKNOWN (CHƯA RÕ)**, mã `NO_RESPONSE`, thông điệp `TIMEOUT: HttpTimeoutException`. GDV chưa chi tiền.

Kiểm tra số dư ngay sau đó: **450.000.000** (trước là 500.000.000), tức core **đã trừ tiền** dù kênh báo không rõ.

## 2. Dòng thời gian (UTC)

| Thời điểm | Sự kiện | Nguồn |
|---|---|---|
| 16:32:37.446 | KSV duyệt, giao dịch `TCCC9F63766098464B853B` chuyển `SENT_TO_CORE` (đã commit) | audit log |
| 16:32:37.465 | channel gửi `POST /core/postings` | log channel, trace `6ade2f25…e61a` |
| 16:32:42.479 | Hết read timeout 5s, adapter phân loại `TIMEOUT` → `UNKNOWN`, **không gửi lại** | log channel |
| ~16:32:47.5 | core-mock xử lý xong sau 10s, ghi bút toán `PST00000001` | span core-mock 10.06s trên Zipkin |
| 16:33:23.422 | Job đối soát chạy, giao dịch đã đủ 30s (`min-age`) | log `ReconciliationService` |
| 16:33:24.395 | `GET /core/postings/TCCC…` trả 200 → chốt `SUCCESS` | audit log `RECONCILE`, actor `SYSTEM-RECON` |
| 16:33:24.461 | Sự kiện `SUCCESS` phát lên Kafka topic `teller.transaction.completed` | log `KafkaTransactionEventSink` |

Trên Zipkin, cùng một trace ID có span `http post /api/transactions/{id}/approve` (5.09s, channel) và
span `http post /core/postings` (10.06s, core-mock): thấy ngay core chậm hơn ngưỡng chờ của kênh.

## 3. Tác động

- 1 giao dịch ở trạng thái CHƯA RÕ trong 47 giây. GDV không chi tiền trong thời gian này.
- Không hạch toán trùng: core chỉ nhận **một** lệnh với mã tham chiếu này.
- Không lệch số liệu sau đối soát: kênh `SUCCESS`, core có bút toán `PST00000001`.

## 4. Nguyên nhân gốc

- **Trực tiếp:** core xử lý lệnh hạch toán mất 10s (bật `postingDelayMs=10000`), vượt read timeout 5s của kênh.
- **Bản chất:** timeout ở phía người gọi **không có nghĩa là lệnh thất bại**. Lệnh đã tới core và core vẫn xử lý xong
  sau khi kênh đã ngừng chờ. Đây là lý do kênh phải có trạng thái CHƯA RÕ thay vì đoán THẤT BẠI.

Hỏi "vì sao" 5 lần:
1. Vì sao CHƯA RÕ? Kênh không nhận được phản hồi trong 5s.
2. Vì sao không nhận được? Core trả lời sau 10s.
3. Vì sao core chậm? (Diễn tập) cấu hình chaos. (Thực tế có thể là) khóa dòng, EOD đang chạy, DB core quá tải.
4. Vì sao kênh không chờ lâu hơn? Chờ lâu thì giữ thread và khách đứng quầy lâu; cần một giới hạn.
5. Vì sao không mất tiền? Thiết kế: commit `SENT_TO_CORE` trước khi gọi, không retry lệnh ghi khi timeout, đối soát bằng mã tham chiếu.

## 5. Xử lý

- Tự động: job đối soát chốt `SUCCESS` sau 47s, không cần can thiệp.
- Nếu khách đang chờ ở quầy: KSV dùng `POST /api/transactions/{id}/reconcile` để đối soát ngay, không chờ job.

## 6. Phòng ngừa / cải tiến

| # | Hành động | Loại |
|---|---|---|
| 1 | Cảnh báo khi số giao dịch `UNKNOWN` > 0 trong 5 phút (metric + Grafana) | Giám sát |
| 2 | Theo dõi p99 thời gian phản hồi core, đặt read timeout dựa trên số liệu (vd. p99 × 2) thay vì đoán | Cấu hình |
| 3 | Thêm circuit breaker (Resilience4j): core chậm liên tục thì ngắt sớm, báo "core bận" ngay cho GDV | Code |
| 4 | Màn hình GDV hiển thị rõ "CHƯA RÕ – KHÔNG CHI TIỀN" và nút "đối soát lại" cho KSV | UI |
| 5 | Khi chạy nhiều instance: khóa job đối soát (ShedLock) để tránh hỏi core trùng | Code |

## 7. Bài học

- Timeout = "không biết", không phải "thất bại".
- Không retry mù lệnh ghi; chỉ HỎI lại bằng mã tham chiếu.
- Trace ID xuyên suốt giúp tìm nguyên nhân trong vài phút: một ID, thấy cả hai phía.
