package com.test.backend.iam.infrastructure.authorization.sfs.pipeline;

import com.test.backend.iam.infrastructure.authorization.sfs.model.UsernamePasswordAuthenticationTokenBuilder;
import com.test.backend.iam.infrastructure.tokens.jwt.BearerTokenService;
import com.test.backend.shared.infrastructure.security.RequestIdentity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class BearerAuthorizationRequestFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(BearerAuthorizationRequestFilter.class);

    private final BearerTokenService tokenService;
    private final UserDetailsService userDetailsService;

    public BearerAuthorizationRequestFilter(BearerTokenService tokenService, UserDetailsService userDetailsService) {
        this.tokenService = tokenService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            var token = tokenService.getBearerTokenFrom(request);
            String email = null;
            if (token != null) {
                if (tokenService.validateToken(token)) {
                    email = tokenService.getEmailFromToken(token);
                }
            } else if (SecurityContextHolder.getContext().getAuthentication() == null) {
                var gatewayEmail = request.getHeader(RequestIdentity.HEADER_USER_EMAIL);
                if (gatewayEmail != null && !gatewayEmail.isBlank()) {
                    email = gatewayEmail.trim();
                }
            }
            if (email != null) {
                var userDetails = userDetailsService.loadUserByUsername(email);
                var authentication = UsernamePasswordAuthenticationTokenBuilder.build(userDetails, request);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }
}
