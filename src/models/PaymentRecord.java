package models;

import system.SystemClock;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

/**
 * Enterprise Immutable Staff Payroll and Compensation Record.
 * Cryptographically sealed with SHA-256 and integrated into hospital accounting.
 */
public final class PaymentRecord implements Serializable, Comparable<PaymentRecord> {
    @Serial
    private static final long serialVersionUID = 40L;

    public enum PaymentMethod {
        DIRECT_DEPOSIT("Bank Direct Deposit (ACH / IBAN)"),
        CHECK("Official Bank Cashier Cheque"),
        CASH("Physical Cash Disbursement"),
        ELECTRONIC_TRANSFER("Electronic Wire Transfer");

        private final String displayLabel;

        PaymentMethod(String displayLabel) {
            this.displayLabel = displayLabel;
        }

        public String getDisplayLabel() {
            return displayLabel;
        }

        @Override
        public String toString() {
            return displayLabel;
        }
    }

    private final String paymentId;
    private final String staffNationalId;
    private final BigDecimal amount;
    private final PaymentMethod paymentMethod;
    private final String destinationAccount;
    private final String transactionReference;
    private final String description;
    private final SystemClock timestamp;
    private final String integrityHash;

    /**
     * Primary 7-argument Constructor matching enterprise accounting rules.
     */
    public PaymentRecord(String staffNationalId, BigDecimal amount, PaymentMethod paymentMethod,
                         String destinationAccount, String transactionReference, String description,
                         SystemClock timestamp) {

        this.paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.staffNationalId = Objects.requireNonNull(staffNationalId, "Staff National ID cannot be null.").trim();

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Disbursement amount must be strictly positive.");
        }
        this.amount = amount;
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "Payment method cannot be null.");
        this.destinationAccount = (destinationAccount == null || destinationAccount.trim().isEmpty())
                ? "TREASURY_CASH"
                : destinationAccount.trim();
        this.transactionReference = (transactionReference == null || transactionReference.trim().isEmpty())
                ? "REF-" + System.currentTimeMillis()
                : transactionReference.trim();
        this.description = (description == null || description.trim().isEmpty())
                ? "Monthly Staff Salary Disbursement"
                : description.trim();
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp cannot be null.");
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
            throw new IllegalStateException("Security error: SHA-256 algorithm unavailable.", e);
        }
    }

    public boolean verifyIntegrity() {
        return this.integrityHash.equals(calculateHash());
    }

    public String getPaymentId() { return paymentId; }
    public String getStaffNationalId() { return staffNationalId; }
    public BigDecimal getAmount() { return amount; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getDestinationAccount() { return destinationAccount; }
    public String getTransactionReference() { return transactionReference; }
    public String getDescription() { return description; }
    public SystemClock getTimestamp() { return timestamp; }
    public String getIntegrityHash() { return integrityHash; }

    public String getFormattedAmount() {
        return String.format("$%,.2f", amount);
    }

    @Override
    public int compareTo(PaymentRecord other) {
        Objects.requireNonNull(other, "Cannot compare to null PaymentRecord.");
        return this.timestamp.compareTo(other.timestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentRecord that = (PaymentRecord) o;
        return paymentId.equals(that.paymentId) && integrityHash.equals(that.integrityHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paymentId, integrityHash);
    }

    @Override
    public String toString() {
        return String.format("[%s] Amount: %s | Staff: %s | Method: %s | Account: %s | Ref: %s",
                paymentId, getFormattedAmount(), staffNationalId, paymentMethod.getDisplayLabel(),
                destinationAccount, transactionReference);
    }
}