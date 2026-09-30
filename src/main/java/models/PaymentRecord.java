package models;

import system.SystemClock;
import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "payment_records")
public final class PaymentRecord implements Serializable, Comparable<PaymentRecord> {

    @Serial
    private static final long serialVersionUID = 40L;

    public enum PaymentMethod { DIRECT_DEPOSIT, CHECK, CASH, ELECTRONIC_TRANSFER }

    @Id
    @Column(name = "payment_id", length = 36, updatable = false, nullable = false)
    private String paymentId;

    @Column(name = "staff_national_id", nullable = false)
    private String staffNationalId;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "destination_account", length = 255)
    private String destinationAccount;

    @Column(name = "transaction_reference", length = 255)
    private String transactionReference;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "timestamp_milli", nullable = false, updatable = false)
    private SystemClock timestamp;

    @Column(name = "integrity_hash", updatable = false, nullable = false)
    private String integrityHash;

    protected PaymentRecord() {}

    public PaymentRecord(String staffNationalId, BigDecimal amount, PaymentMethod paymentMethod,
                         String destinationAccount, String transactionReference, String description,
                         SystemClock timestamp) {
        this.paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.staffNationalId = Objects.requireNonNull(staffNationalId).trim();
        this.amount = amount;
        this.paymentMethod = Objects.requireNonNull(paymentMethod);
        this.destinationAccount = destinationAccount;
        this.transactionReference = transactionReference;
        this.description = description;
        this.timestamp = Objects.requireNonNull(timestamp);
        this.integrityHash = calculateHash();
    }

    private String calculateHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = paymentId + staffNationalId + amount.toPlainString() +
                    paymentMethod.name() + destinationAccount + transactionReference +
                    description + timestamp.toEpochMilli();
            byte[] encoded = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(2 * encoded.length);
            for (byte b : encoded) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable.", e);
        }
    }

    public boolean verifyIntegrity() { return this.integrityHash.equals(calculateHash()); }

    public String getPaymentId() { return paymentId; }
    public String getStaffNationalId() { return staffNationalId; }
    public BigDecimal getAmount() { return amount; }
    public SystemClock getTimestamp() { return timestamp; }

    @Override
    public int compareTo(PaymentRecord other) { return this.timestamp.compareTo(other.timestamp); }
}