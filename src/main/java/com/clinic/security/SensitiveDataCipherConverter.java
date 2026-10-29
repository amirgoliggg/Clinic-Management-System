package com.clinic.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * رمزنگاری شفاف فیلدهای حساس دیتابیس (Data-at-Rest Encryption) با استاندارد AES-256-GCM.
 * هر فیلد با یک IV تصادفی ۱۲ بایتی منحصر‌به‌فرد و تگ اصالت‌سنجی ۱۲۸ بیتی در دیتابیس ذخیره می‌شود.
 */
@Converter
public class SensitiveDataCipherConverter implements AttributeConverter<String, String> {

    private static final String ENCRYPTION_PREFIX = "ENC:GCM:";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(HmacKeyProvider.getDatabaseEncryptionKey256(), "AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);

            return ENCRYPTION_PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("خطا در رمزنگاری AES-256-GCM فیلد حساس دیتابیس.", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        if (!dbData.startsWith(ENCRYPTION_PREFIX)) {
            // سازگاری با رکوردهای قدیمی قبل از فعال‌سازی رمزنگاری
            return dbData;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(dbData.substring(ENCRYPTION_PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(decoded);

            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(HmacKeyProvider.getDatabaseEncryptionKey256(), "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new SecurityException("خطای امنیتی: رمزگشایی AES-256-GCM ناموفق بود؛ احتمال دست‌کاری مستقیم در فایل دیتابیس!", e);
        }
    }
}