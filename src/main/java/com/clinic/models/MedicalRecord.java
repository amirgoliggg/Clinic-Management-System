package com.clinic.models;

import com.clinic.system.SystemClock;
import jakarta.persistence.*;
import com.clinic.security.HmacKeyProvider;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "medical_records",
        indexes = {
                @Index(name = "idx_mr_patient", columnList = "patient_national_id"),
                @Index(name = "idx_mr_doctor", columnList = "doctor_national_id")
        }
)
public class MedicalRecord implements Serializable, Comparable<MedicalRecord> {

    @Serial
    private static final long serialVersionUID = 30L;

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public enum RecordType {
        GENERAL_CHECKUP,
        CONSULTATION,
        EMERGENCY,
        LAB_RESULT,
        PRESCRIPTION,
        SURGERY,
        DISCHARGE_SUMMARY,
        OTHER
    }

    public enum RecordStatus {
        PRELIMINARY,
        FINAL,
        AMENDED,
        CANCELLED
    }

    public enum ConfidentialityLevel {
        NORMAL,
        RESTRICTED,
        HIGHLY_CONFIDENTIAL
    }

    @Id
    @Column(name = "record_id", length = 36, updatable = false, nullable = false)
    private String recordId;

    @Column(name = "previous_record_id", length = 36, updatable = false)
    private String previousRecordId;

    @Column(name = "patient_national_id", nullable = false, updatable = false)
    private String patientNationalId;

    @Column(name = "doctor_national_id", nullable = false, updatable = false)
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

    @Convert(converter = com.clinic.security.SensitiveDataCipherConverter.class)
    @Column(name = "diagnosis", length = 4000)
    private String diagnosis;

    @Convert(converter = com.clinic.security.SensitiveDataCipherConverter.class)
    @Column(name = "prescription", length = 4000)
    private String prescription;

    @Convert(converter = com.clinic.security.SensitiveDataCipherConverter.class)
    @Column(name = "notes", length = 8000)
    private String notes;

    @Column(name = "integrity_hash", updatable = false, nullable = false, length = 128)
    private String integrityHash;

    // ==================== Constructors & Factory Methods ====================

    protected MedicalRecord() {
        // Required by JPA
    }

    public MedicalRecord(String recordId,
                         String previousRecordId,
                         String patientNationalId,
                         String doctorNationalId,
                         SystemClock timestamp,
                         RecordType recordType,
                         RecordStatus recordStatus,
                         ConfidentialityLevel confidentialityLevel,
                         String diagnosis,
                         String prescription,
                         String notes) {
        this.recordId = Objects.requireNonNull(recordId, "Record ID cannot be null.").trim();
        this.previousRecordId = (previousRecordId == null || previousRecordId.isBlank()) ? "NONE" : previousRecordId.trim();
        this.patientNationalId = Objects.requireNonNull(patientNationalId, "Patient ID cannot be null.").trim();
        this.doctorNationalId = Objects.requireNonNull(doctorNationalId, "Doctor/Clinician ID cannot be null.").trim();
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp cannot be null.");
        this.recordType = Objects.requireNonNull(recordType, "Record type cannot be null.");
        this.recordStatus = Objects.requireNonNull(recordStatus, "Record status cannot be null.");
        this.confidentialityLevel = Objects.requireNonNull(confidentialityLevel, "Confidentiality level cannot be null.");
        this.diagnosis = (diagnosis == null || diagnosis.isBlank()) ? "No diagnosis" : diagnosis.trim();
        this.prescription = (prescription == null || prescription.isBlank()) ? "NONE" : prescription.trim();
        this.notes = (notes == null || notes.isBlank()) ? "Routine entry" : notes.trim();
        this.integrityHash = calculatePayloadHash();
    }

    public static MedicalRecord create(String recordId,
                                       String patientNationalId,
                                       String doctorNationalId,
                                       String diagnosis,
                                       String prescription,
                                       String notes) {
        return new MedicalRecord(
                recordId,
                "NONE",
                patientNationalId,
                doctorNationalId,
                SystemClock.now(),
                RecordType.CONSULTATION,
                RecordStatus.FINAL,
                ConfidentialityLevel.NORMAL,
                diagnosis,
                prescription,
                notes
        );
    }

    // ==================== Cryptographic Integrity & Amendments ====================

    private String calculatePayloadHash() {
        String payload = String.join(":",
                recordId,
                previousRecordId,
                patientNationalId,
                doctorNationalId,
                String.valueOf(timestamp.toEpochMilli()),
                recordType.name(),
                recordStatus.name(),
                confidentialityLevel.name(),
                diagnosis,
                prescription,
                notes
        );

        try {
            byte[] keyBytes = com.clinic.security.HmacKeyProvider.getMedicalRecordKey();
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signatureBytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 integrity hash for medical record.", e);
        }
    }

    public boolean verifyIntegrity() {
        if (this.integrityHash == null || this.integrityHash.isBlank()) {
            return false;
        }
        String expected = calculatePayloadHash();
        return MessageDigest.isEqual(
                this.integrityHash.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    public MedicalRecord createAmendment(String attendingDoctorId,
                                         String updatedDiagnosis,
                                         String updatedPrescription,
                                         String amendmentReason,
                                         SystemClock amendmentTimestamp) {
        String safeReason = (amendmentReason != null && !amendmentReason.isBlank()) ? amendmentReason.trim() : "اصلاح پزشکی";
        String compositeNotes = String.format("AMENDMENT to [%s]: %s | Prior: %s", this.recordId, safeReason, this.notes);
        return new MedicalRecord(
                UUID.randomUUID().toString(),
                this.recordId,
                this.patientNationalId,
                attendingDoctorId,
                amendmentTimestamp != null ? amendmentTimestamp : SystemClock.now(),
                this.recordType,
                RecordStatus.AMENDED,
                this.confidentialityLevel,
                updatedDiagnosis,
                updatedPrescription,
                compositeNotes
        );
    }

    // ==================== Getters ====================

    public String getRecordId() {
        return recordId;
    }

    public String getPreviousRecordId() {
        return previousRecordId;
    }

    public String getPatientNationalId() {
        return patientNationalId;
    }

    public String getDoctorNationalId() {
        return doctorNationalId;
    }

    public SystemClock getTimestamp() {
        return timestamp;
    }

    public RecordType getRecordType() {
        return recordType;
    }

    public RecordStatus getRecordStatus() {
        return recordStatus;
    }

    public ConfidentialityLevel getConfidentialityLevel() {
        return confidentialityLevel;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public String getPrescription() {
        return prescription;
    }

    public String getNotes() {
        return notes;
    }

    public String getIntegrityHash() {
        return integrityHash;
    }

    @Override
    public int compareTo(MedicalRecord other) {
        return this.timestamp.compareTo(other.timestamp);
    }
}