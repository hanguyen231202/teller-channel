package com.tellerchannel.channel.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Đăng nhập thật qua /api/auth/login và trả về header "Bearer ..." để dùng trong test. */
public final class AuthHelper {

    public static final String PASSWORD = "Teller@123";

    private AuthHelper() {
    }

    public static String bearer(MockMvc mvc, ObjectMapper json, String username) throws Exception {
        String body = json.writeValueAsString(Map.of("username", username, "password", PASSWORD));
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(response).get("accessToken").asText();
    }
}
