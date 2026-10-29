package com.clinic.models;

import com.clinic.system.SystemClock;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "staff")
@PrimaryKeyJoinColumn(name = "national_id")
public class Staff extends Person implements Serializable {

    @Serial
    private static final long serialVersionUID = 15L;

    private static final String STAFF_ID_PREFIX = "EMP-";
    private static final String DEFAULT_ROLE = "STAFF";

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
    }

    @Column(name = "staff_id", unique = true, nullable = false, updatable = false)
    private String staffId;

    @Column(name = "username", unique = true, nullable = false)
    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
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

    @JsonIgnore
    @Transient
    private List<PaymentRecord> paymentHistory = new ArrayList<>();

    @Column(name = "last_paid_month")
    private int lastPaidMonthNumber;

    // ==================== Constructors ====================

    protected Staff() {
        // Required by JPA
    }

    public Staff(String nationalId, String fullName, Integer age, String contactNumber,
                 String username, String role, BigDecimal baseSalary,
                 SystemClock hireDate, EmploymentStatus employmentStatus) {
        super(nationalId, fullName, age, contactNumber);
        this.staffId = generateStaffId();
        this.username = (username != null && !username.isBlank()) ? username.trim() : getNationalId();
        this.role = (role != null && !role.isBlank()) ? role.trim() : DEFAULT_ROLE;
        this.baseSalary = (baseSalary != null) ? baseSalary : BigDecimal.ZERO;
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
        this.staffId = generateStaffId();
        this.username = source.username;
        this.encodedPassword = source.encodedPassword;
        this.role = source.role;
        this.baseSalary = source.baseSalary;
        this.hireDate = source.hireDate;
        this.employmentStatus = source.employmentStatus;
        this.lastPaidMonthNumber = source.lastPaidMonthNumber;
    }

    // ==================== Lifecycle & Helper Methods ====================

    @PrePersist
    public void ensureStaffDefaults() {
        if (this.staffId == null || this.staffId.isBlank()) {
            this.staffId = generateStaffId();
        }
        if (this.username == null || this.username.isBlank()) {
            this.username = getNationalId();
        }
        if (this.role == null || this.role.isBlank()) {
            this.role = DEFAULT_ROLE;
        }
        if (this.baseSalary == null) {
            this.baseSalary = BigDecimal.ZERO;
        }
        if (this.hireDate == null) {
            this.hireDate = SystemClock.now();
        }
        if (this.employmentStatus == null) {
            this.employmentStatus = EmploymentStatus.ACTIVE;
        }
    }

    private static String generateStaffId() {
        return STAFF_ID_PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public Person cloneWithNewId(String newId) {
        return new Staff(newId, this);
    }

    // ==================== Getters & Setters ====================

    public String getStaffId() {
        return staffId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = (username != null && !username.isBlank()) ? username.trim() : getNationalId();
    }

    @JsonIgnore
    public String getEncodedPassword() {
        return encodedPassword;
    }

    public void setEncodedPassword(String encodedPassword) {
        this.encodedPassword = encodedPassword;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = (role != null && !role.isBlank()) ? role.trim() : DEFAULT_ROLE;
    }

    public boolean isActive() {
        return this.employmentStatus == EmploymentStatus.ACTIVE;
    }

    public void setActive(boolean active) {
        this.employmentStatus = active ? EmploymentStatus.ACTIVE : EmploymentStatus.SUSPENDED;
    }

    public BigDecimal getBaseSalary() {
        return baseSalary;
    }

    public void setBaseSalary(BigDecimal baseSalary) {
        this.baseSalary = (baseSalary != null) ? baseSalary : BigDecimal.ZERO;
    }

    public SystemClock getHireDate() {
        return hireDate;
    }

    public EmploymentStatus getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(EmploymentStatus employmentStatus) {
        this.employmentStatus = (employmentStatus != null) ? employmentStatus : EmploymentStatus.ACTIVE;
    }

    public int getLastPaidMonthNumber() {
        return lastPaidMonthNumber;
    }

    public void setLastPaidMonthNumber(int lastPaidMonthNumber) {
        this.lastPaidMonthNumber = lastPaidMonthNumber;
    }

    @JsonIgnore
    public List<PaymentRecord> getPaymentHistory() {
        return paymentHistory;
    }
}