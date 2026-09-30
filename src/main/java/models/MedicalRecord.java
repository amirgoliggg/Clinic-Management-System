package models;

import system.SystemClock;
import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "medical_records", indexes = {
        @Index(name = "idx_mr_patient", columnList = "patient_national_id"),
        @Index(name = "idx_mr_doctor", columnList = "doctor_national_id")
})
public final class MedicalRecord implements Serializable, Comparable<MedicalRecord> {

    @Serial
    private static final long serialVersionUID = 30L;

    public enum RecordType { GENERAL_CHECKUP, CONSULTATION, EMERGENCY, LAB_RESULT, PRESCRIPTION, SURGERY, DISCHARGE_SUMMARY, OTHER }
    public enum RecordStatus { PRELIMINARY, FINAL, AMENDED, CANCELLED }
    public enum ConfidentialityLevel { NORMAL, RESTRICTED, HIGHLY_CONFIDENTIAL }

    @Id
    @Column(name = "record_id", length = 36, updatable = false, nullable = false)
    private String recordId;

    @Column(name = "previous_record_id", length = 36)
    private String previousRecordId;

    @Column(name = "patient_national_id", nullable = false)
    private String patientNationalId;

    @Column(name = "doctor_national_id", nullable = false)
    private String doctorNationalId;

    @Column(name = "timestamp_milli", nullable = false, updatable = false)
    private SystemClock timestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false)
    private RecordType recordType;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_status", nullable = false)
    private RecordStatus recordStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidentiality_level", nullable = false)
    private ConfidentialityLevel confidentialityLevel;

    @Column(name = "diagnosis", length = 2000)
    private String diagnosis;

    @Column(name = "prescription", length = 2000)
    private String prescription;

    @Column(name = "notes", length = 4000)
    private String notes;

    @Column(name = "integrity_hash", updatable = false, nullable = false)
    private String integrityHash;

    protected MedicalRecord() {}

    public MedicalRecord(String recordId, String previousRecordId, String patientNationalId, String doctorNationalId,
                         SystemClock timestamp, RecordType recordType, RecordStatus recordStatus,
                         ConfidentialityLevel confidentialityLevel, String diagnosis,
                         String prescription, String notes) {
        this.recordId = Objects.requireNonNull(recordId).trim();
        this.previousRecordId = (previousRecordId == null || previousRecordId.isBlank()) ? "NONE" : previousRecordId.trim();
        this.patientNationalId = Objects.requireNonNull(patientNationalId).trim();
        this.doctorNationalId = Objects.requireNonNull(doctorNationalId).trim();
        this.timestamp = Objects.requireNonNull(timestamp);
        this.recordType = Objects.requireNonNull(recordType);
        this.recordStatus = Objects.requireNonNull(recordStatus);
        this.confidentialityLevel = Objects.requireNonNull(confidentialityLevel);
        this.diagnosis = (diagnosis == null || diagnosis.isBlank()) ? "No diagnosis" : diagnosis.trim();
        this.prescription = (prescription == null || prescription.isBlank()) ? "NONE" : prescription.trim();
        this.notes = (notes == null || notes.isBlank()) ? "Routine entry" : notes.trim();
        this.integrityHash = calculatePayloadHash();
    }

    public static MedicalRecord create(String recordId, String patientNationalId, String doctorNationalId,
                                       String diagnosis, String prescription, String notes) {
        return new MedicalRecord(recordId, "NONE", patientNationalId, doctorNationalId, SystemClock.now(),
                RecordType.CONSULTATION, RecordStatus.FINAL, ConfidentialityLevel.NORMAL, diagnosis, prescription, notes);
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
            throw new IllegalStateException("SHA-256 provider unavailable.", e);
        }
    }

    public boolean verifyIntegrity() { return this.integrityHash.equals(calculatePayloadHash()); }

    public MedicalRecord createAmendment(String attendingDoctorId, String updatedDiagnosis,
                                         String updatedPrescription, String amendmentReason, SystemClock amendmentTimestamp) {
        String compositeNotes = String.format("AMENDMENT to [%s]: %s | Prior: %s", this.recordId, amendmentReason, this.notes);
        return new MedicalRecord(java.util.UUID.randomUUID().toString(), this.recordId, this.patientNationalId, attendingDoctorId, amendmentTimestamp,
                this.recordType, RecordStatus.AMENDED, this.confidentialityLevel, updatedDiagnosis, updatedPrescription, compositeNotes);
    }

    public String getRecordId() { return recordId; }
    public String getPatientNationalId() { return patientNationalId; }
    public String getDoctorNationalId() { return doctorNationalId; }
    public SystemClock getTimestamp() { return timestamp; }
    public RecordStatus getRecordStatus() { return recordStatus; }
    public String getDiagnosis() { return diagnosis; }

    @Override
    public int compareTo(MedicalRecord other) { return this.timestamp.compareTo(other.timestamp); }
}