# Teller Channel — bối cảnh cho Claude

## Mục đích
Dự án portfolio để ứng tuyển **Chuyên viên Phát triển Kênh Core Banking cấp 1 (BIDV)**, nhánh Spring Boot.
Mục tiêu chính là HỌC và trả lời được phỏng vấn sâu về từng quyết định thiết kế, không phải ra sản phẩm nhanh.

## Người học
- Nền tảng: Java/Kotlin (Android ~3 năm), C#, Git, unit test, RCA.
- Chưa có kinh nghiệm Spring Boot/web thực tế.
- Thời gian: ~2h tối ngày thường, 5–6h cuối tuần, tổng 2–3 tuần.

## Bối cảnh nghiệp vụ
- BIDV dùng core FIS Profile. Ứng dụng kênh đứng giữa giao dịch viên (GDV) và core.
- Core là nguồn sự thật về số dư và bút toán. Kênh KHÔNG giữ số dư, chỉ lưu trạng thái giao dịch kênh.

## Kiến trúc
- `core-mock/` (port 8081): giả lập core (CIF, tài khoản, hạch toán), cố tình chậm/timeout/lỗi qua cấu hình.
- `channel-service/` (port 8080, TRỌNG TÂM):
  - Vòng đời giao dịch: KHỞI TẠO → CHỜ DUYỆT → ĐÃ GỬI CORE → THÀNH CÔNG / THẤT BẠI / CHƯA RÕ
  - Maker-checker GDV/KSV + JWT
  - Adapter gọi core có timeout/retry (phân biệt lỗi nghiệp vụ không retry vs lỗi kỹ thuật)
  - Job đối soát giao dịch CHƯA RÕ
  - Idempotency, audit log, Oracle lưu dữ liệu kênh
- Phụ: Kafka sự kiện, Micrometer Tracing + Zipkin, Angular 2–3 màn hình.
- `docs/`: URD, sequence diagram, RCA.

## Stack
Java 21, Spring Boot 3.x, Maven (dùng `mvnw`), JUnit 5 + Mockito, Docker. Tiền dùng `BigDecimal`.

## Lộ trình
- Tuần 1: nền tảng Spring Boot, URD/thiết kế, core-mock, khung channel-service.
- Tuần 2: vòng đời giao dịch, bảo mật, timeout/đối soát, test.
- Tuần 3: tracing + RCA, Kafka/UI, Docker, README, cập nhật CV, luyện phỏng vấn.

## Tiến độ
- [x] Tuần 1 – Ngày 1: dựng repo, core-mock API tra cứu tài khoản, @RestControllerAdvice, 3 test
  - [x] Môi trường: Temurin JDK 21, IntelliJ IDEA 2026.2; core-mock Boot 3.5.16, package `com.tellerchannel.coremock`, port 8081 (`application.properties`)
  - [x] `Account` record (BigDecimal), `AccountNotFoundException`, `AccountService` (ConcurrentHashMap, seed trong constructor)
  - [x] Unit test thuần `AccountServiceTest` (2 test pass)
  - [x] `AccountController` GET `/core/accounts/{accountNo}` (constructor injection)
  - [x] `GlobalExceptionHandler` (`@RestControllerAdvice`) → ProblemDetail 404, `code=ACCT_NOT_FOUND`, `application/problem+json`
  - [x] `AccountControllerTest` (`@WebMvcTest` + `@MockitoBean`): 200 / 404 — tổng 5 test xanh
  - Đã học: record vs static, checked/unchecked exception, check-then-act, `BigDecimal.equals` so cả scale → dùng `compareTo`, thứ tự `assertEquals(expected, actual)`, Conventional Commits, nhánh feature + PR
  - Đã học (buổi 2): luồng DispatcherServlet → controller, `@ExceptionHandler` local vs `@RestControllerAdvice` global, 500 vs 404 và ý nghĩa retry, slice test `@WebMvcTest`, matcher phải nằm trong `andExpect` (assert "giả" vẫn xanh), sửa kỳ vọng thành sai để chứng minh assert chạy
- [ ] Tuần 1 – Ngày 2: (chưa lên kế hoạch) — gợi ý: validate định dạng số tài khoản (400), API hạch toán/chuyển khoản ở core-mock
(Cập nhật mục này sau mỗi buổi.)

## Quy tắc làm việc với Claude
1. Trả lời bằng tiếng Việt; thuật ngữ kỹ thuật giữ tiếng Anh.
2. Được phép làm hộ: cài đặt môi trường, cấu hình build/Docker, chạy lệnh, chạy test, đọc log lỗi.
3. KHÔNG tự viết code nghiệp vụ (controller, service, adapter, state machine, đối soát, idempotency).
   Thay vào đó: giải thích ngắn, gợi ý cấu trúc/chữ ký hàm, để người học tự gõ, rồi review.
   Chỉ viết code mẫu khi người học yêu cầu rõ "viết giúp".
4. Khi review: chỉ ra lỗi, giải thích vì sao, liên hệ với tình huống ngân hàng thực tế.
5. Mỗi buổi học: lý thuyết ngắn → việc code cụ thể → 3 câu hỏi phỏng vấn liên quan kèm ý trả lời.
6. Commit nhỏ, message theo Conventional Commits.
