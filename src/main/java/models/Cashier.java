package models;

import system.SystemClock;
import jakarta.persistence.*;
import java.io.Serial;
import java.math.BigDecimal;

@Entity
@Table(name = "cashiers")
public final class Cashier extends Staff {

    @Serial
    private static final long serialVersionUID = 35L;

    @Column(name = "max_single_transaction_limit", precision = 12, scale = 2)
    private BigDecimal maxSingleTransactionLimit;

    @Column(name = "workstation_id")
    private String workstationId;

    protected Cashier() {}

    // سازنده با سقف تراکنش عددی
    public Cashier(String nationalId, String fullName, Integer age, String contactNumber,
                   BigDecimal baseSalary, BigDecimal maxSingleTransactionLimit) {
        super(nationalId, fullName, age, contactNumber, nationalId, "CASHIER", baseSalary, SystemClock.now(), EmploymentStatus.ACTIVE);
        this.maxSingleTransactionLimit = maxSingleTransactionLimit != null ? maxSingleTransactionLimit : new BigDecimal("50000.00");
        this.workstationId = "POS-01";
    }

    // سازنده با شناسه ایستگاه متنی
    public Cashier(String nationalId, String fullName, Integer age, String contactNumber,
                   BigDecimal baseSalary, String workstationId) {
        super(nationalId, fullName, age, contactNumber, nationalId, "CASHIER", baseSalary, SystemClock.now(), EmploymentStatus.ACTIVE);
        this.maxSingleTransactionLimit = new BigDecimal("50000.00");
        this.workstationId = workstationId != null ? workstationId : "POS-01";
    }

    // سازنده جامع
    public Cashier(String nationalId, String fullName, Integer age, String contactNumber,
                   BigDecimal baseSalary, BigDecimal maxSingleTransactionLimit, String workstationId) {
        super(nationalId, fullName, age, contactNumber, nationalId, "CASHIER", baseSalary, SystemClock.now(), EmploymentStatus.ACTIVE);
        this.maxSingleTransactionLimit = maxSingleTransactionLimit != null ? maxSingleTransactionLimit : new BigDecimal("50000.00");
        this.workstationId = workstationId != null ? workstationId : "POS-01";
    }

    public BigDecimal getMaxSingleTransactionLimit() {
        return maxSingleTransactionLimit != null ? maxSingleTransactionLimit : new BigDecimal("50000.00");
    }

    public void setMaxSingleTransactionLimit(BigDecimal maxSingleTransactionLimit) {
        this.maxSingleTransactionLimit = maxSingleTransactionLimit;
    }

    public String getWorkstationId() {
        return workstationId;
    }

    public void setWorkstationId(String workstationId) {
        this.workstationId = workstationId;
    }
}