package models;

import system.ClinicMemory;
import system.SystemClock;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.io.Serial;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Enterprise Hospital Administrator Entity and Security Controller.
 * Enforces role-based permissions, PBKDF2 credential derivation with salt,
 * automated brute-force account lockout with cool-down timers, emergency
 * break-glass recovery tokens, staff payroll disbursement, and backup orchestration.
 */
public final class Admin extends Staff {
    @Serial
    private static final long serialVersionUID = 20L;

    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long COOLDOWN_SECONDS = 900; // 15 Minutes

    public enum AdminRole {
        SUPER_ADMIN("Super Administrator", true, true, true, true),
        CLINICAL_ADMIN("Clinical Operations Manager", true, false, true, false),
        FINANCIAL_ADMIN("Chief Financial Controller", false, true, false, true),
        AUDITOR("Compliance & Security Auditor", false, false, true, false);

        private final String displayTitle;
        private final boolean canManageStaff;
        private final boolean canManageFinances;
        private final boolean canViewAuditLogs;
        private final boolean canExportBackups;

        AdminRole(String displayTitle, boolean canManageStaff, boolean canManageFinances,
                  boolean canViewAuditLogs, boolean canExportBackups) {
            this.displayTitle = displayTitle;
            this.canManageStaff = canManageStaff;
            this.canManageFinances = canManageFinances;
            this.canViewAuditLogs = canViewAuditLogs;
            this.canExportBackups = canExportBackups;
        }

        public String getDisplayTitle() { return displayTitle; }
        public boolean canManageStaff() { return canManageStaff; }
        public boolean canManageFinances() { return canManageFinances; }
        public boolean canViewAuditLogs() { return canViewAuditLogs; }
        public boolean canExportBackups() { return canExportBackups; }

        @Override
        public String toString() { return displayTitle; }
    }

    private final String username;
    private byte[] passwordHash;
    private byte[] salt;
    private AdminRole role;
    private int failedLoginAttempts;
    private Long lockoutReleaseEpochSecond;
    private SystemClock lastLoginTimestamp;
    private final String recoveryKey;

    /**
     * Primary constructor provisioning an administrative staff account.
     */
    public Admin(String nationalId, String fullName, Integer age, String contactNumber,
                 BigDecimal baseSalary, String username, String plainPassword, AdminRole role) {

        super(nationalId, fullName, age, contactNumber, baseSalary);

        this.username = validateUsername(username);
        this.role = Objects.requireNonNull(role, "Admin role cannot be null.");
        this.recoveryKey = "RECOVERY-" + UUID.randomUUID().toString().toUpperCase();
        this.failedLoginAttempts = 0;
        this.lockoutReleaseEpochSecond = null;
        this.lastLoginTimestamp = null;

        setPassword(plainPassword);
    }

    /**
     * Protected copy constructor for identity migration.
     */
    private Admin(String newNationalId, Admin source) {
        super(newNationalId, source);
        this.username = source.username;
        this.passwordHash = source.passwordHash.clone();
        this.salt = source.salt.clone();
        this.role = source.role;
        this.failedLoginAttempts = source.failedLoginAttempts;
        this.lockoutReleaseEpochSecond = source.lockoutReleaseEpochSecond;
        this.lastLoginTimestamp = source.lastLoginTimestamp;
        this.recoveryKey = source.recoveryKey;
    }

    @Override
    public Person cloneWithNewId(String newNationalId) {
        return new Admin(newNationalId, this);
    }

    public static Admin createRootAdmin(String nationalId, String fullName, String contactNumber,
                                        String username, String plainPassword) {
        return new Admin(
                nationalId,
                fullName,
                35,
                contactNumber,
                new BigDecimal("6000.00"),
                username,
                plainPassword,
                AdminRole.SUPER_ADMIN
        );
    }

    private String validateUsername(String u) {
        if (u == null || u.trim().isEmpty()) {
            throw new IllegalArgumentException("Administrator username cannot be null or blank.");
        }
        return u.trim();
    }

    private void setPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty.");
        }
        this.salt = new byte[16];
        new SecureRandom().nextBytes(this.salt);
        this.passwordHash = hashPassword(plainPassword.toCharArray(), this.salt);
    }

    private static byte[] hashPassword(char[] passwordChars, byte[] saltBytes) {
        try {
            KeySpec spec = new PBEKeySpec(passwordChars, saltBytes, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Cryptographic provider error during credential derivation.", e);
        }
    }

    // =========================================================================
    // AUTHENTICATION & ACCESS CONTROL
    // =========================================================================

    public boolean authenticate(String attemptPassword, SystemClock verificationTime) {
        Objects.requireNonNull(verificationTime, "Verification timestamp cannot be null.");

        if (isAccountLocked()) {
            if (verificationTime.toEpochSecond() >= lockoutReleaseEpochSecond) {
                // Cooldown elapsed: automatically clear lock
                this.failedLoginAttempts = 0;
                this.lockoutReleaseEpochSecond = null;
            } else {
                throw new IllegalStateException("Account is temporarily locked. Cooldown remaining: " + getFormattedRemainingCooldown());
            }
        }

        if (attemptPassword == null) {
            registerFailedAttempt(verificationTime);
            return false;
        }

        byte[] attemptHash = hashPassword(attemptPassword.toCharArray(), this.salt);
        boolean matches = MessageDigest.isEqual(this.passwordHash, attemptHash);

        if (matches) {
            this.failedLoginAttempts = 0;
            this.lockoutReleaseEpochSecond = null;
            this.lastLoginTimestamp = verificationTime;
            return true;
        } else {
            registerFailedAttempt(verificationTime);
            return false;
        }
    }

    private void registerFailedAttempt(SystemClock timestamp) {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            this.lockoutReleaseEpochSecond = timestamp.toEpochSecond() + COOLDOWN_SECONDS;
        }
    }

    public boolean isAccountLocked() {
        if (lockoutReleaseEpochSecond == null) {
            return false;
        }
        return SystemClock.now().toEpochSecond() < lockoutReleaseEpochSecond;
    }

    public String getFormattedRemainingCooldown() {
        if (!isAccountLocked()) {
            return "0m 0s";
        }
        long diff = lockoutReleaseEpochSecond - SystemClock.now().toEpochSecond();
        if (diff <= 0) return "0m 0s";
        long min = diff / 60;
        long sec = diff % 60;
        return min + "m " + sec + "s";
    }

    public boolean emergencyUnlockWithRecoveryKey(String keyInput) {
        if (keyInput != null && this.recoveryKey.equalsIgnoreCase(keyInput.trim())) {
            this.failedLoginAttempts = 0;
            this.lockoutReleaseEpochSecond = null;
            return true;
        }
        return false;
    }

    public void changePassword(String currentPassword, String newPassword) {
        if (!authenticate(currentPassword, SystemClock.now())) {
            throw new SecurityException("Current password authentication failed. Password update rejected.");
        }
        setPassword(newPassword);
    }

    public void unlockBy(Admin supervisor) {
        Objects.requireNonNull(supervisor, "Supervisor administrator cannot be null.");
        if (supervisor.getRole() != AdminRole.SUPER_ADMIN) {
            throw new SecurityException("Access Denied: Only Super Administrators possess account unlock authority.");
        }
        this.failedLoginAttempts = 0;
        this.lockoutReleaseEpochSecond = null;
    }

    // =========================================================================
    // CLINICAL WORKFORCE & FINANCIAL GOVERNANCE
    // =========================================================================

    /**
     * Authoritative Staff Salary Disbursement:
     * Issues an immutable PaymentRecord and commits it to the target staff ledger.
     */
    public PaymentRecord payStaffSalary(Staff staff, PaymentRecord.PaymentMethod method,
                                        String destinationAccount, String referenceNumber) {

        Objects.requireNonNull(staff, "Staff recipient cannot be null.");
        Objects.requireNonNull(method, "Disbursement method cannot be null.");

        if (!this.role.canManageFinances()) {
            throw new SecurityException("Permission Denied: Admin role [" + role.getDisplayTitle() + "] cannot disburse payroll.");
        }

        String description = "Monthly Salary Disbursement by Admin " + getFullName() + " (" + getUsername() + ")";

        // Exactly 7 arguments passed:
        PaymentRecord record = new PaymentRecord(
                staff.getNationalId(),
                staff.getBaseSalary(),
                method,
                destinationAccount,
                referenceNumber,
                description,
                SystemClock.now()
        );

        staff.addPaymentRecord(record);
        return record;
    }

    public File exportSystemBackup(ClinicMemory memory, File backupDirectory, char[] masterPassword) throws Exception {
        Objects.requireNonNull(memory, "Memory repository cannot be null.");
        if (!this.role.canExportBackups()) {
            throw new SecurityException("Permission Denied: Admin role [" + role.getDisplayTitle() + "] cannot export backups.");
        }
        return memory.createSnapshotBackup(backupDirectory, masterPassword);
    }

    public boolean verifyBackupFile(File backupFile, char[] masterPassword) {
        return ClinicMemory.verifyBackupIntegrity(backupFile, masterPassword);
    }

    // =========================================================================
    // GETTERS, SETTERS & TELEMETRY (GUI BINDING)
    // =========================================================================

    public String getUsername() { return username; }
    public AdminRole getRole() { return role; }
    public void setRole(AdminRole role) { this.role = Objects.requireNonNull(role, "Role cannot be null."); }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public SystemClock getLastLoginTimestamp() { return lastLoginTimestamp; }
    public String getRecoveryKey() { return recoveryKey; }

    public String getAccountStatusDisplay() {
        if (isAccountLocked()) {
            return "LOCKED (" + getFormattedRemainingCooldown() + ")";
        }
        return "ACTIVE";
    }

    public String getLastLoginDisplay() {
        return (lastLoginTimestamp != null) ? lastLoginTimestamp.toFullDisplayString() : "Never Logged In";
    }

    @Override
    public String toString() {
        return super.toString() + String.format(
                " | Username: %s | Role: %s | Status: %s | Last Access: %s",
                username, role.getDisplayTitle(), getAccountStatusDisplay(), getLastLoginDisplay()
        );
    }
}