package com.tellerchannel.coremock;

import org.springframework.http.HttpStatus;

/**
 * Lỗi nghiệp vụ của core: gửi lại y nguyên thì kết quả vẫn vậy, nên phía kênh KHÔNG retry.
 * Mỗi lỗi mang một mã dạng chuỗi (code) để kênh phân loại mà không phải đọc câu chữ.
 */
public class CoreBusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public CoreBusinessException(HttpStatus status, String code, String message) {
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
