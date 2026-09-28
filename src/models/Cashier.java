package models;

import enums.PatientStatus;
import system.SystemClock;

import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Enterprise Hospital Cashier and Point-of-Sale (POS) terminal controller.
 * Enforces dual-authorization refund policies, shift float reconciliation,
 * payment channel segregation, thread-safe financial ledgers, and GUI telemetry.
 */
public final class Cashier extends Staff {
    private static final long serialVersionUID = 28L;

    private static final Pattern REGISTER_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]{2,20}$");
    private static final BigDecimal DEFAULT_MAX_TRANSACTION_LIMIT = new BigDecimal("10000.00");

    /**
     * Immutable Shift Reconciliation Report produced when closing a register shift.
     * Contains ledger breakdown, physical discrepancy audit, and SHA-256 verification seal.
     */
    public static final class ShiftReconciliationReport implements Serializable {
        private static final long serialVersionUID = 29L;

        private final String shiftId;
        private final String cashierNationalId;
        private final String workstationId;
        private final SystemClock openedAt;
        private final SystemClock closedAt;
        private final BigDecimal openingFloat;
        private final BigDecimal totalGrossSales;
        private final BigDecimal totalRefunds;
        private final BigDecimal netRevenue;
        private final BigDecimal cashCollected;
        private final BigDecimal electronicCollected;
        private final BigDecimal expectedCashInDrawer;
        private final BigDecimal actualCashCounted;
        private final BigDecimal cashVariance; // Negative: Shortage, Positive: Overage
        private final int totalTransactionsCount;
        private final String integrityHash;

        public ShiftReconciliationReport(String shiftId, String cashierNationalId, String workstationId,
                                         SystemClock openedAt, SystemClock closedAt, BigDecimal openingFloat,
                                         BigDecimal totalGrossSales, BigDecimal totalRefunds,
                                         BigDecimal cashCollected, BigDecimal electronicCollected,
                                         BigDecimal actualCashCounted, int totalTransactionsCount) {

            this.shiftId = Objects.requireNonNull(shiftId);
            this.cashierNationalId = Objects.requireNonNull(cashierNationalId);
            this.workstationId = Objects.requireNonNull(workstationId);
            this.openedAt = Objects.requireNonNull(openedAt);
            this.closedAt = Objects.requireNonNull(closedAt);
            this.openingFloat = Objects.requireNonNull(openingFloat);
            this.totalGrossSales = Objects.requireNonNull(totalGrossSales);
            this.totalRefunds = Objects.requireNonNull(totalRefunds);
            this.netRevenue = totalGrossSales.subtract(totalRefunds);
            this.cashCollected = Objects.requireNonNull(cashCollected);
            this.electronicCollected = Objects.requireNonNull(electronicCollected);
            this.actualCashCounted = Objects.requireNonNull(actualCashCounted);
            this.totalTransactionsCount = totalTransactionsCount;

            this.expectedCashInDrawer = openingFloat.add(cashCollected);
            this.cashVariance = actualCashCounted.subtract(this.expectedCashInDrawer);
            this.integrityHash = calculateSeal();
        }

        private String calculateSeal() {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                String payload = shiftId + cashierNationalId + workstationId +
                        openedAt.toEpochMilli() + closedAt.toEpochMilli() +
                        netRevenue.toPlainString() + cashVariance.toPlainString();
                byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder(2 * hash.length);
                for (byte b : hash) {
                    String h = Integer.toHexString(0xff & b);
                    if (h.length() == 1) hex.append('0');
                    hex.append(h);
                }
                return hex.toString();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("Security provider error: SHA-256 unavailable.", e);
            }
        }

        public String getShiftId() { return shiftId; }
        public String getCashierNationalId() { return cashierNationalId; }
        public String getWorkstationId() { return workstationId; }
        public SystemClock getOpenedAt() { return openedAt; }
        public SystemClock getClosedAt() { return closedAt; }
        public BigDecimal getOpeningFloat() { return openingFloat; }
        public BigDecimal getTotalGrossSales() { return totalGrossSales; }
        public BigDecimal getTotalRefunds() { return totalRefunds; }
        public BigDecimal getNetRevenue() { return netRevenue; }
        public BigDecimal getCashCollected() { return cashCollected; }
        public BigDecimal getElectronicCollected() { return electronicCollected; }
        public BigDecimal getExpectedCashInDrawer() { return expectedCashInDrawer; }
        public BigDecimal getActualCashCounted() { return actualCashCounted; }
        public BigDecimal getCashVariance() { return cashVariance; }
        public int getTotalTransactionsCount() { return totalTransactionsCount; }
        public String getIntegrityHash() { return integrityHash; }

        public boolean isBalanced() {
            return cashVariance.compareTo(BigDecimal.ZERO) == 0;
        }

        public String getFormattedSummary() {
            return String.format(
                    "======================================================================%n" +
                            "                   SHIFT RECONCILIATION SUMMARY REPORT                %n" +
                            "======================================================================%n" +
                            " Shift ID          : %s%n" +
                            " Cashier / POS     : %s / %s%n" +
                            " Operational Window: %s to %s%n" +
                            " Opening Float     : $%,.2f%n" +
                            " Gross Sales       : $%,.2f%n" +
                            " Refunds Deducted  : $%,.2f%n" +
                            " Net Sales Revenue : $%,.2f%n" +
                            " Cash In Drawer    : Expected: $%,.2f | Counted: $%,.2f%n" +
                            " Cash Discrepancy  : $%,.2f (%s)%n" +
                            " Total Vouchers    : %d%n" +
                            " Cryptographic Seal: %s%n" +
                            "======================================================================",
                    shiftId, cashierNationalId, workstationId,
                    openedAt.toFullDisplayString(), closedAt.toFullDisplayString(),
                    openingFloat, totalGrossSales, totalRefunds, netRevenue,
                    expectedCashInDrawer, actualCashCounted, cashVariance,
                    (isBalanced() ? "BALANCED" : (cashVariance.compareTo(BigDecimal.ZERO) < 0 ? "SHORTAGE" : "OVERAGE")),
                    totalTransactionsCount, integrityHash.substring(0, 16) + "..."
            );
        }

        @Override
        public String toString() {
            return String.format("[%s] Net: $%,.2f | Variance: $%,.2f | Status: %s",
                    shiftId, netRevenue, cashVariance, isBalanced() ? "BALANCED" : "DISCREPANCY");
        }
    }

    private final String registerTerminalId;
    private BigDecimal maxSingleTransactionLimit;
    private volatile boolean onDuty;
    private String currentShiftId;
    private SystemClock shiftStartTime;
    private BigDecimal currentOpeningFloat;
    private final List<Receipt> shiftLedger;
    private final List<Receipt> refundLedger;
    private final List<ShiftReconciliationReport> historicalReports;

    private transient ReentrantLock posLock;

    /**
     * Primary constructor for provisioning an enterprise hospital cashier terminal.
     */
    public Cashier(String nationalId, String fullName, Integer age, String contactNumber,
                   BigDecimal baseSalary, String registerTerminalId) {

        super(nationalId, fullName, age, contactNumber, baseSalary);

        this.registerTerminalId = validateRegisterTerminalId(registerTerminalId);
        this.maxSingleTransactionLimit = DEFAULT_MAX_TRANSACTION_LIMIT;
        this.onDuty = false;
        this.currentShiftId = null;
        this.shiftStartTime = null;
        this.currentOpeningFloat = BigDecimal.ZERO;
        this.shiftLedger = new ArrayList<>();
        this.refundLedger = new ArrayList<>();
        this.historicalReports = new ArrayList<>();
        this.posLock = new ReentrantLock(true);
    }

    /**
     * Protected Copy Constructor for Identity Migration (ID change).
     */
    private Cashier(String newNationalId, Cashier source) {
        super(newNationalId, source);
        this.registerTerminalId = source.registerTerminalId;
        this.maxSingleTransactionLimit = source.maxSingleTransactionLimit;
        this.onDuty = source.onDuty;
        this.currentShiftId = source.currentShiftId;
        this.shiftStartTime = source.shiftStartTime;
        this.currentOpeningFloat = source.currentOpeningFloat;
        this.shiftLedger = new ArrayList<>(source.shiftLedger);
        this.refundLedger = new ArrayList<>(source.refundLedger);
        this.historicalReports = new ArrayList<>(source.historicalReports);
        this.posLock = new ReentrantLock(true);
    }

    @Override
    public Person cloneWithNewId(String newNationalId) {
        return new Cashier(newNationalId, this);
    }

    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.posLock = new ReentrantLock(true);
    }

    private String validateRegisterTerminalId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Register Terminal ID cannot be null or empty.");
        }
        String trimmed = id.trim();
        if (!REGISTER_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Terminal ID format invalid. Must be 2-20 alphanumeric characters.");
        }
        return trimmed;
    }

    // =========================================================================
    // SHIFT LIFECYCLE MANAGEMENT
    // =========================================================================

    /**
     * Commences an official cashiering shift with a verified starting cash float.
     */
    public void openShift(BigDecimal openingFloat, SystemClock timestamp) {
        Objects.requireNonNull(timestamp, "Shift open timestamp cannot be null.");
        posLock.lock();
        try {
            if (this.onDuty) {
                throw new IllegalStateException("Register is already open under active shift: " + this.currentShiftId);
            }
            if (openingFloat == null || openingFloat.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Opening float cannot be null or negative.");
            }

            this.currentShiftId = "SHIFT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            this.shiftStartTime = timestamp;
            this.currentOpeningFloat = openingFloat;
            this.shiftLedger.clear();
            this.refundLedger.clear();
            this.onDuty = true;
        } finally {
            posLock.unlock();
        }
    }

    /**
     * Concludes the active shift, calculates variances against counted cash, and seals reconciliation.
     */
    public ShiftReconciliationReport closeShift(BigDecimal actualCashCounted, SystemClock timestamp) {
        Objects.requireNonNull(timestamp, "Shift close timestamp cannot be null.");
        Objects.requireNonNull(actualCashCounted, "Actual cash count cannot be null.");

        posLock.lock();
        try {
            if (!this.onDuty) {
                throw new IllegalStateException("No active shift is currently open on this terminal.");
            }

            BigDecimal grossSales = calculateGrossSales();
            BigDecimal totalRefunds = calculateTotalRefunds();
            BigDecimal cashCollected = calculatePaymentMethodTotal(PaymentRecord.PaymentMethod.CASH);
            BigDecimal electronicCollected = grossSales.subtract(cashCollected);

            ShiftReconciliationReport report = new ShiftReconciliationReport(
                    this.currentShiftId,
                    this.getNationalId(),
                    this.registerTerminalId,
                    this.shiftStartTime,
                    timestamp,
                    this.currentOpeningFloat,
                    grossSales,
                    totalRefunds,
                    cashCollected,
                    electronicCollected,
                    actualCashCounted,
                    this.shiftLedger.size() + this.refundLedger.size()
            );

            this.historicalReports.add(report);
            this.onDuty = false;
            this.currentShiftId = null;
            this.shiftStartTime = null;
            this.currentOpeningFloat = BigDecimal.ZERO;

            return report;
        } finally {
            posLock.unlock();
        }
    }

    // =========================================================================
    // CLINICAL TRANSACTION PROCESSING
    // =========================================================================

    public Receipt processPayment(Patient patient, BigDecimal amount, PaymentRecord.PaymentMethod method, String serviceDescription) {
        return processPayment(
                patient,
                amount,
                method,
                serviceDescription,
                "POS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                SystemClock.now()
        );
    }

    public Receipt processPayment(Patient patient, BigDecimal amount, PaymentRecord.PaymentMethod method,
                                  String serviceDescription, String referenceNumber, SystemClock timestamp) {

        Objects.requireNonNull(patient, "Patient cannot be null for payment processing.");
        Objects.requireNonNull(method, "Payment method cannot be null.");
        Objects.requireNonNull(timestamp, "Timestamp cannot be null.");

        posLock.lock();
        try {
            if (!this.onDuty) {
                throw new IllegalStateException("POS terminal is offline. Shift must be formally opened first.");
            }

            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Charge amount must be strictly greater than zero.");
            }

            if (amount.compareTo(this.maxSingleTransactionLimit) > 0) {
                throw new SecurityException("Transaction amount exceeds POS threshold limit ($" +
                        maxSingleTransactionLimit + "). Supervisor authorization override required.");
            }

            if (patient.getStatus() != PatientStatus.WAITING_FOR_PAYMENT) {
                throw new IllegalStateException("Payment rejected: Patient " + patient.getNationalId() +
                        " is not in WAITING_FOR_PAYMENT status. Current status: " + patient.getStatus().getLabel());
            }

            // 1. Issue cryptographically sealed receipt
            Receipt receipt = new Receipt(
                    patient.getNationalId(),
                    this.getNationalId(),
                    amount,
                    method,
                    serviceDescription,
                    referenceNumber,
                    timestamp
            );

            // 2. Commit transaction to internal shift ledger
            this.shiftLedger.add(receipt);

            // 3. Attach directly to patient's personal billing record
            patient.addReceipt(receipt);

            // 4. Advance patient lifecycle state
            patient.setStatus(PatientStatus.PAYMENT_COMPLETED);

            return receipt;
        } finally {
            posLock.unlock();
        }
    }

    /**
     * Anti-Fraud Supervised Refund Pipeline:
     * Authorizes and processes a fee reversal requiring supervisor/admin sign-off.
     */
    public Receipt issueRefund(Patient patient, String originalReceiptId, BigDecimal refundAmount,
                               String refundReason, Admin authorizingSupervisor, SystemClock timestamp) {

        Objects.requireNonNull(patient, "Patient cannot be null for refund.");
        Objects.requireNonNull(originalReceiptId, "Original receipt ID must be provided.");
        Objects.requireNonNull(authorizingSupervisor, "Authorizing supervisor cannot be null.");
        Objects.requireNonNull(timestamp, "Timestamp cannot be null.");

        posLock.lock();
        try {
            if (!this.onDuty) {
                throw new IllegalStateException("POS terminal is offline. Cannot issue refund.");
            }

            if (authorizingSupervisor.isAccountLocked() || !authorizingSupervisor.getRole().canManageFinances()) {
                throw new SecurityException("Security Violation: Authorizing supervisor lacks financial disbursement permissions.");
            }

            if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Refund amount must be strictly positive.");
            }

            // Validate that original receipt exists in shift ledger or patient history
            boolean receiptFound = false;
            for (Receipt r : patient.getBillingHistory()) {
                if (r.getReceiptId().equalsIgnoreCase(originalReceiptId.trim())) {
                    if (refundAmount.compareTo(r.getAmountPaid()) > 0) {
                        throw new IllegalArgumentException("Refund amount cannot exceed original charge ($" + r.getAmountPaid() + ").");
                    }
                    receiptFound = true;
                    break;
                }
            }

            if (!receiptFound) {
                throw new NoSuchElementException("Original receipt [" + originalReceiptId + "] not found in patient history.");
            }

            String fullDescription = String.format("REFUND [%s] AuthBy: %s (%s) | Reason: %s",
                    originalReceiptId, authorizingSupervisor.getFullName(), authorizingSupervisor.getUsername(),
                    (refundReason == null ? "Clinical Reimbursement" : refundReason.trim()));

            Receipt refundReceipt = new Receipt(
                    patient.getNationalId(),
                    this.getNationalId(),
                    refundAmount,
                    PaymentRecord.PaymentMethod.CASH,
                    fullDescription,
                    "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                    timestamp
            );

            this.refundLedger.add(refundReceipt);
            patient.addReceipt(refundReceipt);

            return refundReceipt;
        } finally {
            posLock.unlock();
        }
    }

    // =========================================================================
    // TELEMETRY & LEDGER CALCULATIONS (GUI DATA-BINDING)
    // =========================================================================

    public BigDecimal calculateGrossSales() {
        posLock.lock();
        try {
            BigDecimal total = BigDecimal.ZERO;
            for (Receipt r : shiftLedger) {
                total = total.add(r.getAmountPaid());
            }
            return total;
        } finally {
            posLock.unlock();
        }
    }

    public BigDecimal calculateTotalRefunds() {
        posLock.lock();
        try {
            BigDecimal total = BigDecimal.ZERO;
            for (Receipt r : refundLedger) {
                total = total.add(r.getAmountPaid());
            }
            return total;
        } finally {
            posLock.unlock();
        }
    }

    public BigDecimal calculateNetRevenue() {
        return calculateGrossSales().subtract(calculateTotalRefunds());
    }

    public BigDecimal calculatePaymentMethodTotal(PaymentRecord.PaymentMethod method) {
        Objects.requireNonNull(method, "Payment method cannot be null.");
        posLock.lock();
        try {
            BigDecimal total = BigDecimal.ZERO;
            for (Receipt r : shiftLedger) {
                if (r.getPaymentMethod() == method) {
                    total = total.add(r.getAmountPaid());
                }
            }
            return total;
        } finally {
            posLock.unlock();
        }
    }

    public Optional<Receipt> findReceiptById(String receiptId) {
        if (receiptId == null || receiptId.trim().isEmpty()) {
            return Optional.empty();
        }
        posLock.lock();
        try {
            for (Receipt r : shiftLedger) {
                if (r.getReceiptId().equalsIgnoreCase(receiptId.trim())) {
                    return Optional.of(r);
                }
            }
            for (Receipt r : refundLedger) {
                if (r.getReceiptId().equalsIgnoreCase(receiptId.trim())) {
                    return Optional.of(r);
                }
            }
            return Optional.empty();
        } finally {
            posLock.unlock();
        }
    }

    public List<Receipt> getShiftLedger() {
        posLock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(shiftLedger));
        } finally {
            posLock.unlock();
        }
    }

    public List<Receipt> getRefundLedger() {
        posLock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(refundLedger));
        } finally {
            posLock.unlock();
        }
    }

    public List<ShiftReconciliationReport> getHistoricalReports() {
        posLock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(historicalReports));
        } finally {
            posLock.unlock();
        }
    }

    // =========================================================================
    // GETTERS, SETTERS & DISPLAY FORMATTERS
    // =========================================================================

    public String getRegisterTerminalId() { return registerTerminalId; }
    public boolean isOnDuty() { return onDuty; }
    public String getCurrentShiftId() { return currentShiftId; }
    public SystemClock getShiftStartTime() { return shiftStartTime; }
    public BigDecimal getCurrentOpeningFloat() { return currentOpeningFloat; }

    public BigDecimal getMaxSingleTransactionLimit() { return maxSingleTransactionLimit; }
    public void setMaxSingleTransactionLimit(BigDecimal limit) {
        if (limit == null || limit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Limit must be positive.");
        }
        this.maxSingleTransactionLimit = limit;
    }

    public String getFormattedNetRevenue() {
        return String.format("$%,.2f", calculateNetRevenue());
    }

    public String getFormattedCashCollected() {
        return String.format("$%,.2f", calculatePaymentMethodTotal(PaymentRecord.PaymentMethod.CASH));
    }

    public String getOperationalStatusDisplay() {
        return onDuty
                ? String.format("ACTIVE SHIFT [%s] | Net: %s | Vouchers: %d",
                currentShiftId, getFormattedNetRevenue(), shiftLedger.size())
                : "TERMINAL CLOSED / STANDBY";
    }

    @Override
    public String toString() {
        return super.toString() + String.format(
                " | Terminal: %s | Status: %s | Net Revenue: %s",
                registerTerminalId, onDuty ? "ON-DUTY" : "OFF-DUTY", getFormattedNetRevenue()
        );
    }
}