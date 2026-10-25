package com.tellerchannel.channel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tellerchannel.channel.support.AuthHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void login_validCredentials_returnsTokenWithRoles() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ksv01\",\"password\":\"Teller@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.roles[0]").value("SUPERVISOR"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"gdv01\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void me_withoutToken_returns401ProblemDetail() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void me_withTamperedToken_returns401() throws Exception {
        String token = AuthHelper.bearer(mvc, json, "gdv01");
        mvc.perform(get("/api/auth/me").header("Authorization", token + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withToken_returnsUserFromJwt() throws Exception {
        String token = AuthHelper.bearer(mvc, json, "gdv01");
        mvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("gdv01"))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_TELLER"));
    }
}
