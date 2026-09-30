package models;

import system.SystemClock;
import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;

@Entity
@Table(name = "staff")
@Inheritance(strategy = InheritanceType.JOINED)
public class Staff extends Person implements Serializable {

    @Serial
    private static final long serialVersionUID = 15L;

    public enum EmploymentStatus {
        ACTIVE("Active Duty"),
        ON_LEAVE("On Approved Leave"),
        SUSPENDED("Temporarily Suspended"),
        TERMINATED("Separated / Concluded");

        private final String displayTitle;
        EmploymentStatus(String displayTitle) { this.displayTitle = displayTitle; }
        public String getDisplayTitle() { return displayTitle; }
    }

    @Column(name = "staff_id", unique = true, nullable = false, updatable = false)
    private String staffId;

    @Column(name = "username", unique = true, nullable = false)
    private String username;

    @Column(name = "encoded_password")
    private String encodedPassword;

    @Column(name = "role_name", nullable = false)
    private String role;

    @Column(name = "base_salary", precision = 12, scale = 2)
    private BigDecimal baseSalary;

    @Column(name = "hire_date", updatable = false)
    private SystemClock hireDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", nullable = false)
    private EmploymentStatus employmentStatus;

    @Transient
    private List<PaymentRecord> paymentHistory = new ArrayList<>();

    @Column(name = "last_paid_month")
    private int lastPaidMonthNumber;

    protected Staff() {}

    public Staff(String nationalId, String fullName, Integer age, String contactNumber,
                 String username, String role, BigDecimal baseSalary,
                 SystemClock hireDate, EmploymentStatus employmentStatus) {
        super(nationalId, fullName, age, contactNumber);
        this.staffId = "EMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.username = (username != null && !username.isBlank()) ? username.trim() : nationalId;
        this.role = (role != null) ? role : "STAFF";
        this.baseSalary = baseSalary != null ? baseSalary : BigDecimal.ZERO;
        this.hireDate = (hireDate != null) ? hireDate : SystemClock.now();
        this.employmentStatus = (employmentStatus != null) ? employmentStatus : EmploymentStatus.ACTIVE;
        this.lastPaidMonthNumber = 0;
    }

    public Staff(String nationalId, String fullName, Integer age, String contactNumber,
                 String username, String encodedPassword, String role, BigDecimal baseSalary,
                 SystemClock hireDate, EmploymentStatus employmentStatus) {
        this(nationalId, fullName, age, contactNumber, username, role, baseSalary, hireDate, employmentStatus);
        this.encodedPassword = encodedPassword;
    }

    protected Staff(String newNationalId, Staff source) {
        super(newNationalId, source);
        this.staffId = source.staffId;
        this.username = source.username;
        this.encodedPassword = source.encodedPassword;
        this.role = source.role;
        this.baseSalary = source.baseSalary;
        this.hireDate = source.hireDate;
        this.employmentStatus = source.employmentStatus;
        this.lastPaidMonthNumber = source.lastPaidMonthNumber;
    }

    @Override
    public Person cloneWithNewId(String newId) {
        return new Staff(newId, this);
    }

    public String getStaffId() { return staffId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username.trim(); }
    public String getEncodedPassword() { return encodedPassword; }
    public void setEncodedPassword(String encodedPassword) { this.encodedPassword = encodedPassword; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isActive() { return this.employmentStatus == EmploymentStatus.ACTIVE; }
    public void setActive(boolean active) { this.employmentStatus = active ? EmploymentStatus.ACTIVE : EmploymentStatus.SUSPENDED; }
    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }
    public SystemClock getHireDate() { return hireDate; }
    public EmploymentStatus getEmploymentStatus() { return employmentStatus; }
}