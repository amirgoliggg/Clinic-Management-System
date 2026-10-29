package com.clinic.dto;

import com.clinic.enums.PatientStatus;
import com.clinic.models.Patient;
import com.clinic.models.Receipt;
import com.clinic.models.Staff;
import com.clinic.models.Admin;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.Map;

public final class ClinicalDTOs {

    private ClinicalDTOs() {
        // Utility holder class
    }

    public record PatientRegistrationRequest(
            @NotBlank(message = "کد ملی بیمار الزامی است.")
            @Pattern(regexp = "^[0-9]{1,20}$", message = "کد ملی باید فقط شامل اعداد باشد.")
            String nationalId,

            @NotBlank(message = "نام و نام خانوادگی بیمار الزامی است.")
            @Size(min = 2, max = 100, message = "نام بیمار باید بین ۲ تا ۱۰۰ کاراکتر باشد.")
            String fullName,

            @NotNull(message = "سن بیمار الزامی است.")
            @Min(value = 0, message = "سن بیمار نمی‌تواند منفی باشد.")
            @Max(value = 150, message = "سن وارد شده معتبر نیست.")
            Integer age,

            @NotBlank(message = "شماره تماس الزامی است.")
            @Pattern(regexp = "^[+0-9\\-\\s()]{3,25}$", message = "فرمت شماره تماس معتبر نیست.")
            String contactNumber,

            Patient.BloodType bloodType
    ) {
    }

    public record PatientResponse(
            String patientId,
            String nationalId,
            String fullName,
            Integer age,
            String contactNumber,
            Patient.BloodType bloodType,
            PatientStatus currentStatus,
            BigDecimal pendingBill,
            String assignedDoctorName
    ) {
        public static PatientResponse fromEntity(Patient p) {
            String docName = (p.getAssignedDoctor() != null)
                    ? p.getAssignedDoctor().getFullName()
                    : "تعیین نشده";
            return new PatientResponse(
                    p.getPatientId(),
                    p.getNationalId(),
                    p.getFullName(),
                    p.getAge(),
                    p.getContactNumber(),
                    p.getBloodType(),
                    p.getCurrentStatus(),
                    p.getPendingBill(),
                    docName
            );
        }
    }

    public record ConsultationRequest(
            @NotBlank(message = "کد ملی بیمار الزامی است.")
            String patientNationalId,

            @NotBlank(message = "تشخیص بالینی الزامی است.")
            @Size(max = 2000, message = "شرح تشخیص بیش از حد مجاز طولانی است.")
            String diagnosis,

            @NotBlank(message = "نسخه دارویی الزامی است.")
            @Size(max = 2000, message = "شرح نسخه بیش از حد مجاز طولانی است.")
            String prescription,

            @Size(max = 4000, message = "یادداشت پزشکی بیش از حد مجاز طولانی است.")
            String notes
    ) {
    }

    public record TriageRequest(
            @NotBlank(message = "کد ملی بیمار الزامی است.")
            String patientNationalId,

            @NotBlank(message = "ثبت فشار خون الزامی است.")
            @Pattern(regexp = "^[0-9]{2,3}/[0-9]{2,3}$", message = "فرمت فشار خون باید به صورت سیستول/دیاستول (مثلاً 120/80) باشد.")
            String bloodPressure,

            @NotNull(message = "دمای بدن بیمار الزامی است.")
            @DecimalMin(value = "30.0", message = "دمای بدن خارج از محدوده معتبر بالینی است.")
            @DecimalMax(value = "45.0", message = "دمای بدن خارج از محدوده معتبر بالینی است.")
            Double bodyTemperatureCelsius,

            @NotNull(message = "ضربان قلب بیمار الزامی است.")
            @Min(value = 20, message = "ضربان قلب خارج از محدوده معتبر بالینی است.")
            @Max(value = 250, message = "ضربان قلب خارج از محدوده معتبر بالینی است.")
            Integer heartRateBpm,

            @Size(max = 1000)
            String chiefComplaint,

            @Size(max = 2000)
            String nursingNotes
    ) {
    }

    public record NursingCareRequest(
            @NotBlank(message = "کد ملی بیمار الزامی است.")
            String patientNationalId,

            @NotBlank(message = "شرح خدمت پرستاری الزامی است.")
            @Size(max = 1000)
            String careProcedure,

            @DecimalMin(value = "0.00", message = "هزینه خدمت پرستاری نمی‌تواند منفی باشد.")
            BigDecimal serviceFee,

            @Size(max = 2000)
            String careNotes
    ) {
    }

    public record AmendRecordRequest(
            @NotBlank(message = "تشخیص اصلاح‌شده الزامی است.")
            @Size(max = 2000)
            String updatedDiagnosis,

            @NotBlank(message = "نسخه اصلاح‌شده الزامی است.")
            @Size(max = 2000)
            String updatedPrescription,

            @NotBlank(message = "علت اصلاح پرونده الزامی است.")
            @Size(max = 1000)
            String reason
    ) {
    }

    public record SettlementRequest(
            @NotBlank(message = "کد ملی بیمار الزامی است.")
            String patientNationalId,

            @NotNull(message = "مبلغ تسویه پایه الزامی است.")
            @DecimalMin(value = "0.01", message = "مبلغ تسویه باید بیشتر از صفر باشد.")
            BigDecimal amount,

            @DecimalMin(value = "0.00", message = "مبلغ تخفیف نمی‌تواند منفی باشد.")
            BigDecimal discountAmount,

            @DecimalMin(value = "0.00", message = "مبلغ مالیات نمی‌تواند منفی باشد.")
            BigDecimal taxAmount,

            @NotNull(message = "روش پرداخت الزامی است.")
            Receipt.PaymentMethod method,

            @Size(max = 500)
            String description,

            @Size(max = 64)
            String idempotencyKey
    ) {
    }

    public record RefundRequest(
            @NotBlank(message = "شماره رسید اصلی الزامی است.")
            String originalReceiptId,

            @NotBlank(message = "کد ملی بیمار الزامی است.")
            String patientNationalId,

            @NotNull(message = "مبلغ استرداد الزامی است.")
            @DecimalMin(value = "0.01", message = "مبلغ استرداد باید بیشتر از صفر باشد.")
            BigDecimal refundAmount,

            @NotBlank(message = "علت استرداد وجه الزامی است.")
            @Size(max = 500)
            String reason
    ) {
    }

    public record FinancialSummaryResponse(
            BigDecimal grossRevenue,
            BigDecimal totalDiscounts,
            BigDecimal totalTax,
            BigDecimal totalRefunded,
            BigDecimal netCollectedRevenue,
            BigDecimal pendingReceivables,
            long totalPaidReceipts,
            long totalRefundedReceipts,
            boolean allReceiptsCryptographicallyIntact,
            Map<String, BigDecimal> revenueByPaymentMethod
    ) {
        public FinancialSummaryResponse {
            revenueByPaymentMethod = Map.copyOf(revenueByPaymentMethod);
        }
    }

    public record StaffSummaryResponse(
            String staffId,
            String username,
            String fullName,
            String nationalId,
            String role,
            boolean active,
            String employmentStatus
    ) {
        public static StaffSummaryResponse fromEntity(Staff s) {
            return new StaffSummaryResponse(
                    s.getStaffId(),
                    s.getUsername(),
                    s.getFullName(),
                    s.getNationalId(),
                    s instanceof Admin admin ? admin.getAdminRole().name() : s.getRole(),
                    s.isActive(),
                    s.getEmploymentStatus() != null ? s.getEmploymentStatus().name() : "ACTIVE"
            );
        }
    }
}