package com.custom.postprocessing.config;

import java.time.Duration;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateWithTimeoutConfig {
	static final int TIMEOUT = 1800000;

	@Bean
	RestTemplate restTemplateWithConnectReadTimeout() {
		return new RestTemplateBuilder().setConnectTimeout(Duration.ofMillis(TIMEOUT))
				.setReadTimeout(Duration.ofMillis(TIMEOUT)).build();
	}

	@Bean
	RestTemplate restTemplateWithConnectTimeout() {
		return new RestTemplateBuilder().setConnectTimeout(Duration.ofMillis(TIMEOUT)).build();
	}

	@Bean
	RestTemplate restTemplateTimeoutWithRequestFactory() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(TIMEOUT);
		requestFactory.setReadTimeout(TIMEOUT);

		return new RestTemplate(requestFactory);
	}
}