package com.test.backend.iam.application.internal.security;

import com.test.backend.iam.domain.model.aggregates.User;
import com.test.backend.iam.infrastructure.hashing.bcrypt.services.HashingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TwoFactorServiceTest {

    private TwoFactorService twoFactorService;

    @BeforeEach
    void setUp() {
        twoFactorService = new TwoFactorService(new HashingServiceImpl());
    }

    @Test
    void generatesValidBase32SecretAndOtpAuthUri() {
        String secret = twoFactorService.generateSecret();
        assertNotNull(secret);
        assertTrue(secret.matches("^[A-Z2-7]{32}$"));

        String uri = twoFactorService.buildOtpAuthUri("admin@safelab.com", secret);
        assertEquals("otpauth://totp/SafeLab:admin@safelab.com?secret=" + secret + "&issuer=SafeLab", uri);
    }

    @Test
    void verifiesTotpWithinOneStepWindowAndRejectsExpired() {
        String secret = twoFactorService.generateSecret();
        long currentStep = Instant.now().getEpochSecond() / 30L;

        assertTrue(twoFactorService.verifyTotp(secret, twoFactorService.generateTotp(secret, currentStep)));
        assertTrue(twoFactorService.verifyTotp(secret, twoFactorService.generateTotp(secret, currentStep - 1)));
        assertTrue(twoFactorService.verifyTotp(secret, twoFactorService.generateTotp(secret, currentStep + 1)));
        assertFalse(twoFactorService.verifyTotp(secret, twoFactorService.generateTotp(secret, currentStep - 3)));
        assertFalse(twoFactorService.verifyTotp(secret, "abcdef"));
    }

    @Test
    void generatesEightBackupCodesAndConsumesUsedCodeOnce() {
        List<String> codes = twoFactorService.generateBackupCodes();
        assertEquals(8, codes.size());

        User user = new User("user@safelab.com", "hashed", "User");
        user.setBackupCodesHash(twoFactorService.hashBackupCodes(codes));

        String first = codes.get(0);
        assertTrue(twoFactorService.verifyAndConsumeBackupCode(user, first.toLowerCase().replace("-", "")));
        assertFalse(twoFactorService.verifyAndConsumeBackupCode(user, first));
        assertTrue(twoFactorService.verifyAndConsumeBackupCode(user, codes.get(1)));
    }
}
