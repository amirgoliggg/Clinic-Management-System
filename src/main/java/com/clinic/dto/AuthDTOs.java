package com.clinic.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public final class AuthDTOs {

    private AuthDTOs() {
    }

    public record LoginRequest(
            @NotBlank(message = "نام کاربری الزامی است.")
            @Size(min = 1, max = 64, message = "طول نام کاربری نامعتبر است.")
            String username,

            @NotBlank(message = "رمز عبور الزامی است.")
            @Size(min = 1, max = 128, message = "طول رمز عبور نامعتبر است.")
            String password
    ) {
    }

    public record UserProfileResponse(
            String username,
            String fullName,
            String role,
            String nationalId
    ) {
    }

    public record RoleDefinitionDTO(
            @NotBlank(message = "کد انگلیسی نقش الزامی است.")
            @Pattern(regexp = "^[A-Z0-9_]{2,32}$", message = "کد نقش باید فقط شامل حروف بزرگ انگلیسی، اعداد و زیرخط (_) باشد.")
            String roleCode,

            @NotBlank(message = "عنوان فارسی نقش الزامی است.")
            @Size(min = 2, max = 80, message = "عنوان فارسی نقش باید بین ۲ تا ۸۰ کاراکتر باشد.")
            String displayName,

            @NotBlank(message = "سطح دسترسی پایه الزامی است.")
            @Pattern(
                    regexp = "^(RECEPTIONIST|NURSE|DOCTOR|CASHIER|FINANCIAL_ADMIN|HR_ADMIN|AUDITOR)$",
                    message = "سطح دسترسی پایه معتبر نیست."
            )
            String baseClearance,

            boolean builtIn
    ) {
    }

    public record RegisterRequest(
            @NotBlank(message = "نام کاربری الزامی است.")
            @Size(min = 3, max = 50, message = "نام کاربری باید بین ۳ تا ۵۰ کاراکتر باشد.")
            @Pattern(regexp = "^[a-zA-Z0-9._\\-]+$", message = "نام کاربری فقط می‌تواند شامل حروف انگلیسی، اعداد و نقطه/خط تیره باشد.")
            String username,

            @NotBlank(message = "رمز عبور الزامی است.")
            @Size(min = 6, max = 100, message = "رمز عبور باید حداقل ۶ کاراکتر باشد.")
            String password,

            @NotBlank(message = "کد ملی پرسنل الزامی است.")
            @Pattern(regexp = "^[a-zA-Z0-9\\-_]{4,64}$", message = "فرمت کد ملی یا شناسه پرسنلی معتبر نیست.")
            String nationalId,

            @NotBlank(message = "نام و نام خانوادگی الزامی است.")
            @Size(min = 2, max = 100, message = "نام کامل باید بین ۲ تا ۱۰۰ کاراکتر باشد.")
            String fullName,

            @NotNull(message = "سن پرسنل الزامی است.")
            @Min(value = 18, message = "حداقل سن قانونی برای استخدام پرسنل ۱۸ سال است.")
            @Max(value = 100, message = "سن وارد شده معتبر نیست.")
            Integer age,

            @NotBlank(message = "شماره تماس الزامی است.")
            @Pattern(regexp = "^[+0-9\\-\\s()]{3,30}$", message = "فرمت شماره تماس معتبر نیست.")
            String contactNumber,

            @DecimalMin(value = "0.00", message = "حقوق پایه نمی‌تواند منفی باشد.")
            BigDecimal baseSalary,

            @NotBlank(message = "نقش سازمانی الزامی است.")
            @Pattern(
                    regexp = "^[A-Za-z0-9_]{2,40}$",
                    message = "فرمت نقش پرسنلی انتخاب‌شده معتبر نیست."
            )
            String role,

            String specialization,
            String medicalLicenseNumber,

            @DecimalMin(value = "0.01", message = "سقف مجاز تراکنش باید بزرگ‌تر از صفر باشد.")
            BigDecimal maxTransactionLimit
    ) {
    }
}