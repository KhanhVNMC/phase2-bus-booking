package dev.janitor.busbooking.impl.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import dev.janitor.busbooking.repository.IVerificationCodes;

public class HmacVerificationCodes implements IVerificationCodes {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int CODE_SPACE = 1_000_000;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec digestKey;

    public HmacVerificationCodes(String base64Secret) {
        this.digestKey = new SecretKeySpec(HmacJwtTokens.decodeKey(base64Secret, "EMAIL_OTP_SECRET"), HMAC_ALGORITHM);
    }

    @Override
    public String generate() {
        return String.format("%06d", random.nextInt(CODE_SPACE));
    }

    @Override
    public String digest(String email, String code) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(digestKey);
            byte[] digest = mac.doFinal((email + ":" + code).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available on this JVM", e);
        }
    }
}
