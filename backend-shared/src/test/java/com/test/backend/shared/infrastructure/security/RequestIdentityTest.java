package com.test.backend.shared.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestIdentityTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void sessionIdReturnsNullWhenHeaderOrRequestAbsent() {
        assertNull(RequestIdentity.sessionId());

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        assertNull(RequestIdentity.sessionId());

        var blankReq = new MockHttpServletRequest();
        blankReq.addHeader(RequestIdentity.HEADER_SESSION_ID, "   ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(blankReq));
        assertNull(RequestIdentity.sessionId());
    }

    @Test
    void sessionIdReadsXSessionIdHeader() {
        var request = new MockHttpServletRequest();
        request.addHeader(RequestIdentity.HEADER_SESSION_ID, " sess-123 ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertEquals("sess-123", RequestIdentity.sessionId());
    }
}
