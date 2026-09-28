package system;

import enums.PatientStatus;
import models.*;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Enterprise In-Memory Data Repository and Cryptographic Persistence Engine.
 * Features:
 * 1. Resilient AES-256-GCM authenticated storage without stream descriptor sync bugs.
 * 2. High-throughput ReentrantReadWriteLock for concurrent GUI responsiveness.
 * 3. Blockchain-style cryptographic audit log ledger with tamper verification.
 * 4. Collision-free identity migration and granular role-segregated queries.
 */
public final class ClinicMemory implements Serializable {
    @Serial
    private static final long serialVersionUID = 100L;

    private static final String ENCRYPTION_ALGO = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int AES_KEY_SIZE_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;

    /**
     * Immutable cryptographic audit trail entry forming a tamper-proof Merkle-like chain.
     */
    public static final class AuditLogEntry implements Serializable {
        @Serial
        private static final long serialVersionUID = 101L;

        private final String entryId;
        private final SystemClock timestamp;
        private final String operatorId;
        private final String action;
        private final String details;
        private final String previousHash;
        private final String currentHash;

        public AuditLogEntry(String operatorId, String action, String details, String previousHash, SystemClock timestamp) {
            this.entryId = UUID.randomUUID().toString();
            this.operatorId = Objects.requireNonNull(operatorId, "Operator ID cannot be null.");
            this.action = Objects.requireNonNull(action, "Action cannot be null.");
            this.details = Objects.requireNonNull(details, "Details cannot be null.");
            this.previousHash = (previousHash == null || previousHash.trim().isEmpty()) ? "GENESIS" : previousHash;
            this.timestamp = Objects.requireNonNull(timestamp, "Timestamp cannot be null.");
            this.currentHash = calculateHash();
        }

        private String calculateHash() {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                String payload = entryId + timestamp.toFullDisplayString() + operatorId + action + details + previousHash;
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
            return this.currentHash.equals(calculateHash());
        }

        public String getEntryId() { return entryId; }
        public SystemClock getTimestamp() { return timestamp; }
        public String getOperatorId() { return operatorId; }
        public String getAction() { return action; }
        public String getDetails() { return details; }
        public String getPreviousHash() { return previousHash; }
        public String getCurrentHash() { return currentHash; }

        @Override
        public String toString() {
            return String.format("[%s] OP: %s | ACTION: %s | DETAILS: %s | SEAL: %s",
                    timestamp.toFullDisplayString(), operatorId, action, details, currentHash.substring(0, 12) + "...");
        }
    }

    private final Map<String, Patient> patients;
    private final Map<String, Staff> staffMembers;
    private final Map<String, Admin> adminsByUsername;
    private final List<AuditLogEntry> auditLogs;

    private transient ReentrantReadWriteLock rwLock;

    public ClinicMemory() {
        this.patients = new LinkedHashMap<>();
        this.staffMembers = new LinkedHashMap<>();
        this.adminsByUsername = new LinkedHashMap<>();
        this.auditLogs = new ArrayList<>();
        this.rwLock = new ReentrantReadWriteLock(true);
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.rwLock = new ReentrantReadWriteLock(true);
    }

    // =========================================================================
    // AUDIT LOGGING & BLOCKCHAIN CHAIN VERIFICATION
    // =========================================================================

    private void appendAuditLog(String operatorId, String action, String details, SystemClock timestamp) {
        String lastHash = auditLogs.isEmpty() ? "GENESIS" : auditLogs.get(auditLogs.size() - 1).getCurrentHash();
        AuditLogEntry entry = new AuditLogEntry(operatorId, action, details, lastHash, timestamp);
        this.auditLogs.add(entry);
    }

    public List<AuditLogEntry> getAuditLogs() {
        rwLock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(auditLogs));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<AuditLogEntry> getRecentAuditLogs(int limit) {
        rwLock.readLock().lock();
        try {
            if (limit <= 0 || auditLogs.isEmpty()) {
                return Collections.emptyList();
            }
            int fromIndex = Math.max(0, auditLogs.size() - limit);
            return Collections.unmodifiableList(new ArrayList<>(auditLogs.subList(fromIndex, auditLogs.size())));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public boolean verifyAuditChainIntegrity() {
        rwLock.readLock().lock();
        try {
            String expectedPreviousHash = "GENESIS";
            for (AuditLogEntry entry : auditLogs) {
                if (!entry.verifyIntegrity()) {
                    return false; // Internal record payload modified
                }
                if (!entry.getPreviousHash().equals(expectedPreviousHash)) {
                    return false; // Chain continuity broken or entries deleted
                }
                expectedPreviousHash = entry.getCurrentHash();
            }
            return true;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // =========================================================================
    // PATIENT DATA OPERATIONS & QUERIES
    // =========================================================================

    public void registerPatient(Patient patient, String operatorId) {
        Objects.requireNonNull(patient, "Patient cannot be null.");
        rwLock.writeLock().lock();
        try {
            String id = patient.getNationalId();
            if (patients.containsKey(id)) {
                throw new IllegalArgumentException("Patient with National ID " + id + " already registered.");
            }
            patients.put(id, patient);
            appendAuditLog(operatorId, "REGISTER_PATIENT", "Admitted patient ID: " + id + " (" + patient.getFullName() + ")", SystemClock.now());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public Optional<Patient> findPatientById(String nationalId) {
        if (nationalId == null || nationalId.trim().isEmpty()) {
            return Optional.empty();
        }
        rwLock.readLock().lock();
        try {
            return Optional.ofNullable(patients.get(nationalId.trim()));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<Patient> findPatientsByName(String nameQuery) {
        if (nameQuery == null || nameQuery.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = nameQuery.trim().toLowerCase();
        rwLock.readLock().lock();
        try {
            List<Patient> matches = new ArrayList<>();
            for (Patient p : patients.values()) {
                if (p.getFullName().toLowerCase().contains(normalized)) {
                    matches.add(p);
                }
            }
            return Collections.unmodifiableList(matches);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<Patient> getAllPatients() {
        rwLock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(patients.values()));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<Patient> findPatientsByStatus(PatientStatus status) {
        Objects.requireNonNull(status, "Patient status cannot be null.");
        rwLock.readLock().lock();
        try {
            List<Patient> result = new ArrayList<>();
            for (Patient p : patients.values()) {
                if (p.getStatus() == status) {
                    result.add(p);
                }
            }
            return Collections.unmodifiableList(result);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public int countPatientsByStatus(PatientStatus status) {
        Objects.requireNonNull(status, "Patient status cannot be null.");
        rwLock.readLock().lock();
        try {
            int count = 0;
            for (Patient p : patients.values()) {
                if (p.getStatus() == status) {
                    count++;
                }
            }
            return count;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // =========================================================================
    // STAFF QUERIES SEGREGATED BY ROLE (GUI DATA-BINDING)
    // =========================================================================

    public void registerStaff(Staff staff, String operatorId) {
        Objects.requireNonNull(staff, "Staff member cannot be null.");
        rwLock.writeLock().lock();
        try {
            String id = staff.getNationalId();
            if (staffMembers.containsKey(id)) {
                throw new IllegalArgumentException("Staff member with National ID " + id + " already registered.");
            }

            if (staff instanceof Admin) {
                Admin admin = (Admin) staff;
                String normalizedUsername = admin.getUsername().toLowerCase();
                if (adminsByUsername.containsKey(normalizedUsername)) {
                    throw new IllegalArgumentException("Administrator username '" + admin.getUsername() + "' is already in use.");
                }
                adminsByUsername.put(normalizedUsername, admin);
            }

            staffMembers.put(id, staff);
            appendAuditLog(operatorId, "REGISTER_STAFF", "Registered staff ID: " + id + " [" + staff.getClass().getSimpleName() + "]", SystemClock.now());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public Optional<Staff> findStaffById(String nationalId) {
        if (nationalId == null || nationalId.trim().isEmpty()) {
            return Optional.empty();
        }
        rwLock.readLock().lock();
        try {
            return Optional.ofNullable(staffMembers.get(nationalId.trim()));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<Staff> findStaffByName(String nameQuery) {
        if (nameQuery == null || nameQuery.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = nameQuery.trim().toLowerCase();
        rwLock.readLock().lock();
        try {
            List<Staff> matches = new ArrayList<>();
            for (Staff s : staffMembers.values()) {
                if (s.getFullName().toLowerCase().contains(normalized)) {
                    matches.add(s);
                }
            }
            return Collections.unmodifiableList(matches);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public Optional<Admin> findAdminByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }
        rwLock.readLock().lock();
        try {
            return Optional.ofNullable(adminsByUsername.get(username.trim().toLowerCase()));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<Staff> getAllStaff() {
        rwLock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(staffMembers.values()));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<Doctor> getAllDoctors() {
        rwLock.readLock().lock();
        try {
            List<Doctor> result = new ArrayList<>();
            for (Staff s : staffMembers.values()) {
                if (s instanceof Doctor) {
                    result.add((Doctor) s);
                }
            }
            return Collections.unmodifiableList(result);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<Nurse> getAllNurses() {
        rwLock.readLock().lock();
        try {
            List<Nurse> result = new ArrayList<>();
            for (Staff s : staffMembers.values()) {
                if (s instanceof Nurse) {
                    result.add((Nurse) s);
                }
            }
            return Collections.unmodifiableList(result);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<Cashier> getAllCashiers() {
        rwLock.readLock().lock();
        try {
            List<Cashier> result = new ArrayList<>();
            for (Staff s : staffMembers.values()) {
                if (s instanceof Cashier) {
                    result.add((Cashier) s);
                }
            }
            return Collections.unmodifiableList(result);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<Receptionist> getAllReceptionists() {
        rwLock.readLock().lock();
        try {
            List<Receptionist> result = new ArrayList<>();
            for (Staff s : staffMembers.values()) {
                if (s instanceof Receptionist) {
                    result.add((Receptionist) s);
                }
            }
            return Collections.unmodifiableList(result);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<Admin> getAllAdmins() {
        rwLock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(adminsByUsername.values()));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<Doctor> findDoctorsBySpecialization(Doctor.Specialization specialization) {
        Objects.requireNonNull(specialization, "Specialization cannot be null.");
        rwLock.readLock().lock();
        try {
            List<Doctor> result = new ArrayList<>();
            for (Staff s : staffMembers.values()) {
                if (s instanceof Doctor) {
                    Doctor d = (Doctor) s;
                    if (d.getSpecialization() == specialization) {
                        result.add(d);
                    }
                }
            }
            return Collections.unmodifiableList(result);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<Doctor> findAvailableDoctorsBySpecialization(Doctor.Specialization specialization) {
        Objects.requireNonNull(specialization, "Specialization cannot be null.");
        rwLock.readLock().lock();
        try {
            List<Doctor> result = new ArrayList<>();
            for (Staff s : staffMembers.values()) {
                if (s instanceof Doctor) {
                    Doctor d = (Doctor) s;
                    if (d.getSpecialization() == specialization && d.isAvailable() && !d.hasReachedDailyCapacity()) {
                        result.add(d);
                    }
                }
            }
            return Collections.unmodifiableList(result);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<Doctor> getAvailableDoctors() {
        rwLock.readLock().lock();
        try {
            List<Doctor> result = new ArrayList<>();
            for (Staff s : staffMembers.values()) {
                if (s instanceof Doctor) {
                    Doctor d = (Doctor) s;
                    if (d.isAvailable() && !d.hasReachedDailyCapacity()) {
                        result.add(d);
                    }
                }
            }
            return Collections.unmodifiableList(result);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public boolean hasSuperAdmin() {
        rwLock.readLock().lock();
        try {
            for (Admin admin : adminsByUsername.values()) {
                if (admin.getRole() == Admin.AdminRole.SUPER_ADMIN) {
                    return true;
                }
            }
            return false;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // =========================================================================
    // HOSPITAL KPI & FINANCIAL DASHBOARD
    // =========================================================================

    public BigDecimal calculateTotalHospitalRevenue() {
        rwLock.readLock().lock();
        try {
            BigDecimal total = BigDecimal.ZERO;
            for (Patient patient : patients.values()) {
                total = total.add(patient.getTotalPaidAmount());
            }
            return total;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public String getFormattedTotalHospitalRevenue() {
        return String.format("$%,.2f", calculateTotalHospitalRevenue());
    }

    public int getTotalRegisteredPatientsCount() {
        rwLock.readLock().lock();
        try {
            return patients.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public int getTotalStaffCount() {
        rwLock.readLock().lock();
        try {
            return staffMembers.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // =========================================================================
    // COLLISION-FREE SAFE IDENTITY MIGRATION
    // =========================================================================

    public void migratePersonId(String oldNationalId, String newNationalId, String operatorId) {
        Objects.requireNonNull(oldNationalId, "Current ID cannot be null.");
        Objects.requireNonNull(newNationalId, "New ID cannot be null.");

        String cleanOld = oldNationalId.trim();
        String cleanNew = newNationalId.trim();

        if (cleanOld.equalsIgnoreCase(cleanNew)) {
            throw new IllegalArgumentException("Old and new National IDs are identical.");
        }

        rwLock.writeLock().lock();
        try {
            // Anti-Collision Check: Ensure new ID does not exist anywhere in the hospital system
            if (patients.containsKey(cleanNew) || staffMembers.containsKey(cleanNew)) {
                throw new IllegalArgumentException("Target National ID [" + cleanNew + "] is already in use by another person.");
            }

            boolean migratedAny = false;

            if (patients.containsKey(cleanOld)) {
                Patient oldPatient = patients.remove(cleanOld);
                Patient migrated = (Patient) oldPatient.cloneWithNewId(cleanNew);
                patients.put(cleanNew, migrated);
                appendAuditLog(operatorId, "MIGRATE_PATIENT_ID", "Patient ID migrated from " + cleanOld + " to " + cleanNew, SystemClock.now());
                migratedAny = true;
            }

            if (staffMembers.containsKey(cleanOld)) {
                Staff oldStaff = staffMembers.remove(cleanOld);
                Staff migrated = (Staff) oldStaff.cloneWithNewId(cleanNew);
                staffMembers.put(cleanNew, migrated);

                if (migrated instanceof Admin) {
                    Admin admin = (Admin) migrated;
                    adminsByUsername.put(admin.getUsername().toLowerCase(), admin);
                }
                appendAuditLog(operatorId, "MIGRATE_STAFF_ID", "Staff ID migrated from " + cleanOld + " to " + cleanNew, SystemClock.now());
                migratedAny = true;
            }

            if (!migratedAny) {
                throw new NoSuchElementException("Entity with National ID [" + cleanOld + "] was not found.");
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // =========================================================================
    // DISASTER-PROOF ATOMIC PERSISTENCE (AES-256-GCM + ROBUST FSYNC)
    // =========================================================================

    private static SecretKey deriveKey(char[] masterPassword, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeySpec spec = new PBEKeySpec(masterPassword, salt, PBKDF2_ITERATIONS, AES_KEY_SIZE_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] secretBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(secretBytes, "AES");
        } finally {
            ((PBEKeySpec) spec).clearPassword(); // Wipe sensitive key material from RAM
        }
    }

    /**
     * Serializes, encrypts, and atomically flushes the repository to disk.
     * Prevents stream premature closure and Windows file-locking failures.
     */
    public void saveToEncryptedFile(File targetFile, char[] masterPassword) throws Exception {
        Objects.requireNonNull(targetFile, "Target file cannot be null.");
        Objects.requireNonNull(masterPassword, "Master password cannot be null.");

        rwLock.writeLock().lock();
        try {
            File parentDir = targetFile.getAbsoluteFile().getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                throw new IOException("Failed to create parent directories for: " + targetFile.getAbsolutePath());
            }

            File tempFile = new File(targetFile.getAbsolutePath() + ".tmp");
            File backupFile = new File(targetFile.getAbsolutePath() + ".bak");

            // 1. Serialize memory graph into byte buffer in-memory
            byte[] serializedPayload;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(this);
                oos.flush();
                serializedPayload = baos.toByteArray();
            }

            // 2. Encrypt byte buffer with fresh Salt & IV
            byte[] salt = new byte[SALT_LENGTH_BYTES];
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SecureRandom random = new SecureRandom();
            random.nextBytes(salt);
            random.nextBytes(iv);

            SecretKey secretKey = deriveKey(masterPassword, salt);
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGO);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
            byte[] ciphertextWithTag = cipher.doFinal(serializedPayload);

            // 3. Write directly to temporary file with clean fsync
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(salt);
                fos.write(iv);
                fos.write(ciphertextWithTag);
                fos.flush();
                try {
                    fos.getFD().sync(); // Safe fsync because fos is completely open and independent
                } catch (SyncFailedException ignored) {
                    // Fallback for RAM drives or restricted file-systems
                }
            }

            // 4. Safe atomic replacement with Windows file-lock fallback
            if (targetFile.exists()) {
                try {
                    Files.copy(targetFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {}
            }

            try {
                Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                // Windows non-atomic fallback
                Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public static ClinicMemory loadFromEncryptedFile(File sourceFile, char[] masterPassword) throws Exception {
        Objects.requireNonNull(sourceFile, "Source file cannot be null.");
        Objects.requireNonNull(masterPassword, "Master password cannot be null.");

        File backupFile = new File(sourceFile.getAbsolutePath() + ".bak");

        try {
            return readEncryptedFilePayload(sourceFile, masterPassword);
        } catch (Exception primaryError) {
            if (backupFile.exists() && backupFile.length() > 0) {
                try {
                    return readEncryptedFilePayload(backupFile, masterPassword);
                } catch (Exception backupError) {
                    throw new IOException("Critical Failure: Both primary database and recovery backup are corrupted.", primaryError);
                }
            }
            throw primaryError;
        }
    }

    private static ClinicMemory readEncryptedFilePayload(File file, char[] masterPassword) throws Exception {
        if (!file.exists() || file.length() < (SALT_LENGTH_BYTES + GCM_IV_LENGTH_BYTES + 16)) {
            throw new IOException("Corrupted or incomplete database file: " + file.getAbsolutePath());
        }

        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {

            byte[] salt = bis.readNBytes(SALT_LENGTH_BYTES);
            if (salt.length != SALT_LENGTH_BYTES) {
                throw new IOException("Corrupted header: Incomplete salt.");
            }

            byte[] iv = bis.readNBytes(GCM_IV_LENGTH_BYTES);
            if (iv.length != GCM_IV_LENGTH_BYTES) {
                throw new IOException("Corrupted header: Incomplete initialization vector (IV).");
            }

            byte[] ciphertextWithTag = bis.readAllBytes();
            if (ciphertextWithTag.length == 0) {
                throw new IOException("Corrupted payload: Missing encrypted body.");
            }

            SecretKey secretKey = deriveKey(masterPassword, salt);
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGO);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            // Fast, authoritative AEAD tag verification (fails instantly if password is wrong)
            byte[] decryptedPayload = cipher.doFinal(ciphertextWithTag);

            try (ByteArrayInputStream bais = new ByteArrayInputStream(decryptedPayload);
                 ObjectInputStream ois = new ObjectInputStream(bais)) {
                return (ClinicMemory) ois.readObject();
            }
        }
    }

    public File createSnapshotBackup(File backupDirectory, char[] masterPassword) throws Exception {
        Objects.requireNonNull(backupDirectory, "Backup directory cannot be null.");
        Objects.requireNonNull(masterPassword, "Master password cannot be null.");

        if (!backupDirectory.exists() && !backupDirectory.mkdirs()) {
            throw new IOException("Failed to create backup directory: " + backupDirectory.getAbsolutePath());
        }

        SystemClock now = SystemClock.now();
        String filename = String.format("hospital_snapshot_%04d%02d%02d_%02d%02d%02d_%03d.enc",
                now.getJalaliYear(), now.getJalaliMonth(), now.getJalaliDay(),
                now.getHour(), now.getMinute(), now.getSecond(), now.getMillisecond());

        File snapshotFile = new File(backupDirectory, filename);
        this.saveToEncryptedFile(snapshotFile, masterPassword);
        return snapshotFile;
    }

    public static boolean verifyBackupIntegrity(File backupFile, char[] masterPassword) {
        if (backupFile == null || !backupFile.exists() || masterPassword == null) {
            return false;
        }
        try {
            ClinicMemory testMemory = readEncryptedFilePayload(backupFile, masterPassword);
            return testMemory != null && testMemory.verifyAuditChainIntegrity();
        } catch (Exception e) {
            return false;
        }
    }
}