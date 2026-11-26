package com.test.backend.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = GatewayApplication.class, properties = "gateway.session-check.enabled=false")
class GatewayRoutesTest {

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private GlobalCorsProperties globalCorsProperties;

    @Test
    void securityRouteForwardsToIam() {
        Route securityRoute = routeLocator.getRoutes()
                .filter(route -> "security-route".equals(route.getId()))
                .blockFirst();

        assertNotNull(securityRoute);
        assertEquals(URI.create("http://safelab-backend-iam:8084"), securityRoute.getUri());

        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/security/sessions"));
        assertTrue(Boolean.TRUE.equals(Mono.from(securityRoute.getPredicate().apply(exchange)).block()));
    }

    @Test
    void corsAllowsPatchMethod() {
        var corsConfig = globalCorsProperties.getCorsConfigurations().get("/api/v1/**");
        assertNotNull(corsConfig);
        assertTrue(corsConfig.getAllowedMethods().contains(HttpMethod.PATCH.name()));
    }
}
