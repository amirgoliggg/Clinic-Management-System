package models;

import enums.PatientStatus;
import system.SystemClock;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Enterprise Doctor entity representing licensed medical practitioners.
 * Enforces clinical governance, daily workload limits, automated medical record
 * attachment, and patient lifecycle transitions along the hospital pipeline.
 */
public final class Doctor extends Staff {
    private static final long serialVersionUID = 25L;

    private static final Pattern LICENSE_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]{4,32}$");

    public enum Specialization {
        GENERAL_PRACTICE(201, "General Practice"),
        CARDIOLOGY(202, "Cardiology"),
        DERMATOLOGY(203, "Dermatology"),
        PEDIATRICS(204, "Pediatrics"),
        NEUROLOGY(205, "Neurology"),
        ORTHOPEDICS(206, "Orthopedics"),
        PSYCHIATRY(207, "Psychiatry"),
        INTERNAL_MEDICINE(208, "Internal Medicine"),
        OPHTHALMOLOGY(209, "Ophthalmology"),
        EMERGENCY_MEDICINE(210, "Emergency Medicine"),
        OTHER(299, "Other / General Specialty");

        private final int departmentCode;
        private final String displayTitle;

        Specialization(int departmentCode, String displayTitle) {
            this.departmentCode = departmentCode;
            this.displayTitle = displayTitle;
        }

        public int getDepartmentCode() {
            return departmentCode;
        }

        public String getDisplayTitle() {
            return displayTitle;
        }

        public static Specialization fromString(String input) {
            if (input == null || input.trim().isEmpty()) {
                throw new IllegalArgumentException("Specialization input cannot be empty.");
            }
            String normalized = input.trim().toUpperCase();
            for (Specialization spec : Specialization.values()) {
                if (spec.name().equals(normalized) || spec.displayTitle.equalsIgnoreCase(input.trim())) {
                    return spec;
                }
            }
            throw new IllegalArgumentException("Unknown medical specialization: " + input);
        }

        @Override
        public String toString() {
            return displayTitle;
        }
    }

    private final String medicalLicenseNumber;
    private Specialization specialization;
    private BigDecimal consultationFee;
    private int maxDailyPatients;
    private int dailyPatientCount;
    private volatile boolean available;

    /**
     * Primary Constructor for registering a licensed physician.
     */
    public Doctor(String nationalId, String fullName, Integer age, String contactNumber,
                  BigDecimal baseSalary, String medicalLicenseNumber, Specialization specialization,
                  BigDecimal consultationFee, int maxDailyPatients) {

        super(nationalId, fullName, age, contactNumber, baseSalary);

        this.medicalLicenseNumber = validateLicenseNumber(medicalLicenseNumber);
        this.setSpecialization(specialization);
        this.setConsultationFee(consultationFee);
        this.setMaxDailyPatients(maxDailyPatients);
        this.dailyPatientCount = 0;
        this.available = true;
    }

    /**
     * Protected Copy Constructor for Identity Migration (ID change).
     */
    private Doctor(String newNationalId, Doctor source) {
        super(newNationalId, source);
        this.medicalLicenseNumber = source.medicalLicenseNumber;
        this.specialization = source.specialization;
        this.consultationFee = source.consultationFee;
        this.maxDailyPatients = source.maxDailyPatients;
        this.dailyPatientCount = source.dailyPatientCount;
        this.available = source.available;
    }

    @Override
    public Person cloneWithNewId(String newNationalId) {
        return new Doctor(newNationalId, this);
    }

    private String validateLicenseNumber(String license) {
        if (license == null || license.trim().isEmpty()) {
            throw new IllegalArgumentException("Medical license number cannot be null or empty.");
        }
        String trimmed = license.trim();
        if (!LICENSE_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid medical license format. Must be 4-32 alphanumeric characters.");
        }
        return trimmed;
    }

    /**
     * Convenience consultation method optimized for GUI forms (auto-populates defaults and real-time clock).
     */
    public MedicalRecord conductConsultation(Patient patient, String diagnosis, String prescription, String notes) {
        return conductConsultation(
                patient,
                MedicalRecord.RecordType.CONSULTATION,
                MedicalRecord.RecordStatus.FINAL,
                MedicalRecord.ConfidentialityLevel.NORMAL,
                diagnosis,
                prescription,
                notes,
                null,
                SystemClock.now()
        );
    }

    /**
     * Authoritative Clinical Consultation:
     * Enforces pipeline status, verifies workload ceiling, attaches signed record,
     * and transitions patient to payment queue.
     */
    public MedicalRecord conductConsultation(Patient patient,
                                             MedicalRecord.RecordType recordType,
                                             MedicalRecord.RecordStatus recordStatus,
                                             MedicalRecord.ConfidentialityLevel confidentialityLevel,
                                             String diagnosis, String prescription,
                                             String notes, String previousRecordId,
                                             SystemClock timestamp) {

        Objects.requireNonNull(patient, "Target patient cannot be null.");
        Objects.requireNonNull(timestamp, "Consultation timestamp cannot be null.");

        if (!this.available) {
            throw new IllegalStateException("Doctor " + getFullName() + " is currently unavailable or off-duty.");
        }

        if (this.dailyPatientCount >= this.maxDailyPatients) {
            throw new IllegalStateException("Physician capacity reached: Doctor " + getFullName() +
                    " has already seen the maximum limit of " + maxDailyPatients + " patients today.");
        }

        if (patient.getStatus() != PatientStatus.WAITING_FOR_DOCTOR) {
            throw new IllegalStateException("Invalid clinical sequence: Patient " + patient.getNationalId() +
                    " must be in WAITING_FOR_DOCTOR status to begin examination. Current status: " +
                    patient.getStatus().getLabel());
        }

        String validDiagnosis = (diagnosis == null || diagnosis.trim().isEmpty())
                ? "Diagnosis Pending"
                : diagnosis.trim();
        String validPrescription = (prescription == null || prescription.trim().isEmpty())
                ? "NONE"
                : prescription.trim();
        String validNotes = (notes == null || notes.trim().isEmpty())
                ? "Routine clinical evaluation conducted."
                : notes.trim();

        MedicalRecord record = new MedicalRecord(
                previousRecordId,
                patient.getNationalId(),
                this.getNationalId(),
                timestamp,
                recordType,
                recordStatus,
                confidentialityLevel,
                validDiagnosis,
                validPrescription,
                validNotes
        );

        // Attach record to patient medical history
        patient.addMedicalRecord(record);

        // Advance patient status along hospital pipeline
        patient.setStatus(PatientStatus.WAITING_FOR_PAYMENT);

        // Increment daily patient telemetry
        this.dailyPatientCount++;

        return record;
    }

    /**
     * Resets the daily patient counter during shift changes or daily handovers.
     */
    public void resetDailyPatientCount() {
        this.dailyPatientCount = 0;
    }

    public boolean hasReachedDailyCapacity() {
        return this.dailyPatientCount >= this.maxDailyPatients;
    }

    public String getWorkloadDisplay() {
        return dailyPatientCount + " / " + maxDailyPatients + " Patients";
    }

    public String getFormattedConsultationFee() {
        return String.format("$%,.2f", consultationFee);
    }

    public String getMedicalLicenseNumber() {
        return medicalLicenseNumber;
    }

    public Specialization getSpecialization() {
        return specialization;
    }

    public void setSpecialization(Specialization specialization) {
        this.specialization = Objects.requireNonNull(specialization, "Specialization cannot be null.");
    }

    public BigDecimal getConsultationFee() {
        return consultationFee;
    }

    public void setConsultationFee(BigDecimal consultationFee) {
        if (consultationFee == null || consultationFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Consultation fee cannot be null or negative.");
        }
        this.consultationFee = consultationFee;
    }

    public int getMaxDailyPatients() {
        return maxDailyPatients;
    }

    public void setMaxDailyPatients(int maxDailyPatients) {
        if (maxDailyPatients <= 0 || maxDailyPatients > 200) {
            throw new IllegalArgumentException("Max daily patients must be between 1 and 200.");
        }
        this.maxDailyPatients = maxDailyPatients;
    }

    public int getDailyPatientCount() {
        return dailyPatientCount;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public String toString() {
        return super.toString() + String.format(
                " | License: %s | Spec: %s | Fee: %s | Workload: %s | Available: %s",
                medicalLicenseNumber, specialization.getDisplayTitle(),
                getFormattedConsultationFee(), getWorkloadDisplay(), available ? "YES" : "NO"
        );
    }
}