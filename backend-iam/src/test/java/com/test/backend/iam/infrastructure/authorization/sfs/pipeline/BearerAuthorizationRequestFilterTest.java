package com.test.backend.iam.infrastructure.authorization.sfs.pipeline;

import com.test.backend.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.test.backend.iam.infrastructure.tokens.jwt.BearerTokenService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BearerAuthorizationRequestFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesWithGatewayEmailHeaderWhenBearerIsAbsent() throws Exception {
        var tokenService = mock(BearerTokenService.class);
        var userDetailsService = mock(UserDetailsService.class);
        var filter = new BearerAuthorizationRequestFilter(tokenService, userDetailsService);

        var req = new MockHttpServletRequest();
        req.addHeader("X-User-Email", "sec@safelab.com");
        when(userDetailsService.loadUserByUsername("sec@safelab.com"))
                .thenReturn(new UserDetailsImpl("sec@safelab.com", "pw", List.of()));

        filter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("sec@safelab.com", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void invalidBearerDoesNotFallBackToGatewayHeader() throws Exception {
        var tokenService = mock(BearerTokenService.class);
        var userDetailsService = mock(UserDetailsService.class);
        var filter = new BearerAuthorizationRequestFilter(tokenService, userDetailsService);

        var req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer bad-token");
        req.addHeader("X-User-Email", "spoof@safelab.com");
        when(tokenService.getBearerTokenFrom(req)).thenReturn("bad-token");
        when(tokenService.validateToken("bad-token")).thenReturn(false);

        filter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
