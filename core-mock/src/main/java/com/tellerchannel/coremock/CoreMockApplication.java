package com.tellerchannel.coremock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CoreMockApplication {

	public static void main(String[] args) {
		SpringApplication.run(CoreMockApplication.class, args);
	}

}
