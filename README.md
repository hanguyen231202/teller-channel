# Teller Channel

Ứng dụng kênh giao dịch tại quầy, đứng giữa giao dịch viên (GDV) và hệ thống core banking.
Core là nguồn sự thật về số dư và bút toán; kênh chỉ lưu trạng thái giao dịch kênh.

> Dự án học tập/portfolio cho vị trí Phát triển Kênh Core Banking. Core là giả lập, không phải FIS Profile thật.
> Code được chia theo từng bước, xem [STEPS.md](STEPS.md).

## Điểm chính

- **Maker-checker:** GDV lập và gửi duyệt, KSV duyệt hoặc từ chối, người duyệt phải khác người lập. Vai trò lấy từ JWT.
- **Vòng đời giao dịch:** `INITIATED → PENDING_APPROVAL → SENT_TO_CORE → SUCCESS / FAILED / UNKNOWN`, có thêm `REJECTED`.
- **Gọi core an toàn:** connect/read timeout, phân biệt lỗi nghiệp vụ và lỗi kỹ thuật, chỉ retry lệnh ghi khi chắc chắn chưa gửi.
- **Trạng thái CHƯA RÕ + đối soát:** timeout không bị coi là thất bại; job định kỳ hỏi lại core bằng mã tham chiếu rồi chốt.
- **Idempotency:** header `Idempotency-Key` + ràng buộc UNIQUE ở kênh; `referenceNo` chống hạch toán trùng ở core.
- **Audit log** ghi cùng transaction với mỗi lần đổi trạng thái.
- **Oracle** + Flyway, **Kafka** (sự kiện sau commit), **Micrometer Tracing + Zipkin**, **Docker Compose**.

## Cấu trúc

| Thư mục | Port | Mô tả |
|---|---|---|
| `core-mock/` | 8081 | Giả lập core: tài khoản, hạch toán Nợ/Có idempotent, tra cứu bút toán, chaos (chậm/503) |
| `channel-service/` | 8080 | Kênh: JWT, maker-checker, adapter gọi core, đối soát, audit, sự kiện, màn hình demo |
| `docs/` | – | URD, sequence diagram, bảng mã lỗi, RCA, kịch bản demo |

## Chạy nhanh (không cần Docker)

```bash
cd core-mock && ./mvnw spring-boot:run          # terminal 1: core giả ở :8081
cd channel-service && ./mvnw spring-boot:run    # terminal 2: kênh ở :8080, dùng H2 in-memory
```

Mở http://localhost:8080, đăng nhập `gdv01` / `Teller@123` (GDV) hoặc `ksv01` / `Teller@123` (KSV).
Trên Windows dùng `mvnw.cmd` thay cho `./mvnw`.

## Chạy đủ hệ thống (Oracle, Kafka, Zipkin)

```bash
docker compose up -d --build       # build 2 app và chạy toàn bộ
docker compose ps                  # kiểm tra trạng thái các container
```

Zipkin: http://localhost:9411. Lệnh demo từng tình huống (core chậm, core sập, không đủ số dư…): [docs/demo.md](docs/demo.md).

## Chạy test

```bash
cd core-mock && ./mvnw test          # 23 test
cd channel-service && ./mvnw test    # 58 test (có WireMock giả lập core, không cần Docker)
```

## API chính (channel-service)

| Method | Path | Vai trò | Mô tả |
|---|---|---|---|
| POST | `/api/auth/login` | – | Đăng nhập, nhận JWT |
| GET | `/api/accounts/{accountNo}` | đã đăng nhập | Tra cứu tài khoản (số dư lấy từ core) |
| POST | `/api/transactions` | TELLER | Lập giao dịch, bắt buộc header `Idempotency-Key` |
| POST | `/api/transactions/{id}/submit` | TELLER | Gửi duyệt |
| POST | `/api/transactions/{id}/approve` | SUPERVISOR | Duyệt và gửi core |
| POST | `/api/transactions/{id}/reject` | SUPERVISOR | Từ chối (kèm lý do) |
| POST | `/api/transactions/{id}/reconcile` | SUPERVISOR | Đối soát ngay giao dịch CHƯA RÕ |
| GET | `/api/transactions?status=…` | đã đăng nhập | Danh sách, phân trang |
| GET | `/api/transactions/{id}/audit` | đã đăng nhập | Nhật ký thay đổi |

## Stack

Java 21, Spring Boot 3.5, Spring Security (OAuth2 Resource Server, JWT), Spring Data JPA, Flyway, Oracle 23 Free / H2,
Spring Kafka, Micrometer Tracing (Brave) + Zipkin, JUnit 5, Mockito, WireMock, Docker Compose.
