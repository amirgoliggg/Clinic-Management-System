package com.clinic.models;

import com.clinic.system.SystemClock;
import jakarta.persistence.*;

import java.io.Serial;
import java.math.BigDecimal;

@Entity
@Table(name = "cashiers")
@PrimaryKeyJoinColumn(name = "national_id")
public class Cashier extends Staff {

    @Serial
    private static final long serialVersionUID = 35L;

    private static final String ROLE_CASHIER = "CASHIER";
    private static final String DEFAULT_WORKSTATION_ID = "POS-01";
    private static final BigDecimal DEFAULT_TRANSACTION_LIMIT = new BigDecimal("50000.00");

    @Column(name = "max_single_transaction_limit", precision = 12, scale = 2, nullable = false)
    private BigDecimal maxSingleTransactionLimit;

    @Column(name = "workstation_id", nullable = false)
    private String workstationId;

    // ==================== Constructors ====================

    protected Cashier() {
        super();
    }

    public Cashier(String nationalId, String fullName, Integer age, String contactNumber,
                   BigDecimal baseSalary, BigDecimal maxSingleTransactionLimit) {
        this(nationalId, fullName, age, contactNumber, baseSalary, maxSingleTransactionLimit, DEFAULT_WORKSTATION_ID);
    }

    public Cashier(String nationalId, String fullName, Integer age, String contactNumber,
                   BigDecimal baseSalary, String workstationId) {
        this(nationalId, fullName, age, contactNumber, baseSalary, DEFAULT_TRANSACTION_LIMIT, workstationId);
    }

    public Cashier(String nationalId, String fullName, Integer age, String contactNumber,
                   BigDecimal baseSalary, BigDecimal maxSingleTransactionLimit, String workstationId) {
        super(nationalId, fullName, age, contactNumber, nationalId,
                ROLE_CASHIER, baseSalary, SystemClock.now(), EmploymentStatus.ACTIVE);
        this.maxSingleTransactionLimit = normalizeLimit(maxSingleTransactionLimit);
        this.workstationId = normalizeWorkstation(workstationId);
    }

    private Cashier(String newNationalId, Cashier source) {
        super(newNationalId, source);
        this.maxSingleTransactionLimit = source.maxSingleTransactionLimit;
        this.workstationId = source.workstationId;
    }

    // ==================== Lifecycle & Helper Methods ====================

    @PrePersist
    public void ensureCashierDefaults() {
        super.ensureStaffDefaults();
        this.maxSingleTransactionLimit = normalizeLimit(this.maxSingleTransactionLimit);
        this.workstationId = normalizeWorkstation(this.workstationId);
    }

    @Override
    public Person cloneWithNewId(String newNationalId) {
        return new Cashier(newNationalId, this);
    }

    private static BigDecimal normalizeLimit(BigDecimal limit) {
        return (limit != null && limit.compareTo(BigDecimal.ZERO) > 0) ? limit : DEFAULT_TRANSACTION_LIMIT;
    }

    private static String normalizeWorkstation(String workstation) {
        return (workstation != null && !workstation.isBlank()) ? workstation.trim() : DEFAULT_WORKSTATION_ID;
    }

    // ==================== Domain Business Logic ====================

    public boolean canProcessAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return isActive() && amount.compareTo(getMaxSingleTransactionLimit()) <= 0;
    }

    public void validateTransactionLimit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("مبلغ تراکنش باید بزرگ‌تر از صفر باشد.");
        }
        if (amount.compareTo(getMaxSingleTransactionLimit()) > 0) {
            throw new IllegalStateException("مبلغ تراکنش از سقف مجاز صندوقدار بیشتر است.");
        }
    }

    // ==================== Getters & Setters ====================

    public BigDecimal getMaxSingleTransactionLimit() {
        return normalizeLimit(maxSingleTransactionLimit);
    }

    public void setMaxSingleTransactionLimit(BigDecimal maxSingleTransactionLimit) {
        if (maxSingleTransactionLimit != null && maxSingleTransactionLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("سقف تراکنش صندوقدار باید بزرگ‌تر از صفر باشد.");
        }
        this.maxSingleTransactionLimit = normalizeLimit(maxSingleTransactionLimit);
    }

    public String getWorkstationId() {
        return workstationId;
    }

    public void setWorkstationId(String workstationId) {
        this.workstationId = normalizeWorkstation(workstationId);
    }
}