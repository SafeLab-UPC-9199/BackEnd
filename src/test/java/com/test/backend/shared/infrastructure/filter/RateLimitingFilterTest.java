package com.test.backend.shared.infrastructure.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitingFilterTest {

    private final RateLimitingFilter filter = new RateLimitingFilter();

    private MockHttpServletResponse run(String uri) throws Exception {
        var request = new MockHttpServletRequest("POST", uri);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    @DisplayName("returns 429 only after 60 requests per minute to the ingestion endpoint (AAA)")
    void doFilter_ShouldReturn429_AfterLimitExceeded() throws Exception {
        // Arrange & Act: first 60 requests pass, the 61st is throttled
        for (int i = 0; i < 60; i++) {
            assertEquals(200, run("/api/v1/telemetry/ingestion").getStatus());
        }

        // Assert
        assertEquals(429, run("/api/v1/telemetry/ingestion").getStatus());
    }

    @Test
    @DisplayName("does not rate-limit other endpoints (AAA)")
    void doFilter_ShouldIgnoreOtherPaths() throws Exception {
        // Act & Assert
        for (int i = 0; i < 100; i++) {
            assertEquals(200, run("/api/v1/auth/login").getStatus());
        }
        // Substring lookalike paths must not be throttled either
        assertEquals(200, run("/api/v1/telemetry/ingestion-backup").getStatus());
    }
}
