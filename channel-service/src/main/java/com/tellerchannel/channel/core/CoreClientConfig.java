package com.tellerchannel.channel.core;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class CoreClientConfig {

    /**
     * Dùng RestClient.Builder do Spring Boot cấp (không tự new) để được cấu hình sẵn
     * Jackson, và sau này là tracing (tự gắn trace ID vào header gửi sang core).
     */
    @Bean
    RestClient coreRestClient(RestClient.Builder builder, CoreProperties props) {
        return builder
                .baseUrl(props.baseUrl())
                .requestFactory(requestFactory(props))
                .build();
    }

    /** JDK HttpClient phân biệt rõ connect timeout (HttpConnectTimeoutException) và read timeout (HttpTimeoutException). */
    static JdkClientHttpRequestFactory requestFactory(CoreProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.readTimeout());
        return factory;
    }
}
