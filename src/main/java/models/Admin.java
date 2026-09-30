package models;

import system.SystemClock;
import jakarta.persistence.*;
import java.io.Serial;
import java.math.BigDecimal;

@Entity
@Table(name = "admins")
public final class Admin extends Staff {

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

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "account_locked")
    private boolean accountLocked = false;

    protected Admin() {}

    public Admin(String nationalId, String fullName, Integer age, String contactNumber,
                 BigDecimal baseSalary, String username, String passwordHash, AdminRole adminRole) {
        super(nationalId, fullName, age, contactNumber, username, passwordHash, "ADMIN", baseSalary, SystemClock.now(), EmploymentStatus.ACTIVE);
        this.passwordHash = passwordHash;
        this.adminRole = (adminRole != null) ? adminRole : AdminRole.SUPER_ADMIN;
        this.accountLocked = false;
        super.setEncodedPassword(passwordHash);
    }

    public AdminRole getAdminRole() {
        return adminRole;
    }

    public void setAdminRole(AdminRole adminRole) {
        this.adminRole = adminRole;
    }

    public String getPasswordHash() {
        return (passwordHash != null && !passwordHash.isBlank()) ? passwordHash : getEncodedPassword();
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        super.setEncodedPassword(passwordHash);
    }

    @Override
    public String getEncodedPassword() {
        if (this.passwordHash != null && !this.passwordHash.isBlank()) {
            return this.passwordHash;
        }
        return super.getEncodedPassword();
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