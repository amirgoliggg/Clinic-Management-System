package com.clinic.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * مدیریت متمرکز و امن کلیدهای رمزنگاری HMAC-SHA256 سامانه.
 */
public final class HmacKeyProvider {

    private static final int MIN_SECRET_LENGTH_BYTES = 32;
    private static final Path LOCAL_KEYSTORE_DIR = Paths.get("data", ".keys");

    private static final Set<String> COMPROMISED_STATIC_SECRETS = Set.of(
            "CLINIC_AUDIT_CHAIN_HMAC_SECRET_KEY_2026",
            "CLINIC_MEDICAL_RECORD_HMAC_SECRET_KEY_2026",
            "CLINIC_CORE_RECEIPT_HMAC_SECRET_KEY_2026"
    );

    public enum KeyDomain {
        AUDIT_CHAIN("CLINIC_AUDIT_HMAC_SECRET", "clinic.security.hmac.audit-secret", "audit_hmac.key"),
        MEDICAL_RECORD("CLINIC_MR_HMAC_SECRET", "clinic.security.hmac.medical-record-secret", "medical_record_hmac.key"),
        RECEIPT("CLINIC_RECEIPT_HMAC_SECRET", "clinic.security.hmac.receipt-secret", "receipt_hmac.key");

        private final String envVarName;
        private final String propertyName;
        private final String keyFileName;

        KeyDomain(String envVarName, String propertyName, String keyFileName) {
            this.envVarName = envVarName;
            this.propertyName = propertyName;
            this.keyFileName = keyFileName;
        }

        public String getEnvVarName() {
            return envVarName;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public String getKeyFileName() {
            return keyFileName;
        }
    }

    private static final ConcurrentMap<KeyDomain, byte[]> FALLBACK_LOCAL_KEYS = new ConcurrentHashMap<>();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private HmacKeyProvider() {
    }

    public static byte[] getAuditKey() {
        return resolveKey(KeyDomain.AUDIT_CHAIN);
    }

    public static byte[] getMedicalRecordKey() {
        return resolveKey(KeyDomain.MEDICAL_RECORD);
    }

    public static byte[] getReceiptKey() {
        return resolveKey(KeyDomain.RECEIPT);
    }

    public static byte[] resolveKey(KeyDomain domain) {
        String configured = readConfiguredSecret(domain);
        if (configured != null && !configured.isBlank()) {
            validateSecretStrength(domain, configured.trim());
            return configured.trim().getBytes(StandardCharsets.UTF_8);
        }

        if (isStrictKeyModeEnabled()) {
            throw new IllegalStateException(String.format(
                    "خطای امنیتی بحرانی: کلید رمزنگاری برای دامنه [%s] تنظیم نشده است. " +
                            "در محیط عملیاتی تنظیم متغیر محیطی '%s' یا پراپرتی '%s' (حداقل ۳۲ کاراکتر) الزامی است.",
                    domain.name(), domain.getEnvVarName(), domain.getPropertyName()
            ));
        }

        return FALLBACK_LOCAL_KEYS.computeIfAbsent(domain, HmacKeyProvider::loadOrGenerateLocalKey);
    }

    public static byte[] getDatabaseEncryptionKey256() {
        try {
            java.security.MessageDigest sha256 = java.security.MessageDigest.getInstance("SHA-256");
            return sha256.digest(getMedicalRecordKey());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable for AES key derivation", e);
        }
    }
    private static byte[] loadOrGenerateLocalKey(KeyDomain domain) {
        try {
            Files.createDirectories(LOCAL_KEYSTORE_DIR);
            Path keyFile = LOCAL_KEYSTORE_DIR.resolve(domain.getKeyFileName());
            if (Files.exists(keyFile)) {
                String existing = Files.readString(keyFile, StandardCharsets.UTF_8).trim();
                if (existing.length() >= MIN_SECRET_LENGTH_BYTES && !COMPROMISED_STATIC_SECRETS.contains(existing)) {
                    return existing.getBytes(StandardCharsets.UTF_8);
                }
            }
            byte[] randomBytes = new byte[32];
            SECURE_RANDOM.nextBytes(randomBytes);
            String generatedHex = HexFormat.of().formatHex(randomBytes);
            Files.writeString(keyFile, generatedHex, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            return generatedHex.getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            byte[] randomBytes = new byte[32];
            SECURE_RANDOM.nextBytes(randomBytes);
            return HexFormat.of().formatHex(randomBytes).getBytes(StandardCharsets.UTF_8);
        }
    }

    private static String readConfiguredSecret(KeyDomain domain) {
        String fromProp = System.getProperty(domain.getPropertyName());
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp;
        }
        String fromEnv = System.getenv(domain.getEnvVarName());
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return null;
    }

    private static void validateSecretStrength(KeyDomain domain, String secret) {
        if (COMPROMISED_STATIC_SECRETS.contains(secret)) {
            throw new IllegalStateException(String.format(
                    "خطای امنیتی: استفاده از کلید پیش‌فرض و افشاشده قدیمی برای [%s] مجاز نیست.",
                    domain.name()
            ));
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_LENGTH_BYTES) {
            throw new IllegalStateException(String.format(
                    "خطای امنیتی: کلید HMAC برای [%s] بیش از حد کوتاه است. حداقل طول مجاز %d بایت (۲۵۶ بیت) است.",
                    domain.name(), MIN_SECRET_LENGTH_BYTES
            ));
        }
    }

    private static boolean isStrictKeyModeEnabled() {
        String strictProp = System.getProperty("clinic.security.strict-hmac-keys",
                System.getenv().getOrDefault("CLINIC_STRICT_HMAC_KEYS", "false"));
        if ("true".equalsIgnoreCase(strictProp.trim())) {
            return true;
        }
        String activeProfiles = System.getProperty("spring.profiles.active",
                System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", ""));
        for (String profile : activeProfiles.split(",")) {
            String clean = profile.trim().toLowerCase();
            if ("prod".equals(clean) || "production".equals(clean)) {
                return true;
            }
        }
        return false;
    }
}