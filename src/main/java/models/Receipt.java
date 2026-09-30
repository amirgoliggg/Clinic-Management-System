package models;

import system.SystemClock;
import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "receipts")
public final class Receipt implements Serializable, Comparable<Receipt> {

    @Serial
    private static final long serialVersionUID = 40L;

    public enum PaymentMethod { CASH, CREDIT_CARD, DEBIT_CARD, INSURANCE, BANK_TRANSFER, ONLINE }
    public enum ReceiptStatus { PAID, PENDING, REFUNDED, VOIDED }

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

    @Column(name = "tax_amount", precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;

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

    @Column(name = "integrity_hash", updatable = false, nullable = false)
    private String integrityHash;

    protected Receipt() {}

    public Receipt(String receiptId, String patientNationalId, String doctorNationalId,
                   String cashierUsername, BigDecimal amount, BigDecimal taxAmount,
                   BigDecimal discountAmount, PaymentMethod paymentMethod,
                   ReceiptStatus receiptStatus, String description, SystemClock timestamp) {
        this.receiptId = Objects.requireNonNull(receiptId).trim();
        this.patientNationalId = Objects.requireNonNull(patientNationalId).trim();
        this.doctorNationalId = Objects.requireNonNull(doctorNationalId).trim();
        this.cashierUsername = Objects.requireNonNull(cashierUsername).trim();
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.taxAmount = (taxAmount != null) ? taxAmount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.discountAmount = (discountAmount != null) ? discountAmount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.paymentMethod = Objects.requireNonNull(paymentMethod);
        this.receiptStatus = Objects.requireNonNull(receiptStatus);
        this.description = description != null ? description.trim() : "";
        this.timestamp = Objects.requireNonNull(timestamp);
        this.integrityHash = calculatePayloadHash();
    }

    private String calculatePayloadHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = receiptId + patientNationalId + doctorNationalId + cashierUsername +
                    amount.toPlainString() + paymentMethod.name() + timestamp.toEpochMilli();
            byte[] encoded = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(2 * encoded.length);
            for (byte b : encoded) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 error.", e);
        }
    }

    public boolean verifyIntegrity() { return this.integrityHash.equals(calculatePayloadHash()); }

    public void voidReceipt(String reason) { this.receiptStatus = ReceiptStatus.VOIDED; }
    public void refundReceipt(String reason) { this.receiptStatus = ReceiptStatus.REFUNDED; }

    public String getReceiptId() { return receiptId; }
    public String getPatientNationalId() { return patientNationalId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getAmountPaid() { return amount; }
    public BigDecimal getNetTotal() { return amount.add(taxAmount).subtract(discountAmount).setScale(2, RoundingMode.HALF_UP); }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public ReceiptStatus getReceiptStatus() { return receiptStatus; }
    public boolean isPaid() { return receiptStatus == ReceiptStatus.PAID; }
    public SystemClock getTimestamp() { return timestamp; }

    @Override
    public int compareTo(Receipt other) { return this.timestamp.compareTo(other.timestamp); }
}