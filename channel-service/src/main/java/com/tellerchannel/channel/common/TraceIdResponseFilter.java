package com.tellerchannel.channel.common;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Trả traceId trong header X-Trace-Id của mọi response.
 * Khi GDV báo lỗi, chỉ cần đọc mã này là tìm được log của cả channel lẫn core trên Zipkin/Kibana.
 *
 * ObjectProvider: trong test, Spring Boot tắt tracing nên có thể không có bean Tracer.
 */
@Component
public class TraceIdResponseFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";

    private final ObjectProvider<Tracer> tracer;

    public TraceIdResponseFilter(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Tracer t = tracer.getIfAvailable();
        Span span = t == null ? null : t.currentSpan();
        if (span != null) {
            response.setHeader(HEADER, span.context().traceId());
        }
        chain.doFilter(request, response);
    }
}
