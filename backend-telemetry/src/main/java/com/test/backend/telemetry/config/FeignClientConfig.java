package com.test.backend.telemetry.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "com.test.backend.telemetry.infrastructure.clients")
public class FeignClientConfig {
}
