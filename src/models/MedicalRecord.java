package models;

import system.SystemClock;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

/**
 * Enterprise HIPAA-compliant Medical Record entity.
 * Immutable, cryptographically sealed with SHA-256 integrity verification,
 * supports historical amendment chaining, and provides data-binding for GUI applications.
 */
public final class MedicalRecord implements Serializable, Comparable<MedicalRecord> {
    private static final long serialVersionUID = 30L;

    public enum RecordType {
        GENERAL_CHECKUP(101, "General Checkup / Triage"),
        CONSULTATION(102, "Clinical Consultation"),
        EMERGENCY(103, "Emergency Intervention"),
        LAB_RESULT(104, "Laboratory Test Result"),
        PRESCRIPTION(105, "Prescription & Medication"),
        SURGERY(106, "Surgical Procedure"),
        DISCHARGE_SUMMARY(107, "Discharge Summary"),
        OTHER(199, "Other Medical Event");

        private final int code;
        private final String displayTitle;

        RecordType(int code, String displayTitle) {
            this.code = code;
            this.displayTitle = displayTitle;
        }

        public int getCode() {
            return code;
        }

        public String getDisplayTitle() {
            return displayTitle;
        }

        public static RecordType fromString(String input) {
            if (input == null || input.trim().isEmpty()) {
                throw new IllegalArgumentException("Record type cannot be null or empty.");
            }
            String normalized = input.trim().toUpperCase();
            for (RecordType type : RecordType.values()) {
                if (type.name().equals(normalized) || type.displayTitle.equalsIgnoreCase(input.trim())) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown medical record type: " + input);
        }

        @Override
        public String toString() {
            return displayTitle;
        }
    }

    public enum RecordStatus {
        PRELIMINARY(201, "Preliminary / Draft"),
        FINAL(202, "Finalized & Signed"),
        AMENDED(203, "Amended"),
        CANCELLED(204, "Cancelled / Revoked");

        private final int code;
        private final String displayTitle;

        RecordStatus(int code, String displayTitle) {
            this.code = code;
            this.displayTitle = displayTitle;
        }

        public int getCode() {
            return code;
        }

        public String getDisplayTitle() {
            return displayTitle;
        }

        public static RecordStatus fromString(String input) {
            if (input == null || input.trim().isEmpty()) {
                throw new IllegalArgumentException("Record status cannot be null or empty.");
            }
            String normalized = input.trim().toUpperCase();
            for (RecordStatus status : RecordStatus.values()) {
                if (status.name().equals(normalized) || status.displayTitle.equalsIgnoreCase(input.trim())) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown medical record status: " + input);
        }

        @Override
        public String toString() {
            return displayTitle;
        }
    }

    public enum ConfidentialityLevel {
        NORMAL(301, "Normal Medical Care"),
        RESTRICTED(302, "Restricted Departmental"),
        HIGHLY_CONFIDENTIAL(303, "Highly Confidential / Sealed");

        private final int code;
        private final String displayTitle;

        ConfidentialityLevel(int code, String displayTitle) {
            this.code = code;
            this.displayTitle = displayTitle;
        }

        public int getCode() {
            return code;
        }

        public String getDisplayTitle() {
            return displayTitle;
        }

        public static ConfidentialityLevel fromString(String input) {
            if (input == null || input.trim().isEmpty()) {
                throw new IllegalArgumentException("Confidentiality level cannot be null or empty.");
            }
            String normalized = input.trim().toUpperCase();
            for (ConfidentialityLevel level : ConfidentialityLevel.values()) {
                if (level.name().equals(normalized) || level.displayTitle.equalsIgnoreCase(input.trim())) {
                    return level;
                }
            }
            throw new IllegalArgumentException("Unknown confidentiality level: " + input);
        }

        @Override
        public String toString() {
            return displayTitle;
        }
    }

    private final String recordId;
    private final String previousRecordId;
    private final String patientNationalId;
    private final String doctorNationalId;
    private final SystemClock timestamp;
    private final RecordType recordType;
    private final RecordStatus recordStatus;
    private final ConfidentialityLevel confidentialityLevel;
    private final String diagnosis;
    private final String prescription;
    private final String notes;
    private final String integrityHash;

    /**
     * Primary constructor creating an authoritative and cryptographically sealed medical record.
     */
    public MedicalRecord(String previousRecordId, String patientNationalId, String doctorNationalId,
                         SystemClock timestamp, RecordType recordType, RecordStatus recordStatus,
                         ConfidentialityLevel confidentialityLevel, String diagnosis,
                         String prescription, String notes) {

        this.recordId = "MED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.previousRecordId = (previousRecordId == null || previousRecordId.trim().isEmpty()) ? "NONE" : previousRecordId.trim();
        this.patientNationalId = Objects.requireNonNull(patientNationalId, "Patient ID cannot be null.").trim();
        this.doctorNationalId = Objects.requireNonNull(doctorNationalId, "Doctor ID cannot be null.").trim();
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp cannot be null.");
        this.recordType = Objects.requireNonNull(recordType, "Record type cannot be null.");
        this.recordStatus = Objects.requireNonNull(recordStatus, "Record status cannot be null.");
        this.confidentialityLevel = Objects.requireNonNull(confidentialityLevel, "Confidentiality level cannot be null.");
        this.diagnosis = (diagnosis == null || diagnosis.trim().isEmpty()) ? "No specific diagnosis recorded." : diagnosis.trim();
        this.prescription = (prescription == null || prescription.trim().isEmpty()) ? "NONE" : prescription.trim();
        this.notes = (notes == null || notes.trim().isEmpty()) ? "Routine clinical entry." : notes.trim();

        this.integrityHash = calculatePayloadHash();
    }

    private String calculatePayloadHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = recordId + previousRecordId + patientNationalId + doctorNationalId +
                    timestamp.toEpochMilli() + recordType.name() + recordStatus.name() +
                    confidentialityLevel.name() + diagnosis + prescription + notes;
            byte[] encoded = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(2 * encoded.length);
            for (byte b : encoded) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Security error: SHA-256 provider unavailable.", e);
        }
    }

    /**
     * Verifies that the record content has not been tampered with or corrupted in memory.
     */
    public boolean verifyIntegrity() {
        return this.integrityHash.equals(calculatePayloadHash());
    }

    /**
     * Legal clinical amendment factory: Spawns an amended successor record linked to this document.
     */
    public MedicalRecord createAmendment(String attendingDoctorId, String updatedDiagnosis,
                                         String updatedPrescription, String amendmentReason,
                                         SystemClock amendmentTimestamp) {

        Objects.requireNonNull(attendingDoctorId, "Attending doctor ID cannot be null.");
        Objects.requireNonNull(amendmentTimestamp, "Amendment timestamp cannot be null.");

        String compositeNotes = String.format("AMENDMENT to [%s] by Doctor [%s]: %s | Prior notes: %s",
                this.recordId, attendingDoctorId.trim(),
                (amendmentReason == null ? "Clinical Update" : amendmentReason.trim()),
                this.notes);

        return new MedicalRecord(
                this.recordId,
                this.patientNationalId,
                attendingDoctorId.trim(),
                amendmentTimestamp,
                this.recordType,
                RecordStatus.AMENDED,
                this.confidentialityLevel,
                (updatedDiagnosis == null ? this.diagnosis : updatedDiagnosis),
                (updatedPrescription == null ? this.prescription : updatedPrescription),
                compositeNotes
        );
    }

    public String getRecordId() { return recordId; }
    public String getPreviousRecordId() { return previousRecordId; }
    public String getPatientNationalId() { return patientNationalId; }
    public String getDoctorNationalId() { return doctorNationalId; }
    public SystemClock getTimestamp() { return timestamp; }
    public RecordType getRecordType() { return recordType; }
    public RecordStatus getRecordStatus() { return recordStatus; }
    public ConfidentialityLevel getConfidentialityLevel() { return confidentialityLevel; }
    public String getDiagnosis() { return diagnosis; }
    public String getPrescription() { return prescription; }
    public String getNotes() { return notes; }
    public String getIntegrityHash() { return integrityHash; }

    public boolean isAmended() {
        return this.recordStatus == RecordStatus.AMENDED;
    }

    public boolean isCancelled() {
        return this.recordStatus == RecordStatus.CANCELLED;
    }

    public String getFormattedClinicalReport() {
        return String.format(
                "======================================================================%n" +
                        "                       HOSPITAL CLINICAL RECORD                       %n" +
                        "======================================================================%n" +
                        " Record ID       : %s%n" +
                        " Parent Record   : %s%n" +
                        " Patient ID      : %s%n" +
                        " Physician ID    : %s%n" +
                        " Timestamp       : %s%n" +
                        " Type / Status   : %s / %s%n" +
                        " Confidentiality : %s%n" +
                        " Integrity Seal  : %s%n" +
                        "----------------------------------------------------------------------%n" +
                        " DIAGNOSIS:%n   %s%n" +
                        " PRESCRIPTION:%n   %s%n" +
                        " CLINICAL NOTES:%n   %s%n" +
                        "======================================================================",
                recordId, previousRecordId, patientNationalId, doctorNationalId,
                timestamp.toFullDisplayString(), recordType.getDisplayTitle(), recordStatus.getDisplayTitle(),
                confidentialityLevel.getDisplayTitle(), integrityHash.substring(0, 16) + "...",
                diagnosis, prescription, notes
        );
    }

    @Override
    public int compareTo(MedicalRecord other) {
        Objects.requireNonNull(other, "Cannot compare to null MedicalRecord.");
        return this.timestamp.compareTo(other.timestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MedicalRecord that = (MedicalRecord) o;
        return recordId.equals(that.recordId) && integrityHash.equals(that.integrityHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordId, integrityHash);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s | Patient: %s | Doctor: %s | Status: %s",
                recordId, recordType.getDisplayTitle(), patientNationalId, doctorNationalId, recordStatus.getDisplayTitle());
    }
}