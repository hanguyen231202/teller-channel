package com.tellerchannel.channel.core;

import com.tellerchannel.channel.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.util.Map;

/**
 * Mọi chi tiết "nói chuyện với core" nằm ở đây. Tầng nghiệp vụ chỉ nhận PostingOutcome / InquiryOutcome,
 * không phải biết HTTP, timeout hay exception của thư viện.
 *
 * Quy tắc retry:
 *  - Hạch toán (ghi): chỉ retry khi CHẮC CHẮN chưa gửi được (không kết nối). Timeout / 5xx → UNKNOWN, không retry mù.
 *  - Tra cứu (đọc): retry mọi lỗi kỹ thuật, vì đọc lại không làm thay đổi gì.
 *  - Lỗi nghiệp vụ (4xx): không bao giờ retry.
 */
@Component
public class CoreBankingAdapter {

    private static final Logger log = LoggerFactory.getLogger(CoreBankingAdapter.class);

    private final RestClient coreRestClient;
    private final CoreProperties props;

    public CoreBankingAdapter(RestClient coreRestClient, CoreProperties props) {
        this.coreRestClient = coreRestClient;
        this.props = props;
    }

    public PostingOutcome post(CorePostingRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return coreRestClient.post().uri("/core/postings").body(request)
                        .exchange((req, res) -> toPostingOutcome(res), true);
            } catch (RestClientException ex) {
                FailureKind kind = classify(ex);
                if (kind == FailureKind.NOT_SENT && attempt < props.postingMaxAttempts()) {
                    log.warn("Posting {} not sent (attempt {}/{}): {}. Retrying",
                            request.referenceNo(), attempt, props.postingMaxAttempts(), rootMessage(ex));
                    backoff();
                    continue;
                }
                if (kind == FailureKind.NOT_SENT) {
                    log.error("Posting {} could not reach core after {} attempts", request.referenceNo(), attempt);
                    return new PostingOutcome.NotSent(rootMessage(ex));
                }
                log.error("Posting {} outcome UNKNOWN ({}): {}", request.referenceNo(), kind, rootMessage(ex));
                return new PostingOutcome.Unknown(kind + ": " + rootMessage(ex));
            }
        }
    }

    public InquiryOutcome findPosting(String referenceNo) {
        String lastError = "";
        for (int attempt = 1; attempt <= props.inquiryMaxAttempts(); attempt++) {
            try {
                InquiryOutcome outcome = coreRestClient.get().uri("/core/postings/{ref}", referenceNo)
                        .exchange((req, res) -> toInquiryOutcome(res), true);
                if (!(outcome instanceof InquiryOutcome.Unavailable u)) {
                    return outcome;
                }
                lastError = u.reason();
            } catch (RestClientException ex) {
                lastError = classify(ex) + ": " + rootMessage(ex);
            }
            log.warn("Inquiry {} failed (attempt {}/{}): {}", referenceNo, attempt, props.inquiryMaxAttempts(), lastError);
            if (attempt < props.inquiryMaxAttempts()) {
                backoff();
            }
        }
        return new InquiryOutcome.Unavailable(lastError);
    }

    /** Tra cứu tài khoản cho GDV. Lỗi nghiệp vụ / kỹ thuật được đổi thành ApiException để trả cho client. */
    public CoreAccount getAccount(String accountNo) {
        for (int attempt = 1; ; attempt++) {
            try {
                return coreRestClient.get().uri("/core/accounts/{no}", accountNo)
                        .exchange((req, res) -> {
                            HttpStatusCode status = res.getStatusCode();
                            if (status.is2xxSuccessful()) {
                                return res.bodyTo(CoreAccount.class);
                            }
                            CoreError err = readError(res);
                            if (status.is4xxClientError()) {
                                throw new ApiException(HttpStatus.valueOf(status.value()), err.code(), err.message());
                            }
                            throw new CoreServerError(err);
                        }, true);
            } catch (RestClientException | CoreServerError ex) {
                if (attempt >= props.inquiryMaxAttempts()) {
                    log.error("Account inquiry {} failed after {} attempts: {}", accountNo, attempt, rootMessage(ex));
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CORE_UNAVAILABLE",
                            "Core banking is not responding, please try again");
                }
                backoff();
            }
        }
    }

    // ----------------------------------------------------------------------------------------

    private PostingOutcome toPostingOutcome(ClientHttpResponse res) throws IOException {
        HttpStatusCode status = res.getStatusCode();
        if (status.is2xxSuccessful()) {
            return new PostingOutcome.Posted(readBody(res, CorePosting.class));
        }
        CoreError err = readError(res);
        if (status.is4xxClientError()) {
            return new PostingOutcome.Rejected(err.code(), err.message());
        }
        // 5xx: có thể core lỗi trước khi xử lý, cũng có thể sau khi đã hạch toán. Không đoán.
        return new PostingOutcome.Unknown("HTTP " + status.value() + " " + err.code());
    }

    private InquiryOutcome toInquiryOutcome(ClientHttpResponse res) throws IOException {
        HttpStatusCode status = res.getStatusCode();
        if (status.is2xxSuccessful()) {
            return new InquiryOutcome.Found(readBody(res, CorePosting.class));
        }
        CoreError err = readError(res);
        if (status.value() == 404 && "POSTING_NOT_FOUND".equals(err.code())) {
            return new InquiryOutcome.NotFound();
        }
        return new InquiryOutcome.Unavailable("HTTP " + status.value() + " " + err.code());
    }

    private static <T> T readBody(ClientHttpResponse res, Class<T> type) {
        return ((RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse) res).bodyTo(type);
    }

    /** Đọc ProblemDetail của core để lấy "code". Body hỏng thì vẫn trả được code chung. */
    private static CoreError readError(ClientHttpResponse res) {
        try {
            ProblemDetail pd = readBody(res, ProblemDetail.class);
            Map<String, Object> extra = pd == null ? null : pd.getProperties();
            Object code = extra == null ? null : extra.get("code");
            return new CoreError(code == null ? "CORE_ERROR" : code.toString(), pd == null ? "" : pd.getDetail());
        } catch (RuntimeException e) {
            return new CoreError("CORE_ERROR", "Unreadable error body");
        }
    }

    /**
     * Phân loại lỗi kỹ thuật. Đây là quyết định quan trọng nhất của adapter:
     * NOT_SENT thì an toàn để thử lại; TIMEOUT / IO_ERROR thì có thể core đã nhận lệnh.
     */
    static FailureKind classify(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof HttpConnectTimeoutException
                    || t instanceof ConnectException
                    || t instanceof UnknownHostException
                    || t instanceof NoRouteToHostException) {
                return FailureKind.NOT_SENT;
            }
            if (t instanceof java.net.http.HttpTimeoutException
                    || t instanceof java.net.SocketTimeoutException) {
                return FailureKind.TIMEOUT;
            }
        }
        return FailureKind.IO_ERROR;
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : " " + root.getMessage());
    }

    private void backoff() {
        try {
            Thread.sleep(props.retryBackoff().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    enum FailureKind { NOT_SENT, TIMEOUT, IO_ERROR }

    private record CoreError(String code, String message) {
    }

    /** Core trả 5xx khi tra cứu tài khoản: coi như lỗi kỹ thuật để retry. */
    private static final class CoreServerError extends RuntimeException {
        CoreServerError(CoreError err) {
            super(err.code() + " " + err.message());
        }
    }
}
