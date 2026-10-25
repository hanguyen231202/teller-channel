package com.tellerchannel.coremock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CoreMockApplicationTests {

	@Autowired
	TestRestTemplate rest;

	@Test
	void contextLoads() {
	}

	/** Chạy thật qua HTTP: hạch toán, gửi trùng, rồi tra cứu bằng mã tham chiếu. */
	@Test
	void postThenReplayThenInquire() {
		Map<String, Object> body = Map.of("referenceNo", "IT-REF-1", "accountNo", "12345678",
				"direction", "CREDIT", "amount", 1000, "currency", "VND");

		ResponseEntity<Map> first = rest.postForEntity("/core/postings", body, Map.class);
		ResponseEntity<Map> second = rest.postForEntity("/core/postings", body, Map.class);
		ResponseEntity<Map> inquiry = rest.getForEntity("/core/postings/IT-REF-1", Map.class);

		assertEquals(HttpStatus.CREATED, first.getStatusCode());
		assertEquals(HttpStatus.OK, second.getStatusCode());
		assertEquals(first.getBody().get("postingId"), inquiry.getBody().get("postingId"));
	}
}
