package com.tellerchannel.channel.transaction;

import com.tellerchannel.channel.audit.AuditLog;
import com.tellerchannel.channel.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Người thao tác lấy từ JWT (authentication.getName()), không bao giờ lấy từ body do client gửi. */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    /** 201 khi tạo mới, 200 khi là request lặp (cùng Idempotency-Key). */
    @PostMapping
    @PreAuthorize("hasRole('TELLER')")
    public ResponseEntity<TransactionResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody CreateTransactionRequest request,
            Authentication auth) {
        TransactionService.CreateResult result = service.create(request, auth.getName(), idempotencyKey);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(TransactionResponse.from(result.transaction()));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('TELLER')")
    public TransactionResponse submit(@PathVariable Long id, Authentication auth) {
        return TransactionResponse.from(service.submit(id, auth.getName()));
    }

    /** Trả về trạng thái cuối: SUCCESS / FAILED / UNKNOWN. UNKNOWN nghĩa là GDV CHƯA được chi tiền. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public TransactionResponse approve(@PathVariable Long id, Authentication auth) {
        return TransactionResponse.from(service.approve(id, auth.getName()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public TransactionResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request,
                                      Authentication auth) {
        return TransactionResponse.from(service.reject(id, auth.getName(), request.reason()));
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable Long id) {
        return TransactionResponse.from(service.get(id));
    }

    /** Ví dụ: KSV xem danh sách chờ duyệt: GET /api/transactions?status=PENDING_APPROVAL */
    @GetMapping
    public PageResponse<TransactionResponse> list(@RequestParam(required = false) TransactionStatus status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.of(service.list(status, pageable), TransactionResponse::from);
    }

    @GetMapping("/{id}/audit")
    public List<AuditEntry> audit(@PathVariable Long id) {
        return service.auditTrail(id).stream().map(AuditEntry::from).toList();
    }

    public record RejectRequest(@NotBlank @Size(max = 255) String reason) {
    }

    public record AuditEntry(String action, TransactionStatus fromStatus, TransactionStatus toStatus,
                             String actor, String detail, java.time.Instant at) {
        static AuditEntry from(AuditLog a) {
            return new AuditEntry(a.getAction(), a.getFromStatus(), a.getToStatus(), a.getActor(),
                    a.getDetail(), a.getCreatedAt());
        }
    }
}
