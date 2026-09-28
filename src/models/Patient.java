package models;

import enums.PatientStatus;

import java.math.BigDecimal;
import java.util.*;

/**
 * Enterprise Patient entity managing clinical metadata, medical history,
 * immutable billing records, and automated lifecycle state progression.
 */
public final class Patient extends Person {
    private static final long serialVersionUID = 12L;

    public enum BloodType {
        A_POSITIVE("A+"),
        A_NEGATIVE("A-"),
        B_POSITIVE("B+"),
        B_NEGATIVE("B-"),
        AB_POSITIVE("AB+"),
        AB_NEGATIVE("AB-"),
        O_POSITIVE("O+"),
        O_NEGATIVE("O-"),
        UNKNOWN("Unknown / Pending Lab");

        private final String displayLabel;

        BloodType(String displayLabel) {
            this.displayLabel = displayLabel;
        }

        public String getDisplayLabel() {
            return displayLabel;
        }

        public static BloodType fromString(String input) {
            if (input == null || input.trim().isEmpty()) {
                return UNKNOWN;
            }
            String normalized = input.trim().toUpperCase();
            for (BloodType bt : BloodType.values()) {
                if (bt.name().equals(normalized) || bt.displayLabel.equalsIgnoreCase(input.trim())) {
                    return bt;
                }
            }
            return UNKNOWN;
        }

        @Override
        public String toString() {
            return displayLabel;
        }
    }

    private BloodType bloodType;
    private String knownAllergies;
    private String emergencyContactNumber;
    private PatientStatus status;
    private final List<MedicalRecord> medicalHistory;
    private final List<Receipt> billingHistory;

    /**
     * Primary constructor for clinical patient registration.
     */
    public Patient(String nationalId, String fullName, Integer age, String contactNumber,
                   BloodType bloodType, String knownAllergies, String emergencyContactNumber) {

        super(nationalId, fullName, age, contactNumber);

        this.bloodType = (bloodType == null) ? BloodType.UNKNOWN : bloodType;
        this.knownAllergies = (knownAllergies == null || knownAllergies.trim().isEmpty()) ? "None Known" : knownAllergies.trim();
        this.emergencyContactNumber = (emergencyContactNumber == null) ? "N/A" : emergencyContactNumber.trim();
        this.status = PatientStatus.REGISTERED;
        this.medicalHistory = new ArrayList<>();
        this.billingHistory = new ArrayList<>();
    }

    /**
     * Protected Copy Constructor for Identity Migration (ID change).
     */
    private Patient(String newNationalId, Patient source) {
        super(newNationalId, source);
        this.bloodType = source.bloodType;
        this.knownAllergies = source.knownAllergies;
        this.emergencyContactNumber = source.emergencyContactNumber;
        this.status = source.status;
        this.medicalHistory = new ArrayList<>(source.medicalHistory);
        this.billingHistory = new ArrayList<>(source.billingHistory);
    }

    @Override
    public Person cloneWithNewId(String newNationalId) {
        return new Patient(newNationalId, this);
    }

    // =========================================================================
    // CLINICAL HISTORY OPERATIONS
    // =========================================================================

    public void addMedicalRecord(MedicalRecord record) {
        Objects.requireNonNull(record, "Medical record cannot be null.");
        if (!record.getPatientNationalId().equalsIgnoreCase(this.getNationalId())) {
            throw new IllegalArgumentException("Cross-validation Error: Record patient ID (" +
                    record.getPatientNationalId() + ") does not match patient ID (" + this.getNationalId() + ").");
        }
        this.medicalHistory.add(record);
    }

    public List<MedicalRecord> getMedicalHistory() {
        return Collections.unmodifiableList(new ArrayList<>(medicalHistory));
    }

    public Optional<MedicalRecord> getLatestMedicalRecord() {
        if (medicalHistory.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(medicalHistory.get(medicalHistory.size() - 1));
    }

    // =========================================================================
    // FINANCIAL BILLING HISTORY OPERATIONS
    // =========================================================================

    public void addReceipt(Receipt receipt) {
        Objects.requireNonNull(receipt, "Receipt cannot be null.");
        if (!receipt.getPatientNationalId().equalsIgnoreCase(this.getNationalId())) {
            throw new IllegalArgumentException("Cross-validation Error: Receipt patient ID (" +
                    receipt.getPatientNationalId() + ") does not match patient ID (" + this.getNationalId() + ").");
        }
        this.billingHistory.add(receipt);
    }

    public List<Receipt> getBillingHistory() {
        return Collections.unmodifiableList(new ArrayList<>(billingHistory));
    }

    public Optional<Receipt> getLatestReceipt() {
        if (billingHistory.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(billingHistory.get(billingHistory.size() - 1));
    }

    public BigDecimal getTotalPaidAmount() {
        BigDecimal total = BigDecimal.ZERO;
        for (Receipt r : billingHistory) {
            total = total.add(r.getAmountPaid());
        }
        return total;
    }

    public String getFormattedTotalPaid() {
        return String.format("$%,.2f", getTotalPaidAmount());
    }

    // =========================================================================
    // GETTERS & SETTERS
    // =========================================================================

    public BloodType getBloodType() { return bloodType; }
    public void setBloodType(BloodType bloodType) { this.bloodType = Objects.requireNonNull(bloodType); }

    public String getKnownAllergies() { return knownAllergies; }
    public void setKnownAllergies(String knownAllergies) {
        this.knownAllergies = (knownAllergies == null || knownAllergies.trim().isEmpty()) ? "None Known" : knownAllergies.trim();
    }

    public String getEmergencyContactNumber() { return emergencyContactNumber; }
    public void setEmergencyContactNumber(String emergencyContactNumber) {
        this.emergencyContactNumber = (emergencyContactNumber == null) ? "N/A" : emergencyContactNumber.trim();
    }

    public PatientStatus getStatus() { return status; }
    public void setStatus(PatientStatus status) { this.status = Objects.requireNonNull(status); }

    @Override
    public String toString() {
        return super.toString() + String.format(
                " | Status: %s | Blood: %s | Total Paid: %s | Records: %d | Invoices: %d",
                status.getLabel(), bloodType.getDisplayLabel(), getFormattedTotalPaid(),
                medicalHistory.size(), billingHistory.size()
        );
    }
}