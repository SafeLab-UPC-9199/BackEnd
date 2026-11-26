package com.test.backend.iam.application.internal.security;

import com.test.backend.iam.domain.model.aggregates.User;
import com.test.backend.iam.domain.model.exceptions.AccountLockedException;
import com.test.backend.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountProtectionService {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final ConcurrentHashMap<String, IpAttemptState> ipAttempts = new ConcurrentHashMap<>();

    @Value("${security.password.max-age-days:90}")
    private int passwordMaxAgeDays = 90;

    public AccountProtectionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void checkNotLocked(User user, String ipAddress) {
        Instant now = Instant.now();
        if (ipAddress != null && !ipAddress.isBlank()) {
            IpAttemptState state = ipAttempts.get(ipAddress);
            if (state != null && state.lockedUntil != null && state.lockedUntil.isAfter(now)) {
                throw new AccountLockedException("Too many failed login attempts from this IP", state.lockedUntil);
            }
        }
        if (user != null && user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new AccountLockedException("Account temporarily locked due to too many failed login attempts", user.getLockedUntil());
        }
    }

    public void recordFailedAttempt(User user, String ipAddress) {
        Instant now = Instant.now();
        if (user != null) {
            if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(now)) {
                user.setFailedAttempts(0);
                user.setLockedUntil(null);
            }
            int nextAttempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(nextAttempts);
            if (nextAttempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(now.plus(LOCK_DURATION));
            }
            userRepository.save(user);
        }
        if (ipAddress != null && !ipAddress.isBlank()) {
            ipAttempts.compute(ipAddress, (key, existing) -> {
                IpAttemptState state = (existing == null || (existing.lockedUntil != null && !existing.lockedUntil.isAfter(now)))
                        ? new IpAttemptState()
                        : existing;
                state.attempts++;
                if (state.attempts >= MAX_FAILED_ATTEMPTS) {
                    state.lockedUntil = now.plus(LOCK_DURATION);
                }
                return state;
            });
        }
    }

    public void resetFailedAttempts(User user, String ipAddress) {
        if (user != null && (user.getFailedAttempts() > 0 || user.getLockedUntil() != null)) {
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
        if (ipAddress != null && !ipAddress.isBlank()) {
            ipAttempts.remove(ipAddress);
        }
    }

    public boolean isAtRisk(User user) {
        return user != null && user.isPasswordExpired(passwordMaxAgeDays);
    }

    public int getPasswordMaxAgeDays() {
        return passwordMaxAgeDays;
    }

    private static final class IpAttemptState {
        int attempts;
        Instant lockedUntil;
    }
}
