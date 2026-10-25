package com.tellerchannel.channel.common;

import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ của kênh, mang HTTP status + mã lỗi dạng chuỗi (xem docs/error-codes.md). */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
