package dto;

import java.math.BigDecimal;

public class AuthDTOs {

    public record LoginRequest(String username, String password) {}

    public record UserProfileResponse(
            String username,
            String fullName,
            String role,
            String nationalId
    ) {}

    public record RegisterRequest(
            String username,
            String password,
            String nationalId,
            String fullName,
            int age,
            String contactNumber,
            BigDecimal baseSalary,
            String role, // SUPER_ADMIN, DOCTOR, NURSE, CASHIER, RECEPTIONIST
            String specialization,       // برای پزشک
            String medicalLicenseNumber, // برای پزشک
            BigDecimal maxTransactionLimit // برای صندوق‌دار
    ) {}
}