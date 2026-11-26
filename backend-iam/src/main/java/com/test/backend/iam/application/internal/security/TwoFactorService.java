package com.test.backend.iam.application.internal.security;

import com.test.backend.iam.application.internal.outboundservices.hashing.HashingService;
import com.test.backend.iam.domain.model.aggregates.User;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class TwoFactorService {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final String BACKUP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int SECRET_BYTES = 20;
    private static final int TOTP_STEP_SECONDS = 30;
    private static final int BACKUP_CODE_COUNT = 8;

    private final SecureRandom secureRandom = new SecureRandom();
    private final HashingService hashingService;

    public TwoFactorService(HashingService hashingService) {
        this.hashingService = hashingService;
    }

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public String buildOtpAuthUri(String email, String secret) {
        return "otpauth://totp/SafeLab:" + email + "?secret=" + secret + "&issuer=SafeLab";
    }

    public boolean verifyTotp(String base32Secret, String code) {
        if (base32Secret == null || base32Secret.isBlank() || code == null) {
            return false;
        }
        String trimmed = code.trim();
        if (!trimmed.matches("\\d{6}")) {
            return false;
        }
        long currentStep = Instant.now().getEpochSecond() / TOTP_STEP_SECONDS;
        byte[] candidate = trimmed.getBytes(StandardCharsets.US_ASCII);
        for (long offset = -1; offset <= 1; offset++) {
            String expected = generateTotp(base32Secret, currentStep + offset);
            if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), candidate)) {
                return true;
            }
        }
        return false;
    }

    public String generateTotp(String base32Secret, long timeStep) {
        byte[] key = decodeBase32(base32Secret);
        byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format("%06d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute TOTP", e);
        }
    }

    public List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            StringBuilder sb = new StringBuilder(9);
            for (int j = 0; j < 8; j++) {
                if (j == 4) {
                    sb.append('-');
                }
                sb.append(BACKUP_ALPHABET.charAt(secureRandom.nextInt(BACKUP_ALPHABET.length())));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    public String hashBackupCodes(List<String> rawCodes) {
        return rawCodes.stream()
                .map(code -> hashingService.encode(normalizeCode(code)))
                .reduce((a, b) -> a + "," + b)
                .orElse(null);
    }

    public boolean verifyAndConsumeBackupCode(User user, String rawCode) {
        if (user == null || user.getBackupCodesHash() == null || user.getBackupCodesHash().isBlank()
                || rawCode == null || rawCode.isBlank()) {
            return false;
        }
        String normalized = normalizeCode(rawCode);
        List<String> hashes = new ArrayList<>(Arrays.asList(user.getBackupCodesHash().split(",")));
        for (int i = 0; i < hashes.size(); i++) {
            String hash = hashes.get(i).trim();
            if (!hash.isEmpty() && hashingService.matches(normalized, hash)) {
                hashes.remove(i);
                user.setBackupCodesHash(hashes.isEmpty() ? null : String.join(",", hashes));
                return true;
            }
        }
        return false;
    }

    private String normalizeCode(String code) {
        return code.replace("-", "").replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }

    private byte[] decodeBase32(String secret) {
        String cleaned = secret.trim().replace("=", "").replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        byte[] out = new byte[cleaned.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (int i = 0; i < cleaned.length(); i++) {
            int val = BASE32_ALPHABET.indexOf(cleaned.charAt(i));
            if (val < 0) {
                throw new IllegalArgumentException("Invalid Base32 character in TOTP secret");
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out;
    }
}
