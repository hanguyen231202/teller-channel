# Teller Channel

Ứng dụng kênh giao dịch tại quầy, đứng giữa giao dịch viên (GDV) và hệ thống core banking.
Core là nguồn sự thật về số dư và bút toán; kênh chỉ lưu trạng thái giao dịch kênh.

> Dự án học tập/portfolio, đang trong quá trình xây dựng.

## Cấu trúc

| Module | Port | Trạng thái | Mô tả |
|---|---|---|---|
| `core-mock/` | 8081 | Đã dựng khung | Giả lập core (CIF, tài khoản, hạch toán), có thể cấu hình chậm/timeout/lỗi |
| `channel-service/` | 8080 | Chưa bắt đầu | Vòng đời giao dịch, maker-checker, adapter gọi core, đối soát |
| `docs/` | – | Chưa bắt đầu | URD, sequence diagram, RCA |

## Yêu cầu

- JDK 21
- Không cần cài Maven: mỗi module có sẵn Maven Wrapper (`mvnw` / `mvnw.cmd`)

## Chạy core-mock

```bash
cd core-mock
./mvnw spring-boot:run
```

Trên Windows (cmd/PowerShell) dùng `mvnw.cmd` thay cho `./mvnw`.

Kiểm tra service đã lên:

```bash
curl http://localhost:8081/actuator/health
```

## Chạy test

```bash
cd core-mock
./mvnw test
```

## Stack

Java 21, Spring Boot 3.5, Maven, JUnit 5 + Mockito.
