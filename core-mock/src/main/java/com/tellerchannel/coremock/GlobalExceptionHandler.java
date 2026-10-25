package com.tellerchannel.coremock;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Mọi lỗi nghiệp vụ (ACCT_NOT_FOUND, INSUFFICIENT_FUNDS...) đi qua một chỗ duy nhất. */
    @ExceptionHandler(CoreBusinessException.class)
    public ProblemDetail handleBusiness(CoreBusinessException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        pd.setTitle(ex.getStatus().getReasonPhrase());
        pd.setProperty("code", ex.getCode());
        return pd;
    }

    /** Lỗi kỹ thuật (giả lập): 503 báo cho kênh biết đây là lỗi có thể thử lại / cần đối soát. */
    @ExceptionHandler(CoreUnavailableException.class)
    public ProblemDetail handleUnavailable(CoreUnavailableException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        pd.setTitle("Service unavailable");
        pd.setProperty("code", "CORE_UNAVAILABLE");
        return pd;
    }

    /**
     * Lỗi validate tham số (@PathVariable, @RequestParam) từ Spring 6.1+.
     * 400 = lỗi phía người gọi, gửi lại y nguyên cũng vô ích nên không retry.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleInvalidParameter(HandlerMethodValidationException ex) {
        return invalidRequest("Invalid request parameter");
    }

    /** Lỗi validate body (@Valid @RequestBody). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidBody(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return invalidRequest(detail);
    }

    /** JSON hỏng hoặc sai kiểu (vd. direction = "ABC"). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return invalidRequest("Malformed request body");
    }

    private ProblemDetail invalidRequest(String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setTitle("Invalid request");
        pd.setProperty("code", "INVALID_REQUEST");
        return pd;
    }
}
