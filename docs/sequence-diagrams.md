# Sequence diagram

GitHub hiển thị trực tiếp các sơ đồ Mermaid bên dưới.

## 1. Luồng chính: rút tiền có maker-checker

```mermaid
sequenceDiagram
  autonumber
  actor G as GDV (maker)
  participant C as channel-service
  participant DB as Oracle (kênh)
  actor K as KSV (checker)
  participant R as core-mock

  G->>C: POST /api/auth/login
  C-->>G: JWT (roles=TELLER)
  G->>C: POST /api/transactions (Idempotency-Key)
  C->>DB: INSERT status=INITIATED + audit
  C-->>G: 201 INITIATED
  G->>C: POST /api/transactions/{id}/submit
  C->>DB: UPDATE status=PENDING_APPROVAL + audit
  K->>C: POST /api/transactions/{id}/approve
  C->>C: kiểm tra role SUPERVISOR, checker ≠ maker
  C->>DB: UPDATE status=SENT_TO_CORE + audit (COMMIT trước khi gọi core)
  C->>R: POST /core/postings (referenceNo)
  alt Core trả lời kịp
    R-->>C: 201 Posted (postingId)
    C->>DB: UPDATE status=SUCCESS + audit
  else Core từ chối (4xx + code)
    R-->>C: 422 INSUFFICIENT_FUNDS
    C->>DB: UPDATE status=FAILED + audit
  else Timeout / 5xx
    C->>DB: UPDATE status=UNKNOWN + audit
  end
  C-->>K: trạng thái cuối
```

## 2. Đối soát giao dịch CHƯA RÕ

```mermaid
sequenceDiagram
  autonumber
  participant J as ReconciliationJob (@Scheduled)
  participant DB as Oracle (kênh)
  participant R as core-mock

  J->>DB: tìm UNKNOWN / SENT_TO_CORE cũ hơn 30s
  loop từng giao dịch
    J->>R: GET /core/postings/{referenceNo}
    alt 200 tìm thấy bút toán
      J->>DB: SUCCESS (resolved by reconciliation) + audit
    else 404 POSTING_NOT_FOUND
      J->>DB: FAILED (NOT_POSTED_AT_CORE) + audit
    else Core vẫn lỗi
      J->>DB: tăng reconcile_attempts, giữ UNKNOWN
    end
  end
```

## 3. Vì sao ghi `SENT_TO_CORE` trước khi gọi core

```mermaid
sequenceDiagram
  participant C as channel-service
  participant DB as Oracle
  participant R as core
  C->>DB: COMMIT SENT_TO_CORE
  C->>R: hạch toán
  Note over C: app sập ở đây
  Note over DB: Khởi động lại vẫn thấy SENT_TO_CORE<br/>→ job đối soát hỏi lại core.<br/>Nếu chưa ghi gì, ta sẽ tưởng giao dịch chưa từng gửi.
```

## 4. Vòng đời trạng thái

```mermaid
stateDiagram-v2
  [*] --> INITIATED
  INITIATED --> PENDING_APPROVAL: GDV gửi duyệt
  PENDING_APPROVAL --> REJECTED: KSV từ chối
  PENDING_APPROVAL --> SENT_TO_CORE: KSV duyệt
  SENT_TO_CORE --> SUCCESS: core OK
  SENT_TO_CORE --> FAILED: core từ chối / không kết nối được
  SENT_TO_CORE --> UNKNOWN: timeout / 5xx
  UNKNOWN --> SUCCESS: đối soát thấy đã hạch toán
  UNKNOWN --> FAILED: đối soát thấy chưa hạch toán
```

| Code | Tiếng Việt |
|---|---|
| `INITIATED` | KHỞI TẠO |
| `PENDING_APPROVAL` | CHỜ DUYỆT |
| `REJECTED` | TỪ CHỐI |
| `SENT_TO_CORE` | ĐÃ GỬI CORE |
| `SUCCESS` | THÀNH CÔNG |
| `FAILED` | THẤT BẠI |
| `UNKNOWN` | CHƯA RÕ |
