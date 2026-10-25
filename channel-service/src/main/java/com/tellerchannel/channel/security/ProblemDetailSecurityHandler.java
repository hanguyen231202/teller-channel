package com.tellerchannel.channel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Lỗi 401/403 xảy ra trong filter của Spring Security, TRƯỚC khi tới controller,
 * nên @RestControllerAdvice không bắt được. Class này trả cùng định dạng ProblemDetail.
 */
@Component
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemDetailSecurityHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 401: chưa đăng nhập hoặc token sai/hết hạn. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Missing or invalid token");
    }

    /** 403: đã đăng nhập nhưng không đủ quyền. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission for this action");
    }

    private void write(HttpServletResponse response, HttpStatus status, String code, String detail) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("code", code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), pd);
    }
}
