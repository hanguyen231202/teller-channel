package com.tellerchannel.channel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Màn hình demo mở được mà không cần token (token chỉ cần khi gọi API). */
@SpringBootTest(properties = "teller.reconciliation.enabled=false")
@AutoConfigureMockMvc
class StaticPageTest {

    @Autowired
    MockMvc mvc;

    @Test
    void indexPage_isPublic() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Teller Channel")));
    }
}
