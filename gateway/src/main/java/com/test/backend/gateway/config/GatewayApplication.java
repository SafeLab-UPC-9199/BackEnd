package com.test.backend.gateway.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// scanBasePackages: without it Spring scans only com.test.backend.gateway.config
// (this class's package) and would silently drop beans in sibling packages such
// as ...gateway.security — which would disable the edge JWT filter (ADR-006).
@SpringBootApplication(scanBasePackages = "com.test.backend.gateway")
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
