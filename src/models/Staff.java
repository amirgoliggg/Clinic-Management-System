package models;

import system.SystemClock;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;

/**
 * Enterprise Abstract Base Staff Entity.
 * Represents all employed healthcare and administrative personnel.
 * Enforces strict financial encapsulation, employment lifecycle states,
 * immutable historical payroll records, and GUI table data-binding.
 */
public abstract class Staff extends Person implements Serializable {
    @Serial
    private static final long serialVersionUID = 15L;

    public enum EmploymentStatus {
        ACTIVE("Active Duty"),
        ON_LEAVE("On Approved Leave"),
        SUSPENDED("Temporarily Suspended"),
        TERMINATED("Separated / Concluded");

        private final String displayTitle;

        EmploymentStatus(String displayTitle) {
            this.displayTitle = displayTitle;
        }

        public String getDisplayTitle() {
            return displayTitle;
        }

        @Override
        public String toString() {
            return displayTitle;
        }
    }

    private BigDecimal baseSalary;
    private final SystemClock hireDate;
    private EmploymentStatus employmentStatus;
    private final List<PaymentRecord> paymentHistory;

    /**
     * Primary constructor for onboarding new hospital personnel.
     */
    public Staff(String nationalId, String fullName, Integer age, String contactNumber, BigDecimal baseSalary) {
        this(nationalId, fullName, age, contactNumber, baseSalary, SystemClock.now(), EmploymentStatus.ACTIVE);
    }

    /**
     * Extended constructor supporting explicit onboarding timestamp and operational status.
     */
    public Staff(String nationalId, String fullName, Integer age, String contactNumber,
                 BigDecimal baseSalary, SystemClock hireDate, EmploymentStatus employmentStatus) {

        super(nationalId, fullName, age, contactNumber);

        setBaseSalary(baseSalary);
        this.hireDate = Objects.requireNonNull(hireDate, "Hire date cannot be null.");
        this.employmentStatus = Objects.requireNonNull(employmentStatus, "Employment status cannot be null.");
        this.paymentHistory = new ArrayList<>();
    }

    /**
     * Protected Copy Constructor for Identity Migration (ID change).
     */
    protected Staff(String newNationalId, Staff source) {
        super(newNationalId, source);
        this.baseSalary = source.baseSalary;
        this.hireDate = source.hireDate;
        this.employmentStatus = source.employmentStatus;
        this.paymentHistory = new ArrayList<>(source.paymentHistory);
    }

    // =========================================================================
    // PAYROLL & COMPENSATION OPERATIONS
    // =========================================================================

    public BigDecimal getBaseSalary() {
        return baseSalary;
    }

    public void setBaseSalary(BigDecimal baseSalary) {
        if (baseSalary == null || baseSalary.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Base salary cannot be null or negative.");
        }
        this.baseSalary = baseSalary;
    }

    public String getFormattedBaseSalary() {
        return String.format("$%,.2f", baseSalary);
    }

    /**
     * Appends an authoritative salary voucher to the staff member's ledger.
     */
    public void addPaymentRecord(PaymentRecord record) {
        Objects.requireNonNull(record, "Payment record cannot be null.");
        if (!record.getStaffNationalId().equalsIgnoreCase(this.getNationalId())) {
            throw new IllegalArgumentException("Cross-validation Error: Payment recipient ID (" +
                    record.getStaffNationalId() + ") does not match staff ID (" + this.getNationalId() + ").");
        }
        this.paymentHistory.add(record);
    }

    public List<PaymentRecord> getPaymentHistory() {
        return Collections.unmodifiableList(new ArrayList<>(paymentHistory));
    }

    public Optional<PaymentRecord> getLatestPaymentRecord() {
        if (paymentHistory.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(paymentHistory.get(paymentHistory.size() - 1));
    }

    public BigDecimal calculateTotalSalaryPaid() {
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentRecord pr : paymentHistory) {
            total = total.add(pr.getAmount());
        }
        return total;
    }

    public String getFormattedTotalSalaryPaid() {
        return String.format("$%,.2f", calculateTotalSalaryPaid());
    }

    /**
     * Clinical Payroll Safeguard: Verifies whether monthly compensation was already disbursed.
     */
    public boolean isSalaryPaidForCurrentMonth(SystemClock clock) {
        Objects.requireNonNull(clock, "Clock reference cannot be null.");
        for (PaymentRecord pr : paymentHistory) {
            if (pr.getTimestamp().getJalaliYear() == clock.getJalaliYear() &&
                    pr.getTimestamp().getJalaliMonth() == clock.getJalaliMonth()) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // EMPLOYMENT STATUS & METADATA
    // =========================================================================

    public SystemClock getHireDate() {
        return hireDate;
    }

    public EmploymentStatus getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(EmploymentStatus employmentStatus) {
        this.employmentStatus = Objects.requireNonNull(employmentStatus, "Employment status cannot be null.");
    }

    public boolean isActive() {
        return this.employmentStatus == EmploymentStatus.ACTIVE;
    }

    @Override
    public String toString() {
        return super.toString() + String.format(
                " | Base: %s | Status: %s | Hired: %s | Paid Records: %d",
                getFormattedBaseSalary(), employmentStatus.getDisplayTitle(),
                hireDate.toDateString(), paymentHistory.size()
        );
    }
}