package com.clinic.service;

import com.clinic.dto.ClinicalDTOs.FinancialSummaryResponse;
import com.clinic.enums.PatientStatus;
import com.clinic.exceptions.EntityNotFoundException;
import com.clinic.exceptions.InsufficientPermissionException;
import com.clinic.models.Admin;
import com.clinic.models.Cashier;
import com.clinic.models.Patient;
import com.clinic.models.Receipt;
import com.clinic.repository.FinancialRepository;
import com.clinic.repository.PatientRepository;
import com.clinic.system.SystemClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class BillingService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    private static final String POS_RECEIPT_PREFIX = "POS-";
    private static final String REFUND_RECEIPT_PREFIX = "REF-";
    private static final String DEFAULT_DOCTOR_ID = "DOC-GENERAL";

    private final FinancialRepository financialRepository;
    private final PatientRepository patientRepository;
    private final AuditService auditService;

    public BillingService(FinancialRepository financialRepository,
                          PatientRepository patientRepository,
                          AuditService auditService) {
        this.financialRepository = financialRepository;
        this.patientRepository = patientRepository;
        this.auditService = auditService;
    }

    // ==================== Settlement Operations ====================

    @Transactional
    public Receipt processPatientSettlement(Cashier cashier,
                                            String patientNationalId,
                                            BigDecimal amount,
                                            Receipt.PaymentMethod method,
                                            String description) {
        return processPatientSettlement(
                cashier, patientNationalId, amount,
                BigDecimal.ZERO, BigDecimal.ZERO, method, description, null
        );
    }

    @Transactional
    public Receipt processPatientSettlement(Cashier cashier,
                                            String patientNationalId,
                                            BigDecimal amount,
                                            BigDecimal discountAmount,
                                            BigDecimal taxAmount,
                                            Receipt.PaymentMethod method,
                                            String description,
                                            String idempotencyKey) {
        if (cashier == null || !cashier.isActive()) {
            throw InsufficientPermissionException.accessDenied("UNKNOWN", "POS_SETTLEMENT");
        }
        if (patientNationalId == null || patientNationalId.isBlank()) {
            throw new IllegalArgumentException("کد ملی بیمار الزامی است.");
        }
        Objects.requireNonNull(method, "روش پرداخت نمی‌تواند خالی باشد.");

        BigDecimal safeSubtotal = normalizeMoney(amount);
        BigDecimal safeDiscount = normalizeMoney(discountAmount);
        BigDecimal safeTax = normalizeMoney(taxAmount);

        validateSettlementAmounts(safeSubtotal, safeDiscount, safeTax);

        // ۱. بررسی دقیق کلید یکتای تراکنش (Idempotency) به همراه تطبیق اثر انگشت درخواست
        String normalizedKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey.trim() : null;
        if (normalizedKey != null) {
            Optional<Receipt> existingOpt = findExistingByIdempotencyKey(normalizedKey);
            if (existingOpt.isPresent()) {
                Receipt existing = existingOpt.get();
                verifyIdempotentRequestMatch(existing, patientNationalId.trim(), safeSubtotal, safeDiscount, safeTax, method);
                return existing;
            }
        }

        // ۲. دریافت رکورد بیمار با قفل بدبینانه (Pessimistic Write Lock) برای ایمنی در برابر هم‌زمانی
        Patient patient = patientRepository.findByNationalIdForUpdate(patientNationalId.trim())
                .orElseThrow(() -> EntityNotFoundException.patientNotFound(patientNationalId));

        // A concurrent request may have committed while we waited for the patient lock.
        if (normalizedKey != null) {
            Optional<Receipt> existingOpt = findExistingByIdempotencyKey(normalizedKey);
            if (existingOpt.isPresent()) {
                Receipt existing = existingOpt.get();
                verifyIdempotentRequestMatch(existing, patient.getNationalId(), safeSubtotal, safeDiscount, safeTax, method);
                return existing;
            }
        }

        BigDecimal netPayable = safeSubtotal.add(safeTax).subtract(safeDiscount).setScale(MONEY_SCALE, MONEY_ROUNDING);
        if (netPayable.compareTo(cashier.getMaxSingleTransactionLimit()) > 0) {
            throw new SecurityException(String.format(
                    "مبلغ خالص تراکنش (%s$) از سقف مجاز صندوق‌دار (%s$) بیشتر است.",
                    netPayable.toPlainString(), cashier.getMaxSingleTransactionLimit().toPlainString()
            ));
        }

        BigDecimal currentPendingBill = normalizeMoney(patient.getPendingBill());
        if (currentPendingBill.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("این بیمار هیچ بدهی بازی برای تسویه ندارد.");
        }
        if (safeSubtotal.compareTo(currentPendingBill) > 0) {
            throw new IllegalArgumentException(String.format(
                    "مبلغ پایه تسویه (%s$) بیشتر از کل بدهی جاری بیمار (%s$) است.",
                    safeSubtotal.toPlainString(), currentPendingBill.toPlainString()
            ));
        }

        // ۳. کسر مبلغ پایه از بدهی بیمار
        patient.deductPayment(safeSubtotal);

        String docId = (patient.getAssignedDoctor() != null)
                ? patient.getAssignedDoctor().getNationalId()
                : DEFAULT_DOCTOR_ID;

        String referenceNumber = POS_RECEIPT_PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String finalDesc = buildSettlementDescription(description, normalizedKey);

        Receipt receipt = new Receipt(
                referenceNumber,
                patient.getNationalId(),
                docId,
                cashier.getUsername(),
                safeSubtotal,
                safeTax,
                safeDiscount,
                method,
                Receipt.ReceiptStatus.PAID,
                finalDesc,
                SystemClock.now(),
                normalizedKey
        );

        financialRepository.save(receipt);
        patient.addReceipt(receipt);

        if (patient.getPendingBill().compareTo(BigDecimal.ZERO) == 0) {
            patient.transitionTo(PatientStatus.PAYMENT_COMPLETED);
        }

        patientRepository.save(patient);

        auditService.logCurrentActorAction("CASHIER", "BILLING_SETTLEMENT", "SUCCESS",
                String.format(
                        "تسویه بیمار %s | رسید: %s | کسر از بدهی: %s$ | تخفیف: %s$ | مالیات: %s$ | خالص دریافتی: %s$ (%s)",
                        patient.getNationalId(), referenceNumber, safeSubtotal, safeDiscount, safeTax, netPayable, method
                )
        );

        return receipt;
    }

    // ==================== Supervised Refund Operations ====================

    @Transactional
    public Receipt issueSupervisedRefund(Cashier cashier,
                                         String patientNationalId,
                                         String originalReceiptId,
                                         BigDecimal refundAmount,
                                         String reason,
                                         Admin supervisor) {
        if (supervisor == null || supervisor.isAccountLocked() || !supervisor.getAdminRole().canManageFinances()) {
            throw InsufficientPermissionException.refundApprovalRequired(
                    cashier != null ? cashier.getUsername() : "UNKNOWN", originalReceiptId
            );
        }
        if (originalReceiptId == null || originalReceiptId.isBlank()) {
            throw new IllegalArgumentException("شناسه رسید اصلی الزامی است.");
        }

        Patient patient = patientRepository.findByNationalIdForUpdate(patientNationalId)
                .orElseThrow(() -> EntityNotFoundException.patientNotFound(patientNationalId));

        BigDecimal safeRefundAmount = normalizeMoney(refundAmount);
        if (safeRefundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("مبلغ استرداد باید بزرگ‌تر از صفر باشد.");
        }

        Receipt originalReceipt = financialRepository.findById(originalReceiptId.trim())
                .orElseThrow(() -> new NoSuchElementException("رسید اصلی با شناسه [" + originalReceiptId + "] یافت نشد."));

        if (!originalReceipt.verifyIntegrity()) {
            throw new SecurityException("امضای دیجیتال رسید اصلی مخدوش شده است؛ امکان استرداد وجه وجود ندارد.");
        }

        if (!originalReceipt.isRefundable()) {
            throw new IllegalStateException("این رسید قابل استرداد نیست (قبلاً به طور کامل مسترد یا باطل شده است).");
        }

        if (!originalReceipt.getPatientNationalId().equals(patient.getNationalId())) {
            throw new IllegalArgumentException("رسید انتخاب‌شده متعلق به این بیمار نیست.");
        }

        BigDecimal remainingRefundable = originalReceipt.getRemainingRefundableAmount();
        if (safeRefundAmount.compareTo(remainingRefundable) > 0) {
            throw new IllegalArgumentException(String.format(
                    "مبلغ استرداد (%s$) نمی‌تواند از مانده قابل استرداد رسید (%s$) بیشتر باشد.",
                    safeRefundAmount.toPlainString(), remainingRefundable.toPlainString()
            ));
        }

        // اعمال استرداد (جزئی یا کامل) روی رسید اصلی
        originalReceipt.applyRefund(safeRefundAmount, reason);
        financialRepository.save(originalReceipt);

        String cleanReason = (reason != null && !reason.isBlank()) ? reason.trim() : "استرداد وجه بالینی";
        String fullDescription = String.format(
                "REFUND [%s] AuthBy: %s (%s) | Reason: %s",
                originalReceipt.getReceiptId(), supervisor.getFullName(), supervisor.getUsername(), cleanReason
        );

        String docId = (patient.getAssignedDoctor() != null)
                ? patient.getAssignedDoctor().getNationalId()
                : originalReceipt.getDoctorNationalId();

        Receipt refundReceipt = new Receipt(
                REFUND_RECEIPT_PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                patient.getNationalId(),
                docId,
                cashier != null ? cashier.getUsername() : supervisor.getUsername(),
                safeRefundAmount,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                originalReceipt.getPaymentMethod(),
                Receipt.ReceiptStatus.REFUNDED,
                fullDescription,
                SystemClock.now()
        );

        refundReceipt = financialRepository.save(refundReceipt);
        patient.addReceipt(refundReceipt);

        // بازگرداندن مبلغ مستردشده به بدهی باز بیمار و اصلاح وضعیت پرونده
        patient.addCharge(safeRefundAmount);
        if (patient.getCurrentStatus() != PatientStatus.WAITING_FOR_PAYMENT) {
            patient.transitionTo(PatientStatus.WAITING_FOR_PAYMENT);
        }
        patientRepository.save(patient);

        auditService.logCurrentActorAction("FINANCIAL_ADMIN", "BILLING_REFUND", "SUCCESS",
                String.format(
                        "استرداد وجه رسید %s برای بیمار %s به مبلغ %s$ (وضعیت جدید رسید اصلی: %s) با تایید %s",
                        originalReceipt.getReceiptId(), patientNationalId, safeRefundAmount,
                        originalReceipt.getReceiptStatus(), supervisor.getUsername()
                )
        );

        return refundReceipt;
    }

    // ==================== Reporting & Verification ====================

    @Transactional(readOnly = true)
    public List<Receipt> getPatientReceipts(String patientNationalId) {
        return financialRepository.findByPatientNationalIdOrderByTimestampDesc(patientNationalId);
    }

    @Transactional(readOnly = true)
    public List<Receipt> getAllReceipts() {
        return financialRepository.findAll();
    }

    @Transactional(readOnly = true)
    public boolean verifyAllReceiptsIntegrity() {
        return financialRepository.findAll().stream().allMatch(Receipt::verifyIntegrity);
    }

    @Transactional(readOnly = true)
    public FinancialSummaryResponse getFinancialSummary() {
        List<Receipt> allReceipts = financialRepository.findAll();

        BigDecimal grossRevenue = zeroMoney();
        BigDecimal totalDiscounts = zeroMoney();
        BigDecimal totalTax = zeroMoney();
        BigDecimal totalRefunded = zeroMoney();
        long paidCount = 0L;
        long refundedCount = 0L;
        boolean allIntact = true;

        Map<Receipt.PaymentMethod, BigDecimal> byMethod = new EnumMap<>(Receipt.PaymentMethod.class);
        for (Receipt.PaymentMethod pm : Receipt.PaymentMethod.values()) {
            byMethod.put(pm, zeroMoney());
        }

        for (Receipt r : allReceipts) {
            if (!r.verifyIntegrity()) {
                allIntact = false;
            }

            boolean isRefundDocument = r.getReceiptId() != null && r.getReceiptId().startsWith(REFUND_RECEIPT_PREFIX);
            Receipt.ReceiptStatus status = r.getReceiptStatus();

            if (isRefundDocument && status == Receipt.ReceiptStatus.REFUNDED) {
                refundedCount++;
                totalRefunded = totalRefunded.add(r.getAmount());
            } else if (!isRefundDocument && status != Receipt.ReceiptStatus.VOIDED && status != Receipt.ReceiptStatus.PENDING) {
                // رسیدهای اصلی فروش (PAID، PARTIALLY_REFUNDED یا REFUNDED)
                if (status == Receipt.ReceiptStatus.PAID || status == Receipt.ReceiptStatus.PARTIALLY_REFUNDED) {
                    paidCount++;
                }
                grossRevenue = grossRevenue.add(r.getAmount());
                totalDiscounts = totalDiscounts.add(r.getDiscountAmount());
                totalTax = totalTax.add(r.getTaxAmount());

                BigDecimal netRetainedForMethod = r.getNetRetainedAmount();
                byMethod.put(r.getPaymentMethod(), byMethod.get(r.getPaymentMethod()).add(netRetainedForMethod));
            }
        }

        BigDecimal netCollected = grossRevenue
                .add(totalTax)
                .subtract(totalDiscounts)
                .subtract(totalRefunded)
                .setScale(MONEY_SCALE, MONEY_ROUNDING);

        BigDecimal pendingReceivables = normalizeMoney(patientRepository.calculateTotalPendingReceivables());

        Map<String, BigDecimal> methodMap = new LinkedHashMap<>();
        byMethod.forEach((k, v) -> methodMap.put(k.name(), v));

        return new FinancialSummaryResponse(
                grossRevenue,
                totalDiscounts,
                totalTax,
                totalRefunded,
                netCollected,
                pendingReceivables,
                paidCount,
                refundedCount,
                allIntact,
                methodMap
        );
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateTotalRevenue() {
        return getFinancialSummary().netCollectedRevenue();
    }

    // ==================== Private Validation Helpers ====================

    private Optional<Receipt> findExistingByIdempotencyKey(String normalizedKey) {
        return financialRepository.findByIdempotencyKey(normalizedKey);
    }

    private static void verifyIdempotentRequestMatch(Receipt existing,
                                                     String requestedPatientId,
                                                     BigDecimal requestedSubtotal,
                                                     BigDecimal requestedDiscount,
                                                     BigDecimal requestedTax,
                                                     Receipt.PaymentMethod requestedMethod) {
        if (!existing.verifyIntegrity()) {
            throw new SecurityException("امضای دیجیتال رسید قبلی مخدوش شده است.");
        }
        boolean samePatient = existing.getPatientNationalId().equals(requestedPatientId);
        boolean sameAmount = existing.getAmount().compareTo(requestedSubtotal) == 0;
        boolean sameDiscount = existing.getDiscountAmount().compareTo(requestedDiscount) == 0;
        boolean sameTax = existing.getTaxAmount().compareTo(requestedTax) == 0;
        boolean sameMethod = existing.getPaymentMethod() == requestedMethod;

        if (!samePatient || !sameAmount || !sameDiscount || !sameTax || !sameMethod) {
            throw new IllegalStateException(
                    "تعارض در کلید یکتای تراکنش (Idempotency Key Conflict): این کلید قبلاً برای تراکنش دیگری با مشخصات متفاوت استفاده شده است."
            );
        }
    }

    private static void validateSettlementAmounts(BigDecimal subtotal, BigDecimal discount, BigDecimal tax) {
        if (subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("مبلغ پایه تسویه باید بزرگ‌تر از صفر باشد.");
        }
        if (discount.compareTo(BigDecimal.ZERO) < 0 || tax.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("مبلغ تخفیف یا مالیات نمی‌تواند منفی باشد.");
        }
        if (discount.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("مبلغ تخفیف نمی‌تواند از مبلغ پایه تسویه بیشتر باشد.");
        }
    }

    private static String buildSettlementDescription(String description, String normalizedKey) {
        String baseDesc = (description != null && !description.isBlank()) ? description.trim() : "تسویه در باجه صندوق";
        if (normalizedKey != null) {
            return baseDesc + " [IDEMP:" + normalizedKey + "]";
        }
        return baseDesc;
    }

    private static BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private static BigDecimal normalizeMoney(BigDecimal value) {
        return (value != null) ? value.setScale(MONEY_SCALE, MONEY_ROUNDING) : zeroMoney();
    }
}