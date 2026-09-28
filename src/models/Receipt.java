package models;

import system.SystemClock;

import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

/**
 * Enterprise Immutable Financial Receipt entity.
 * Issued upon fee settlement, cryptographically sealed with SHA-256,
 * and attached both to the Cashier's shift ledger and the Patient's billing history.
 */
public final class Receipt implements Serializable, Comparable<Receipt> {
    private static final long serialVersionUID = 35L;

    private final String receiptId;
    private final String patientNationalId;
    private final String cashierNationalId;
    private final BigDecimal amountPaid;
    private final PaymentRecord.PaymentMethod paymentMethod;
    private final String serviceDescription;
    private final String transactionReference;
    private final SystemClock timestamp;
    private final String integrityHash;

    /**
     * Primary constructor creating a sealed financial receipt.
     */
    public Receipt(String patientNationalId, String cashierNationalId, BigDecimal amountPaid,
                   PaymentRecord.PaymentMethod paymentMethod, String serviceDescription,
                   String transactionReference, SystemClock timestamp) {

        this.receiptId = "REC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.patientNationalId = Objects.requireNonNull(patientNationalId, "Patient ID cannot be null.").trim();
        this.cashierNationalId = Objects.requireNonNull(cashierNationalId, "Cashier ID cannot be null.").trim();

        if (amountPaid == null || amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount paid must be strictly positive.");
        }
        this.amountPaid = amountPaid;
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "Payment method cannot be null.");
        this.serviceDescription = (serviceDescription == null || serviceDescription.trim().isEmpty())
                ? "Hospital Clinical Services"
                : serviceDescription.trim();
        this.transactionReference = (transactionReference == null || transactionReference.trim().isEmpty())
                ? "TXN-" + System.currentTimeMillis()
                : transactionReference.trim();
        this.timestamp = Objects.requireNonNull(timestamp, "Receipt timestamp cannot be null.");
        this.integrityHash = calculateHash();
    }

    private String calculateHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = receiptId + patientNationalId + cashierNationalId +
                    amountPaid.toPlainString() + paymentMethod.name() +
                    transactionReference + timestamp.toEpochMilli();
            byte[] encoded = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(2 * encoded.length);
            for (byte b : encoded) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cryptographic provider error: SHA-256 unavailable.", e);
        }
    }

    public boolean verifyIntegrity() {
        return this.integrityHash.equals(calculateHash());
    }

    public String getReceiptId() { return receiptId; }
    public String getPatientNationalId() { return patientNationalId; }
    public String getCashierNationalId() { return cashierNationalId; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public PaymentRecord.PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getServiceDescription() { return serviceDescription; }
    public String getTransactionReference() { return transactionReference; }
    public SystemClock getTimestamp() { return timestamp; }
    public String getIntegrityHash() { return integrityHash; }

    public String getFormattedAmount() {
        return String.format("$%,.2f", amountPaid);
    }

    public String getPrintableReceipt() {
        return String.format(
                "======================================================================%n" +
                        "                       OFFICIAL PAYMENT RECEIPT                       %n" +
                        "======================================================================%n" +
                        " Receipt ID      : %s%n" +
                        " Patient ID      : %s%n" +
                        " Cashier ID      : %s%n" +
                        " Date & Time     : %s%n" +
                        " Service Details : %s%n" +
                        " Payment Method  : %s%n" +
                        " Reference / Card: %s%n" +
                        "----------------------------------------------------------------------%n" +
                        " TOTAL PAID      : %s%n" +
                        " Integrity Seal  : %s%n" +
                        "======================================================================",
                receiptId, patientNationalId, cashierNationalId,
                timestamp.toFullDisplayString(), serviceDescription,
                paymentMethod.toString(), transactionReference,
                getFormattedAmount(), integrityHash.substring(0, 16) + "..."
        );
    }

    @Override
    public int compareTo(Receipt other) {
        Objects.requireNonNull(other, "Cannot compare to null Receipt.");
        return this.timestamp.compareTo(other.timestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Receipt receipt = (Receipt) o;
        return receiptId.equals(receipt.receiptId) && integrityHash.equals(receipt.integrityHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiptId, integrityHash);
    }

    @Override
    public String toString() {
        return String.format("[%s] Amount: %s | Method: %s | Patient: %s | Ref: %s",
                receiptId, getFormattedAmount(), paymentMethod.toString(), patientNationalId, transactionReference);
    }
}