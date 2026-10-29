package com.clinic.models;

import com.clinic.system.SystemClock;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.io.Serial;
import java.math.BigDecimal;

@Entity
@Table(name = "admins")
@PrimaryKeyJoinColumn(name = "national_id")
public class Admin extends Staff {

    @Serial
    private static final long serialVersionUID = 20L;

    public enum AdminRole {
        SUPER_ADMIN, FINANCIAL_ADMIN, CLINICAL_ADMIN, HR_ADMIN, AUDITOR;

        public boolean canManageFinances() {
            return this == SUPER_ADMIN || this == FINANCIAL_ADMIN;
        }

        public boolean canManageStaff() {
            return this == SUPER_ADMIN || this == HR_ADMIN;
        }

        public boolean canAccessAuditLogs() {
            return this == SUPER_ADMIN || this == AUDITOR;
        }
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "admin_role", nullable = false)
    private AdminRole adminRole;

    @JsonIgnore
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "account_locked", nullable = false)
    private boolean accountLocked = false;

    // ==================== Constructors ====================

    protected Admin() {
        super();
    }

    public Admin(String nationalId, String fullName, Integer age, String contactNumber,
                 BigDecimal baseSalary, String username, String passwordHash, AdminRole adminRole) {
        super(nationalId, fullName, age, contactNumber, username, passwordHash,
                "ADMIN", baseSalary, SystemClock.now(), EmploymentStatus.ACTIVE);
        this.passwordHash = passwordHash;
        this.adminRole = (adminRole != null) ? adminRole : AdminRole.SUPER_ADMIN;
        this.accountLocked = false;
        super.setEncodedPassword(passwordHash);
    }

    private Admin(String newNationalId, Admin source) {
        super(newNationalId, source);
        this.adminRole = source.adminRole;
        this.passwordHash = source.passwordHash;
        this.accountLocked = source.accountLocked;
    }

    @Override
    public Person cloneWithNewId(String newNationalId) {
        return new Admin(newNationalId, this);
    }

    // ==================== Getters & Setters ====================

    public AdminRole getAdminRole() {
        return adminRole;
    }

    public void setAdminRole(AdminRole adminRole) {
        this.adminRole = (adminRole != null) ? adminRole : AdminRole.SUPER_ADMIN;
    }

    @JsonIgnore
    public String getPasswordHash() {
        return (passwordHash != null && !passwordHash.isBlank()) ? passwordHash : super.getEncodedPassword();
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        super.setEncodedPassword(passwordHash);
    }

    @Override
    @JsonIgnore
    public String getEncodedPassword() {
        return (this.passwordHash != null && !this.passwordHash.isBlank()) ? this.passwordHash : super.getEncodedPassword();
    }

    @Override
    public void setEncodedPassword(String encodedPassword) {
        super.setEncodedPassword(encodedPassword);
        this.passwordHash = encodedPassword;
    }

    public boolean isAccountLocked() {
        return accountLocked;
    }

    public void setAccountLocked(boolean accountLocked) {
        this.accountLocked = accountLocked;
    }
}