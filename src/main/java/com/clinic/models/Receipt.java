package com.clinic.models;

import com.clinic.system.SystemClock;
import jakarta.persistence.*;
import com.clinic.security.HmacKeyProvider;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

@Entity
@Table(
        name = "receipts",
        indexes = {
                @Index(name = "idx_receipt_patient", columnList = "patient_national_id"),
                @Index(name = "idx_receipt_idempotency", columnList = "idempotency_key", unique = true)
        }
)
public class Receipt implements Serializable, Comparable<Receipt> {

    @Serial
    private static final long serialVersionUID = 40L;

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DEFAULT_CURRENCY = "USD";

    public enum PaymentMethod {
        CASH,
        CREDIT_CARD,
        DEBIT_CARD,
        INSURANCE,
        BANK_TRANSFER,
        ONLINE
    }

    public enum ReceiptStatus {
        PAID,
        PARTIALLY_REFUNDED,
        PENDING,
        REFUNDED,
        VOIDED
    }

    @Id
    @Column(name = "receipt_id", length = 36, updatable = false, nullable = false)
    private String receiptId;

    @Column(name = "patient_national_id", nullable = false)
    private String patientNationalId;

    @Column(name = "doctor_national_id", nullable = false)
    private String doctorNationalId;

    @Column(name = "cashier_username", nullable = false)
    private String cashierUsername;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "tax_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal taxAmount;

    @Column(name = "discount_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal discountAmount;

    @Column(name = "refunded_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal refundedAmount = zeroMoney();

    @Column(name = "currency", length = 8, nullable = false)
    private String currency = DEFAULT_CURRENCY;

    @Column(name = "idempotency_key", length = 128, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "receipt_status", nullable = false)
    private ReceiptStatus receiptStatus;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "timestamp_milli", nullable = false, updatable = false)
    private SystemClock timestamp;

    @Column(name = "integrity_hash", nullable = false, length = 128)
    private String integrityHash;

    // ==================== Constructors ====================

    protected Receipt() {
        // Required by JPA
    }

    public Receipt(String receiptId,
                   String patientNationalId,
                   String doctorNationalId,
                   String cashierUsername,
                   BigDecimal amount,
                   BigDecimal taxAmount,
                   BigDecimal discountAmount,
                   PaymentMethod paymentMethod,
                   ReceiptStatus receiptStatus,
                   String description,
                   SystemClock timestamp) {
        this(receiptId, patientNationalId, doctorNationalId, cashierUsername,
                amount, taxAmount, discountAmount, paymentMethod, receiptStatus,
                description, timestamp, null);
    }

    public Receipt(String receiptId,
                   String patientNationalId,
                   String doctorNationalId,
                   String cashierUsername,
                   BigDecimal amount,
                   BigDecimal taxAmount,
                   BigDecimal discountAmount,
                   PaymentMethod paymentMethod,
                   ReceiptStatus receiptStatus,
                   String description,
                   SystemClock timestamp,
                   String idempotencyKey) {
        this.receiptId = requireNonBlank(receiptId, "Receipt ID cannot be null or blank.");
        this.patientNationalId = requireNonBlank(patientNationalId, "Patient National ID cannot be null or blank.");
        this.doctorNationalId = requireNonBlank(doctorNationalId, "Doctor National ID cannot be null or blank.");
        this.cashierUsername = requireNonBlank(cashierUsername, "Cashier username cannot be null or blank.");

        this.amount = normalizePositiveOrZero(amount, "Amount cannot be null or negative.");
        this.taxAmount = normalizePositiveOrZero(taxAmount, "Tax amount cannot be negative.");
        this.discountAmount = normalizePositiveOrZero(discountAmount, "Discount amount cannot be negative.");
        this.refundedAmount = zeroMoney();

        if (this.discountAmount.compareTo(this.amount) > 0) {
            throw new IllegalArgumentException("Discount amount cannot exceed base amount.");
        }

        this.paymentMethod = Objects.requireNonNull(paymentMethod, "Payment method cannot be null.");
        this.receiptStatus = Objects.requireNonNull(receiptStatus, "Receipt status cannot be null.");
        this.description = (description != null) ? description.trim() : "";
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp cannot be null.");
        this.idempotencyKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey.trim() : null;
        this.currency = DEFAULT_CURRENCY;
        this.integrityHash = calculatePayloadHash();
    }

    // ==================== Lifecycle & Normalization ====================

    @PrePersist
    @PreUpdate
    public void ensureDefaults() {
        if (this.taxAmount == null) {
            this.taxAmount = zeroMoney();
        }
        if (this.discountAmount == null) {
            this.discountAmount = zeroMoney();
        }
        if (this.refundedAmount == null) {
            this.refundedAmount = zeroMoney();
        }
        if (this.currency == null || this.currency.isBlank()) {
            this.currency = DEFAULT_CURRENCY;
        }
        if (this.description == null) {
            this.description = "";
        }
    }

    private static BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private static BigDecimal normalizeMoney(BigDecimal value) {
        return (value != null) ? value.setScale(MONEY_SCALE, MONEY_ROUNDING) : zeroMoney();
    }

    private static BigDecimal normalizePositiveOrZero(BigDecimal value, String errorMessage) {
        if (value == null) {
            return zeroMoney();
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    // ==================== Cryptographic Integrity (HMAC-SHA256) ====================

    // در بالای کلاس Receipt، خط FALLBACK_HMAC_SECRET را حذف کن و import زیر را اضافه کن:
    // import com.clinic.security.HmacKeyProvider;

    private String calculatePayloadHash() {
        String safeAmount = normalizeMoney(amount).toPlainString();
        String safeTax = normalizeMoney(taxAmount).toPlainString();
        String safeDiscount = normalizeMoney(discountAmount).toPlainString();
        String safeRefunded = normalizeMoney(refundedAmount).toPlainString();
        String safeDesc = (description != null) ? description : "";

        String payload = String.join(":",
                receiptId,
                patientNationalId,
                doctorNationalId,
                cashierUsername,
                safeAmount,
                safeTax,
                safeDiscount,
                safeRefunded,
                paymentMethod.name(),
                receiptStatus.name(),
                safeDesc,
                String.valueOf(timestamp.toEpochMilli())
        );

        try {
            byte[] keyBytes = com.clinic.security.HmacKeyProvider.getReceiptKey();
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signatureBytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 integrity signature for receipt.", e);
        }
    }

    public boolean verifyIntegrity() {
        if (this.integrityHash == null || this.integrityHash.isBlank()) {
            return false;
        }
        String expectedHash = calculatePayloadHash();
        return MessageDigest.isEqual(
                this.integrityHash.getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    // ==================== Accounting & State Transitions ====================

    public void voidReceipt(String reason) {
        if (this.receiptStatus == ReceiptStatus.REFUNDED || this.receiptStatus == ReceiptStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("امکان ابطال رسیدی که استرداد وجه روی آن انجام شده وجود ندارد.");
        }
        this.receiptStatus = ReceiptStatus.VOIDED;
        appendDescriptionTag("VOID: " + ((reason != null && !reason.isBlank()) ? reason.trim() : "N/A"));
        this.integrityHash = calculatePayloadHash();
    }

    /**
     * اعمال استرداد وجه (کامل یا جزئی) روی رسید اصلی و به‌روزرسانی وضعیت و امضای دیجیتال.
     */
    public void applyRefund(BigDecimal refundAmountToApply, String reason) {
        if (!isRefundable()) {
            throw new IllegalStateException("فقط رسیدهای پرداخت‌شده یا دارای استرداد جزئی قابل استرداد هستند.");
        }
        if (refundAmountToApply == null || refundAmountToApply.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("مبلغ استرداد باید بزرگ‌تر از صفر باشد.");
        }

        BigDecimal normalizedRefund = normalizeMoney(refundAmountToApply);
        BigDecimal remainingRefundable = getRemainingRefundableAmount();

        if (normalizedRefund.compareTo(remainingRefundable) > 0) {
            throw new IllegalArgumentException(
                    "مبلغ استرداد (" + normalizedRefund + ") بیشتر از مانده قابل استرداد رسید (" + remainingRefundable + ") است."
            );
        }

        this.refundedAmount = normalizeMoney(this.refundedAmount).add(normalizedRefund);

        if (this.refundedAmount.compareTo(getMaxRefundableCap()) >= 0) {
            this.receiptStatus = ReceiptStatus.REFUNDED;
        } else {
            this.receiptStatus = ReceiptStatus.PARTIALLY_REFUNDED;
        }

        appendDescriptionTag("REFUND(" + normalizedRefund.toPlainString() + "): "
                + ((reason != null && !reason.isBlank()) ? reason.trim() : "N/A"));
        this.integrityHash = calculatePayloadHash();
    }

    /**
     * متد سازگار با نسخه‌های قبلی برای استرداد کامل مانده رسید.
     */
    public void refundReceipt(String reason) {
        BigDecimal remaining = getRemainingRefundableAmount();
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            applyRefund(remaining, reason);
        } else {
            this.receiptStatus = ReceiptStatus.REFUNDED;
            appendDescriptionTag("REFUND: " + ((reason != null && !reason.isBlank()) ? reason.trim() : "N/A"));
            this.integrityHash = calculatePayloadHash();
        }
    }

    private void appendDescriptionTag(String tag) {
        this.description = (this.description == null || this.description.isBlank())
                ? tag
                : this.description + " | " + tag;
    }

    // ==================== Explicit Accounting Getters ====================

    public String getReceiptId() {
        return receiptId;
    }

    public String getPatientNationalId() {
        return patientNationalId;
    }

    public String getDoctorNationalId() {
        return doctorNationalId;
    }

    public String getCashierUsername() {
        return cashierUsername;
    }

    /**
     * مبلغ پایه تسویه‌شده از بدهی بیمار (Subtotal).
     */
    public BigDecimal getAmount() {
        return normalizeMoney(amount);
    }

    public BigDecimal getSubtotalAmount() {
        return normalizeMoney(amount);
    }

    public BigDecimal getAmountPaid() {
        return normalizeMoney(amount);
    }

    public BigDecimal getTaxAmount() {
        return normalizeMoney(taxAmount);
    }

    public BigDecimal getDiscountAmount() {
        return normalizeMoney(discountAmount);
    }

    public BigDecimal getRefundedAmount() {
        return normalizeMoney(refundedAmount);
    }

    /**
     * مبلغ خالص قابل پرداخت در زمان صدور رسید: Subtotal + Tax - Discount
     */
    public BigDecimal getNetTotal() {
        return getAmount()
                .add(getTaxAmount())
                .subtract(getDiscountAmount())
                .setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    /**
     * خالص وجه نهایی باقی‌مانده در صندوق پس از کسر استردادهای انجام‌شده روی این رسید.
     */
    public BigDecimal getNetRetainedAmount() {
        BigDecimal retained = getNetTotal().subtract(getRefundedAmount());
        return retained.compareTo(BigDecimal.ZERO) > 0 ? retained : zeroMoney();
    }

    /**
     * سقف مجاز استرداد برای این رسید.
     */
    private BigDecimal getMaxRefundableCap() {
        // Preserve the existing subtotal ceiling, but never return more cash than collected.
        return getAmount().min(getNetTotal()).max(zeroMoney());
    }

    public BigDecimal getRemainingRefundableAmount() {
        if (this.receiptId != null && this.receiptId.startsWith("REF-")) {
            return zeroMoney();
        }
        BigDecimal remaining = getMaxRefundableCap().subtract(getRefundedAmount());
        return remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : zeroMoney();
    }

    public String getCurrency() {
        return (currency != null && !currency.isBlank()) ? currency : DEFAULT_CURRENCY;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public ReceiptStatus getReceiptStatus() {
        return receiptStatus;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPaid() {
        return receiptStatus == ReceiptStatus.PAID || receiptStatus == ReceiptStatus.PARTIALLY_REFUNDED;
    }

    public boolean isRefundable() {
        return (receiptStatus == ReceiptStatus.PAID || receiptStatus == ReceiptStatus.PARTIALLY_REFUNDED)
                && !receiptId.startsWith("REF-")
                && getRemainingRefundableAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    public SystemClock getTimestamp() {
        return timestamp;
    }

    public String getIntegrityHash() {
        return integrityHash;
    }

    @Override
    public int compareTo(Receipt other) {
        return this.timestamp.compareTo(other.timestamp);
    }
}