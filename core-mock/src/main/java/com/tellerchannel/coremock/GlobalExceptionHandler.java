package com.tellerchannel.coremock;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(AccountNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Account not found");
        pd.setProperty("code", "ACCT_NOT_FOUND");
        return pd;
    }

    /**
     * Lỗi validate tham số (@PathVariable, @RequestParam) từ Spring 6.1+.
     * 400 = lỗi phía người gọi, gửi lại y nguyên cũng vô ích nên không retry.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleInvalidParameter(HandlerMethodValidationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request parameter");
        pd.setTitle("Invalid request");
        pd.setProperty("code", "INVALID_REQUEST");
        return pd;
    }
}
