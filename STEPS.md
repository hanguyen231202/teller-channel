# Teller Channel: code theo từng step

Bản hoàn chỉnh nằm ở nhánh `feat/full-implementation`. Step 01 là phần Hà tự làm (Tuần 1 – Ngày 1).
Từ step 02 trở đi, mỗi step là **một commit** theo đúng lộ trình 3 tuần đã lên (step 15 chỉ là tài liệu).
Mỗi step bên dưới gồm: mục tiêu, file cần đọc (theo thứ tự), các quyết định thiết kế và lý do, 3 câu hỏi phỏng vấn.

| Step | Commit | Nội dung |
|---|---|---|
| 02 | `52f5343` | URD, sequence diagram, mã lỗi, validate số tài khoản |
| 03 | `698d91f` | core-mock hạch toán idempotent |
| 04 | `6d7fbe3` | core-mock chaos (chậm / 503) |
| 05 | `76bd170` | khung channel-service, JPA, Flyway, Oracle |
| 06 | `0c6722d` | JWT, vai trò GDV/KSV |
| 07 | `e7e5625` | adapter gọi core: timeout, phân loại lỗi, retry |
| 08 | `06fa081` | vòng đời giao dịch, maker-checker, idempotency, audit |
| 09 | `48dd550` | job đối soát |
| 10 | `44d81bd` | test end-to-end |
| 11 | `9cdd325` | tracing + Zipkin |
| 12 | `2684f80` | sự kiện Kafka |
| 13 | `3a2f22e` | Docker, RCA, kịch bản demo |
| 14 | `0bc78d5` | màn hình demo |
| 15 | commit cuối | STEPS.md, README, CLAUDE.md |

## Cách xem từng step

```bash
git fetch origin                          # tải nhánh mới về máy
git checkout feat/full-implementation     # chuyển sang bản hoàn chỉnh
git log --oneline master..HEAD            # liệt kê các commit (mỗi dòng một step)

# Tạo tag step-02 … step-15 trên máy cho dễ gọi tên (chạy 1 lần trong Git Bash).
# Lệnh đọc dòng "Step NN" trong message của từng commit rồi gắn tag tương ứng.
for c in $(git rev-list master..HEAD); do n=$(git log -1 --format=%b $c | grep -o 'Step [0-9]*' | head -1 | cut -d' ' -f2); git tag -f step-$n $c; done

git show --stat step-08                   # xem step 08 sửa những file nào
git show step-08                          # xem toàn bộ thay đổi của step 08 (dòng + là thêm, - là xóa)
git diff step-07 step-08 -- channel-service/src/main   # chỉ xem phần code main của step 08
git checkout step-05                      # quay về đúng trạng thái code sau step 05 để chạy thử
git checkout feat/full-implementation     # quay lại bản hoàn chỉnh
```

Không muốn tạo tag thì thay `step-08` bằng mã commit trong bảng (vd. `git show 06fa081`).
Trong IntelliJ: tab **Git → Log**, chọn nhánh `feat/full-implementation`, bấm từng commit để xem diff.
Trên GitHub: vào trang Pull Request, tab **Commits**, bấm từng commit.

## Bức tranh tổng

```
GDV/KSV ──JWT──> channel-service :8080 ──RestClient (timeout)──> core-mock :8081
                   │  Oracle (giao dịch kênh + audit, KHÔNG có số dư)
                   │  Job đối soát (@Scheduled) hỏi lại core
                   └─ Kafka (sự kiện), Zipkin (trace)
```

Đọc bản hoàn chỉnh theo thứ tự này là dễ hiểu nhất:

1. `docs/URD.md`, `docs/sequence-diagrams.md`: hệ thống phải làm gì.
2. `core-mock/.../PostingService.java`: core hạch toán thế nào, idempotent ra sao.
3. `channel-service/.../transaction/TransactionStatus.java` rồi `TellerTransaction.java`: vòng đời và quy tắc.
4. `channel-service/.../transaction/TransactionService.java` → `TransactionWriter.java`: luồng duyệt.
5. `channel-service/.../core/CoreBankingAdapter.java`: gọi core, timeout, retry.
6. `channel-service/.../reconciliation/ReconciliationService.java`: đối soát.
7. `docs/rca/RCA-001-core-timeout.md`: câu chuyện để kể khi phỏng vấn.

Đường dẫn rút gọn: `core-mock/...` = `core-mock/src/main/java/com/tellerchannel/coremock/`,
`channel-service/...` = `channel-service/src/main/java/com/tellerchannel/channel/`.

---

## Step 01 · Tuần 1 – Ngày 1 (Hà đã làm)

Dựng repo, core-mock `GET /core/accounts/{accountNo}`, `@RestControllerAdvice` trả ProblemDetail 404 `ACCT_NOT_FOUND`, 5 test.
Các step sau giữ nguyên tên class, package và phong cách của phần này.

---

## Step 02 · Tuần 1 – Ngày 2: URD, sequence diagram, mã lỗi

**Mục tiêu:** viết ra hệ thống phải làm gì trước khi code tiếp, và chốt bảng mã lỗi giữa kênh và core.

**File:**
- `docs/URD.md`: yêu cầu chức năng FR-01…FR-11, phi chức năng, tiêu chí chấp nhận.
- `docs/sequence-diagrams.md`: luồng duyệt, luồng đối soát, vì sao ghi `SENT_TO_CORE` trước, sơ đồ trạng thái (Mermaid, GitHub tự vẽ).
- `docs/error-codes.md`: mã lỗi nào là nghiệp vụ (không retry), mã nào là kỹ thuật.
- `core-mock/.../AccountController.java`: `@Pattern` cho số tài khoản (8–14 chữ số).
- `core-mock/.../GlobalExceptionHandler.java`: bắt `HandlerMethodValidationException` → 400 `INVALID_REQUEST`.

**Quyết định:**
- Kênh dựa vào **mã lỗi dạng chuỗi** (`code`) chứ không đọc câu chữ `detail`, vì câu chữ có thể đổi, còn mã lỗi là hợp đồng.
- Validate ở cửa vào: số tài khoản sai định dạng bị chặn trước khi chạm vào service (test `verifyNoInteractions`).

**Câu hỏi phỏng vấn:**
1. *URD khác SRS thế nào?* URD mô tả người dùng cần gì (ngôn ngữ nghiệp vụ). SRS mô tả hệ thống làm thế nào để đáp ứng (API, dữ liệu, ràng buộc kỹ thuật).
2. *Vì sao trả 400 chứ không 404 khi số tài khoản sai định dạng?* 400 = request sai ngay từ đầu, gửi lại y nguyên vẫn sai. 404 = request hợp lệ nhưng không có dữ liệu.
3. *Sequence diagram dùng để làm gì trong dự án?* Thống nhất thứ tự gọi giữa các bên, phát hiện chỗ thiếu xử lý lỗi (nhánh `alt` timeout) trước khi code.

---

## Step 03 · Tuần 1 – Ngày 3: core-mock hạch toán idempotent

**Mục tiêu:** core-mock nhận lệnh hạch toán Nợ/Có, chống hạch toán trùng theo mã tham chiếu, và cho phép tra cứu lại bút toán.

**File (đọc theo thứ tự):**
- `PostingRequest.java`: lệnh hạch toán + validation (`@DecimalMin` > 0, `@Digits` 2 số lẻ).
- `PostingDirection.java`: `DEBIT` (trừ tiền) / `CREDIT` (cộng tiền).
- `Posting.java`: bút toán đã ghi, có `sameContentAs()`.
- `PostingService.java`: **trọng tâm**.
- `PostingController.java`: `POST /core/postings` (201 mới / 200 trùng), `GET /core/postings/{referenceNo}`.
- `CoreBusinessException.java`, `AccountNotFoundException.java` (giờ kế thừa class chung), `GlobalExceptionHandler.java`.
- `Account.java` thêm `status`, `AccountStatus.java`, `AccountService.java` thêm 2 tài khoản demo và `updateBalance`.
- Test: `PostingServiceTest` (8 test, có test 20 thread rút tiền cùng lúc), `PostingControllerTest`.

**Quyết định:**
- **Idempotent theo `referenceNo`**: gửi lại cùng mã thì trả bút toán cũ, không trừ tiền lần nữa. Cùng mã mà nội dung khác → 409 `DUPLICATE_REFERENCE` (kênh gửi nhầm, phải báo to).
- `synchronized` trên `post()`: "kiểm tra trùng → kiểm tra số dư → ghi" phải nguyên khối. Đây chính là bài toán *check-then-act* Hà đã học. Core thật dùng khóa dòng DB (`SELECT … FOR UPDATE`).
- Gom lỗi nghiệp vụ về một class `CoreBusinessException(status, code, message)` thay vì mỗi lỗi một handler.
- `Clock` được tiêm vào để test cố định thời gian.

**Câu hỏi phỏng vấn:**
1. *Hai thread cùng rút tiền một tài khoản thì hỏng ở đâu?* Cả hai cùng đọc số dư cũ, cùng thấy đủ tiền, cùng ghi → số dư âm hoặc mất một lần trừ. Sửa: khóa (synchronized / `SELECT FOR UPDATE`) hoặc optimistic lock có retry.
2. *Idempotency key do ai sinh?* Bên gọi (kênh) sinh, duy nhất cho mỗi giao dịch, gửi kèm mọi lần thử. Bên nhận (core) lưu lại để nhận ra lần gửi lặp.
3. *Vì sao 422 cho INSUFFICIENT_FUNDS?* Request đúng cú pháp nhưng vi phạm quy tắc nghiệp vụ. Quan trọng hơn mã HTTP là mã lỗi nghiệp vụ đi kèm.

---

## Step 04 · Tuần 1 – Ngày 4: core-mock cố tình hư (chaos)

**Mục tiêu:** cho core-mock chậm hoặc trả 503 theo cấu hình, đổi được lúc đang chạy, để luyện xử lý sự cố ở kênh.

**File:**
- `ChaosSettings.java`: `@ConfigurationProperties("core-mock.chaos")` dạng record, có `@DefaultValue`.
- `ChaosService.java`: giữ cấu hình trong `AtomicReference`, `beforePosting()` / `beforeInquiry()`.
- `ChaosController.java`: `GET/PUT /core/admin/chaos`, `POST /core/admin/chaos/reset`.
- `CoreUnavailableException.java` → 503 `CORE_UNAVAILABLE`.
- `application.properties`: 3 khóa `core-mock.chaos.*`.
- `CoreMockApplication.java`: `@ConfigurationPropertiesScan`.
- Test: `ChaosServiceTest`, `CoreMockApplicationTests` (gọi HTTP thật, port ngẫu nhiên).

**Quyết định:**
- `postingDelayMs` ngủ **trước** khi hạch toán, rồi vẫn hạch toán. Kênh bỏ cuộc lúc 5s nhưng core vẫn trừ tiền lúc 10s: đúng tình huống "timeout mà tiền đã trừ".
- 503 trả **trước** khi xử lý (chưa trừ tiền). Nhưng kênh không biết điều đó, nên vẫn phải coi 5xx là CHƯA RÕ.

**Câu hỏi phỏng vấn:**
1. *`@ConfigurationProperties` khác `@Value` thế nào?* Gom nhóm cấu hình thành object có kiểu, validate được, dễ test; `@Value` lẻ từng giá trị.
2. *Vì sao dùng `AtomicReference`?* Nhiều thread request cùng đọc, admin endpoint ghi. Thay cả object một lần (bất biến) thì thread đọc luôn thấy bộ cấu hình nhất quán.
3. *Chaos engineering là gì?* Chủ động gây lỗi có kiểm soát để kiểm chứng hệ thống chịu được, thay vì chờ sự cố thật.

---

## Step 05 · Tuần 1 – Ngày 5: khung channel-service + Oracle

**Mục tiêu:** module channel-service (port 8080) có entity, schema do Flyway quản lý, chạy được trên H2 lẫn Oracle.

**File:**
- `channel-service/pom.xml`: web, validation, actuator, data-jpa, flyway (+ `flyway-database-oracle`), ojdbc11, h2.
- `src/main/resources/db/migration/V1__create_teller_transaction.sql`: bảng `teller_txn`, `txn_audit_log`. **Không có cột số dư.**
- `application.properties` (H2 chế độ Oracle) và `application-oracle.properties` (Oracle thật).
- `transaction/TransactionStatus.java`, `TransactionType.java`, `TellerTransaction.java`, `TellerTransactionRepository.java`.
- `audit/AuditLog.java`, `AuditLogRepository.java`.
- `docker-compose.yml`: Oracle Free 23 (`gvenzl/oracle-free`).
- Test: `TellerTransactionRepositoryTest` (`@DataJpaTest`, ràng buộc UNIQUE chặn trùng key).

**Quyết định:**
- `ddl-auto=none`: Flyway sở hữu schema. Hibernate tự sinh bảng thì không kiểm soát được kiểu cột, index, và không có lịch sử thay đổi.
- `NUMBER(19,2)` cho tiền, `BigDecimal` trong Java.
- `@Enumerated(EnumType.STRING)`: lưu tên trạng thái. Lưu số thứ tự (ORDINAL) thì chèn thêm trạng thái vào giữa enum là hỏng toàn bộ dữ liệu cũ.
- `@Version`: optimistic lock, hai KSV duyệt cùng lúc thì người sau bị lỗi thay vì ghi đè.
- UNIQUE trên `idempotency_key` và `txn_ref`: DB là chốt chặn cuối cùng, kể cả khi code có race condition.
- `TellerTransaction.initiate(...)`: chỉ có một cách tạo giao dịch, luôn bắt đầu ở `INITIATED`.
- `open-in-view=false`: không giữ kết nối DB suốt request.
- Đã chạy thử với Oracle 23 thật: Flyway migrate thành công, health `db: UP`.

**Câu hỏi phỏng vấn:**
1. *Vì sao dùng Flyway/Liquibase?* Schema có version, chạy giống nhau ở DEV/SIT/UAT/PROD, review được bằng Git, không ai sửa tay DB.
2. *Optimistic và pessimistic lock khác nhau thế nào?* Optimistic: không khóa, kiểm tra `version` lúc ghi, xung đột thì báo lỗi (hợp khi xung đột hiếm). Pessimistic: `SELECT … FOR UPDATE` khóa dòng ngay khi đọc (hợp khi tranh chấp nhiều, như trừ số dư).
3. *Kênh lưu những gì?* Trạng thái giao dịch kênh, người lập/duyệt, mã tham chiếu, kết quả core, audit. Không lưu số dư.

---

## Step 06 · Tuần 2 – Ngày 1: đăng nhập JWT, vai trò GDV/KSV

**Mục tiêu:** đăng nhập lấy JWT, mọi API khác yêu cầu token, phân quyền `TELLER` / `SUPERVISOR`.

**File:**
- `security/SecurityConfig.java`: **trọng tâm**. Filter chain stateless, BCrypt, user demo, JwtEncoder/Decoder HS256, đọc claim `roles`.
- `security/TokenService.java`: phát token (sub, roles, iat, exp, iss).
- `security/AuthController.java`: `POST /api/auth/login`, `GET /api/auth/me`.
- `security/ProblemDetailSecurityHandler.java`: 401/403 trả ProblemDetail (lỗi xảy ra ở filter, trước controller).
- `security/SecurityProperties.java`: khóa ký ≥ 32 byte, `@Validated` để thiếu khóa thì không khởi động.
- `common/ApiException.java`, `common/GlobalExceptionHandler.java`.
- Test: `AuthControllerTest`, `support/AuthHelper.java` (đăng nhập thật trong test).

**Quyết định:**
- Dùng `spring-boot-starter-oauth2-resource-server` để kiểm tra JWT (chữ ký, hạn, issuer) thay vì tự viết filter.
- Tắt CSRF vì token nằm trong header `Authorization`, trình duyệt không tự gửi như cookie.
- Người thao tác luôn lấy từ token (`authentication.getName()`), không bao giờ lấy từ body.
- Sai mật khẩu và sai user trả cùng một lỗi `BAD_CREDENTIALS`, để không lộ user nào tồn tại.

**Câu hỏi phỏng vấn:**
1. *401 khác 403?* 401: chưa xác thực được bạn là ai (thiếu/sai token). 403: biết bạn là ai nhưng bạn không có quyền.
2. *JWT có nhược điểm gì?* Không thu hồi được trước khi hết hạn (cần TTL ngắn + refresh token hoặc danh sách chặn); payload chỉ mã hóa base64, không bí mật.
3. *Vì sao BCrypt chứ không SHA-256?* BCrypt chậm có chủ đích và có salt, dò hàng loạt rất tốn kém. SHA-256 quá nhanh nên dò được hàng tỷ lần mỗi giây.

---

## Step 07 · Tuần 2 – Ngày 2: adapter gọi core

**Mục tiêu:** một lớp duy nhất nói chuyện với core, có timeout, phân loại lỗi và retry an toàn.

**File (đọc theo thứ tự):**
- `core/PostingOutcome.java`: `sealed interface` 4 kết quả `Posted / Rejected / NotSent / Unknown`.
- `core/InquiryOutcome.java`: `Found / NotFound / Unavailable`.
- `core/CoreProperties.java`, `application.properties` mục "Kết nối core".
- `core/CoreClientConfig.java`: RestClient dùng JDK HttpClient, connect timeout 2s, read timeout 5s.
- `core/CoreBankingAdapter.java`: **trọng tâm**, đặc biệt `classify()` và vòng retry của `post()`.
- `account/AccountController.java`: `GET /api/accounts/{no}` hỏi thẳng core.
- Test: `CoreBankingAdapterTest` dùng WireMock (core giả chạy HTTP thật): chậm 1.5s với timeout 0.5s → `Unknown` và **chỉ 1 request**.

**Quyết định (phần quan trọng nhất của project):**

| Tình huống | Có tới core chưa? | Hạch toán (ghi) | Tra cứu (đọc) |
|---|---|---|---|
| 2xx | Rồi, thành công | `Posted` | `Found` |
| 4xx + code | Rồi, bị từ chối | `Rejected`, không retry | `NotFound` nếu `POSTING_NOT_FOUND` |
| Không kết nối được (`ConnectException`, connect timeout, DNS) | **Chắc chắn chưa** | retry, hết lượt → `NotSent` | retry |
| Read timeout | **Không biết** | `Unknown`, **không retry** | retry |
| 5xx | **Không biết** | `Unknown` | retry |

- `sealed interface` + `switch`: thêm một loại kết quả mới mà quên xử lý thì compiler báo lỗi.
- Tầng nghiệp vụ không thấy exception HTTP nào: adapter dịch hết sang `PostingOutcome`.
- Dùng `RestClient.Builder` do Spring Boot cấp (không tự `new`) để step 11 tự có tracing.

**Câu hỏi phỏng vấn:**
1. *Connect timeout khác read timeout?* Connect: thời gian bắt tay TCP; hết hạn nghĩa là chưa gửi được gì. Read: chờ phản hồi sau khi đã gửi; hết hạn nghĩa là không biết bên kia đã xử lý chưa.
2. *Khi nào được retry?* Lỗi kỹ thuật với thao tác đọc, hoặc khi chắc chắn chưa gửi. Không retry lỗi nghiệp vụ. Lệnh ghi bị timeout chỉ retry nếu bên nhận idempotent; ở đây chọn đối soát thay vì retry.
3. *Không đặt timeout thì sao?* Mỗi request treo giữ một thread Tomcat; core chậm kéo dài là hết thread, kênh treo toàn bộ (lỗi dây chuyền). Đây cũng là lý do người ta thêm circuit breaker.

---

## Step 08 · Tuần 2 – Ngày 3: vòng đời giao dịch, maker-checker, idempotency, audit

**Mục tiêu:** GDV lập → gửi duyệt → KSV duyệt/từ chối → gửi core → SUCCESS / FAILED / UNKNOWN, mọi bước có audit.

**File (đọc theo thứ tự):**
- `transaction/TransactionStatus.java`: `canTransitionTo()` là bảng chuyển trạng thái.
- `transaction/TellerTransaction.java`: `submit / approve / reject / markSuccess / markFailed / markUnknown`, kiểm tra maker ≠ checker.
- `audit/AuditService.java`: `@Transactional(propagation = MANDATORY)`.
- `transaction/TransactionWriter.java`: mỗi method một transaction ngắn; `applyPostingOutcome` dùng `switch` trên sealed interface.
- `transaction/TransactionService.java`: **trọng tâm**, `create()` (idempotency) và `approve()` (3 bước).
- `transaction/TransactionController.java`: API + `@PreAuthorize` theo vai trò.
- `CreateTransactionRequest.java`, `TransactionResponse.java`, `common/PageResponse.java`.
- Test: `TellerTransactionTest` (unit thuần), `TransactionServiceTest` (DB thật + core mock), `TransactionControllerTest` (HTTP + phân quyền).

**Quyết định:**
- **Quy tắc nằm trong entity**, service không set status trực tiếp. Muốn biết "được đổi trạng thái nào" chỉ cần đọc một chỗ.
- **Luồng duyệt 3 bước:** (1) transaction 1 commit `SENT_TO_CORE`; (2) gọi core **ngoài transaction**; (3) transaction 2 ghi kết quả. Không giữ kết nối/khóa DB trong 5s chờ core, và app sập giữa chừng thì DB vẫn ghi lại "có thể đã tới core".
- **Vì sao tách `TransactionWriter` khỏi `TransactionService`:** `@Transactional` hoạt động qua proxy. Gọi `this.method()` trong cùng class sẽ bỏ qua proxy nên không có transaction. Tách bean thì gọi qua proxy.
- **Audit `MANDATORY`:** audit buộc phải chạy chung transaction với việc đổi trạng thái, cùng commit hoặc cùng rollback.
- **Idempotency 3 lớp:** tìm theo key → nếu chưa có thì INSERT → nếu hai request đồng thời cùng INSERT, UNIQUE constraint chặn request sau, ta đọc lại bản đã có.
- `applyPostingOutcome` bỏ qua nếu trạng thái không còn là `SENT_TO_CORE` (đối soát đã chốt trước), không ghi đè.

**Câu hỏi phỏng vấn:**
1. *Maker-checker cài đặt thế nào?* Vai trò lấy từ JWT (`@PreAuthorize`), entity kiểm tra checker ≠ maker, mỗi bước ghi audit trong cùng transaction.
2. *Vì sao gọi `@Transactional` method từ chính class đó không có tác dụng?* Spring bọc bean bằng proxy; lời gọi nội bộ `this.x()` không đi qua proxy nên không mở transaction.
3. *Exception checked có làm rollback không?* Mặc định không: Spring chỉ rollback `RuntimeException` và `Error`. Muốn rollback checked thì `@Transactional(rollbackFor = Exception.class)`.

---

## Step 09 · Tuần 2 – Ngày 4: job đối soát

**Mục tiêu:** tự động chốt giao dịch `UNKNOWN` (và `SENT_TO_CORE` bị treo do sập) bằng cách hỏi lại core.

**File:**
- `reconciliation/ReconciliationService.java`: **trọng tâm**. `reconcileBatch()`, `reconcileNow()`, `requireSafeMinAge()`.
- `reconciliation/ReconciliationJob.java`: `@Scheduled(fixedDelay)`, bật/tắt bằng property.
- `reconciliation/ReconciliationProperties.java`, `application.properties` mục "Đối soát".
- `TellerTransactionRepository.findReconcileCandidates(...)`: JPQL.
- `TransactionWriter.applyInquiryOutcome(...)`: Found khớp → SUCCESS; Found lệch số tiền → giữ UNKNOWN, ghi `RECONCILE_MISMATCH`; NotFound → FAILED `NOT_POSTED_AT_CORE`; Unavailable → tăng `reconcileAttempts`.
- `TransactionController`: `POST /api/transactions/{id}/reconcile` cho KSV.
- Test: `ReconciliationServiceTest` dùng `support/MutableClock` để tua thời gian thay vì chờ 30s.

**Quyết định:**
- Đối soát chỉ **HỎI**, không bao giờ gửi lại lệnh hạch toán.
- `min-age` (30s) phải lớn hơn thời gian chờ core tệ nhất của luồng duyệt. Nếu không, job có thể hỏi lúc core còn đang xử lý, nhận "chưa có" và chốt FAILED sai. App **từ chối khởi động** nếu cấu hình sai (fail fast).
- Lệch số tiền thì không tự chốt: đây là việc cho con người.
- Quá `max-attempts` thì dừng tự động, chờ xử lý tay.
- Một giao dịch lỗi không làm dừng cả lô (try/catch trong vòng lặp).

**Câu hỏi phỏng vấn:**
1. *Core không trả lời sau 30 giây thì kênh làm gì? Khách đứng quầy được báo gì?* Giao dịch CHƯA RÕ, GDV không chi tiền, báo khách chờ. KSV có thể bấm đối soát ngay. Job hỏi core bằng mã tham chiếu rồi chốt.
2. *Chạy 2 instance thì job đối soát có vấn đề gì?* Cả hai cùng chạy, có thể hỏi core trùng. Dùng ShedLock hoặc `SELECT … FOR UPDATE SKIP LOCKED`. Ở đây `@Version` vẫn ngăn ghi đè.
3. *`fixedDelay` khác `fixedRate`?* `fixedDelay` đếm từ lúc lượt trước **xong**, không chồng lượt. `fixedRate` đếm từ lúc lượt trước **bắt đầu**, lượt chậm có thể chồng nhau.

---

## Step 10 · Tuần 2 – Ngày 5: test end-to-end

**Mục tiêu:** chứng minh cả luồng chạy đúng qua HTTP với adapter thật.

**File:**
- `e2e/TellerFlowEndToEndTest.java`: core WireMock chậm 1.5s, kênh timeout 0.5s → `UNKNOWN` → KSV đối soát → `SUCCESS`, và `core.verify(1, post…)`: core chỉ nhận **đúng 1 lệnh**.
- `transaction/IdempotencyConcurrencyTest.java`: 10 thread cùng gửi một Idempotency-Key → 1 giao dịch.

Tổng: core-mock 23 test, channel-service 58 test, tất cả xanh.

**Câu hỏi phỏng vấn:**
1. *Test pyramid?* Nhiều unit test (nhanh, rẻ), ít hơn integration test, rất ít end-to-end (chậm, dễ vỡ). Project này: entity/adapter là unit, service là integration với DB, e2e cho luồng chính.
2. *`@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest` khác nhau?* Hai cái đầu là slice (chỉ tầng web / chỉ JPA), nhanh. `@SpringBootTest` dựng cả ứng dụng.
3. *Vì sao dùng WireMock thay vì mock adapter?* Mock adapter thì không kiểm tra được timeout, header, JSON thật. WireMock là server HTTP thật nên test được đúng hành vi mạng.

---

## Step 11 · Tuần 3 – Ngày 1: tracing + Zipkin

**Mục tiêu:** một trace ID đi xuyên channel → core, xem trên Zipkin, log có `[traceId-spanId]`.

**File:**
- `pom.xml` của cả hai module: `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`.
- `application.properties`: `management.tracing.sampling.probability`, `management.zipkin.tracing.endpoint`.
- `common/TraceIdResponseFilter.java`: trả header `X-Trace-Id` cho client.
- `docker-compose.yml`: thêm Zipkin (http://localhost:9411).

**Quyết định:** không phải viết code truyền trace: RestClient lấy từ `RestClient.Builder` của Boot đã được gắn sẵn. Trong lần chạy thật, trace của nút "Duyệt" có cả span channel (5.09s) lẫn span core-mock (10.06s).

**Câu hỏi phỏng vấn:**
1. *Trace, span là gì?* Trace là toàn bộ hành trình một request; span là một chặng (một lần gọi HTTP, một query). Span con trỏ về span cha.
2. *Trace ID truyền sang service khác thế nào?* Qua header HTTP (chuẩn W3C `traceparent` hoặc B3), service nhận đọc header và tạo span con.
3. *Vì sao production không lấy mẫu 100%?* Tốn tài nguyên và lưu trữ; thường lấy 1–10%, cộng lấy mẫu toàn bộ request lỗi.

---

## Step 12 · Tuần 3 – Ngày 2: sự kiện Kafka

**Mục tiêu:** khi giao dịch kết thúc, phát sự kiện để hệ thống khác (SMS, báo cáo) tự xử lý.

**File:**
- `events/TransactionCompletedEvent.java`.
- `TransactionWriter.publishIfFinal()`: phát `ApplicationEvent` trong transaction.
- `events/TransactionEventListener.java`: `@TransactionalEventListener(AFTER_COMMIT)`.
- `events/TransactionEventSink.java` + `KafkaTransactionEventSink.java` (bật bằng `KAFKA_ENABLED=true`) + `LoggingTransactionEventSink.java` (mặc định).
- `application.properties` mục Kafka (`acks=all`, `enable.idempotence=true`), `docker-compose.yml` thêm Kafka KRaft.
- Test: `TransactionEventsTest` (FAILED/REJECTED phát 1 lần, UNKNOWN không phát), `KafkaTransactionEventSinkTest`.

**Quyết định:**
- Chỉ phát **sau commit**: phát trong transaction mà rollback thì hệ thống khác gửi SMS cho giao dịch không tồn tại.
- Key = `transactionRef` để các sự kiện của cùng giao dịch vào cùng partition, giữ thứ tự.
- Giới hạn đã biết: app sập đúng lúc giữa commit và gửi Kafka thì mất sự kiện. Cách triệt để là **Outbox pattern**: ghi sự kiện vào bảng `outbox` trong cùng transaction, một job khác đọc bảng và đẩy lên Kafka.

**Câu hỏi phỏng vấn:**
1. *At-most-once, at-least-once, exactly-once?* Tối đa một lần (có thể mất), ít nhất một lần (có thể trùng), đúng một lần (khó, cần idempotent producer + transaction hoặc consumer idempotent).
2. *Consumer nhận trùng sự kiện thì sao?* Consumer phải idempotent: lưu `transactionRef` đã xử lý, gặp lại thì bỏ qua.
3. *Outbox pattern giải quyết gì?* Bài toán "ghi DB và gửi message" không cùng transaction. Ghi cả hai vào DB trong một transaction, rồi đẩy message sau.

---

## Step 13 · Tuần 3 – Ngày 3: Docker, diễn tập sự cố, RCA

**Mục tiêu:** chạy cả hệ thống bằng một lệnh, gây sự cố thật và viết RCA từ số liệu thật.

**File:**
- `core-mock/Dockerfile`, `channel-service/Dockerfile`: multi-stage (Maven build → JRE), chạy bằng user không phải root.
- `docker-compose.yml`: Oracle + Kafka + Zipkin + core-mock + channel-service (`depends_on: service_healthy`).
- `docs/rca/RCA-001-core-timeout.md`: **đọc kỹ, dùng để kể khi phỏng vấn**.
- `docs/demo.md`: lệnh chạy thử từng tình huống, có giải thích.

**Đã chạy thật:** core chậm 10s → kênh báo `UNKNOWN` sau 5s → số dư core đã giảm 50 triệu → khoảng 40 giây sau, job đối soát chốt `SUCCESS` → sự kiện lên Kafka. Core chỉ có một bút toán. Tắt hẳn core-mock rồi duyệt → `FAILED` `CORE_UNREACHABLE`.

**Câu hỏi phỏng vấn:**
1. *Kể về một sự cố em đã phân tích.* Dùng RCA-001: hiện tượng, dòng thời gian, tác động, nguyên nhân gốc (5 why), xử lý, phòng ngừa.
2. *Multi-stage build để làm gì?* Image cuối chỉ có JRE + jar, nhỏ hơn và không chứa mã nguồn, Maven.
3. *Vì sao không chạy container bằng root?* Lỗ hổng trong app bị khai thác thì kẻ tấn công không có quyền root trong container.

---

## Step 14 · Tuần 3 – Ngày 4: màn hình demo

**Mục tiêu:** có giao diện để demo maker-checker mà không cần gõ curl.

**File:** `channel-service/src/main/resources/static/index.html` (HTML + JavaScript thuần), `StaticPageTest.java`.
Mở http://localhost:8080, đăng nhập `gdv01` lập và gửi duyệt, đăng nhập `ksv01` duyệt. Trạng thái CHƯA RÕ hiện "KHÔNG CHI TIỀN" và nút "Đối soát ngay".

**Quyết định:** không dùng Angular để giữ trọng tâm vào backend. Idempotency-Key sinh bằng `crypto.randomUUID()` và **giữ nguyên** đến khi lập thành công, nên bấm lại sau lỗi mạng không tạo giao dịch trùng.

---

## Những gì có thể làm thêm (nếu được hỏi "nếu có thêm thời gian")

- Circuit breaker (Resilience4j) quanh `CoreBankingAdapter`.
- Outbox pattern cho sự kiện Kafka.
- ShedLock cho job đối soát khi chạy nhiều instance.
- Hạn mức duyệt nhiều cấp (số tiền lớn cần KSV cấp cao hơn).
- Metric `teller_transactions_unknown` + cảnh báo Grafana.
- Angular cho màn hình GDV/KSV.
