package com.clinic.models;

import com.clinic.enums.PatientStatus;
import com.clinic.exceptions.InvalidWorkflowException;
import com.clinic.system.SystemClock;
import jakarta.persistence.*;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "doctors")
@PrimaryKeyJoinColumn(name = "national_id")
public class Doctor extends Staff {

    @Serial
    private static final long serialVersionUID = 25L;

    private static final String ROLE_DOCTOR = "DOCTOR";
    private static final String LICENSE_PREFIX = "MED-";
    private static final BigDecimal DEFAULT_CONSULTATION_FEE = new BigDecimal("150.00");
    private static final int DEFAULT_MAX_DAILY_PATIENTS = 30;

    public enum Specialization {
        GENERAL_PRACTICE, CARDIOLOGY, DERMATOLOGY, PEDIATRICS, NEUROLOGY,
        ORTHOPEDICS, PSYCHIATRY, INTERNAL_MEDICINE, OPHTHALMOLOGY, EMERGENCY_MEDICINE, OTHER
    }

    @Column(name = "medical_license_number", unique = true, nullable = false)
    private String medicalLicenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "specialization", nullable = false)
    private Specialization specialization;

    @Column(name = "consultation_fee", precision = 12, scale = 2, nullable = false)
    private BigDecimal consultationFee;

    @Column(name = "max_daily_patients", nullable = false)
    private int maxDailyPatients;

    @Column(name = "daily_patient_count", nullable = false)
    private int dailyPatientCount = 0;

    @Column(name = "last_consultation_date", length = 16)
    private String lastConsultationDate;

    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    // ==================== Constructors ====================

    protected Doctor() {
        super();
    }

    public Doctor(String nationalId, String fullName, Integer age, String contactNumber,
                  BigDecimal baseSalary, String medicalLicenseNumber, Specialization specialization,
                  BigDecimal consultationFee, int maxDailyPatients) {
        super(nationalId, fullName, age, contactNumber, nationalId,
                ROLE_DOCTOR, baseSalary, SystemClock.now(), EmploymentStatus.ACTIVE);
        this.medicalLicenseNumber = normalizeLicense(medicalLicenseNumber, getNationalId());
        this.specialization = (specialization != null) ? specialization : Specialization.GENERAL_PRACTICE;
        this.consultationFee = normalizeFee(consultationFee);
        this.maxDailyPatients = (maxDailyPatients > 0) ? maxDailyPatients : DEFAULT_MAX_DAILY_PATIENTS;
        this.dailyPatientCount = 0;
        this.lastConsultationDate = SystemClock.now().toGregorianDateString();
        this.available = true;
    }

    private Doctor(String newNationalId, Doctor source) {
        super(newNationalId, source);
        this.medicalLicenseNumber = LICENSE_PREFIX + newNationalId;
        this.specialization = source.specialization;
        this.consultationFee = source.consultationFee;
        this.maxDailyPatients = source.maxDailyPatients;
        this.dailyPatientCount = source.dailyPatientCount;
        this.lastConsultationDate = source.lastConsultationDate;
        this.available = source.available;
    }

    // ==================== Lifecycle & Helper Methods ====================

    @PrePersist
    public void ensureDoctorDefaults() {
        super.ensureStaffDefaults();
        if (this.medicalLicenseNumber == null || this.medicalLicenseNumber.isBlank()) {
            this.medicalLicenseNumber = LICENSE_PREFIX + getNationalId();
        }
        if (this.specialization == null) {
            this.specialization = Specialization.GENERAL_PRACTICE;
        }
        if (this.consultationFee == null || this.consultationFee.compareTo(BigDecimal.ZERO) <= 0) {
            this.consultationFee = DEFAULT_CONSULTATION_FEE;
        }
        if (this.maxDailyPatients <= 0) {
            this.maxDailyPatients = DEFAULT_MAX_DAILY_PATIENTS;
        }
        if (this.lastConsultationDate == null) {
            this.lastConsultationDate = SystemClock.now().toGregorianDateString();
        }
    }

    @Override
    public Person cloneWithNewId(String newNationalId) {
        return new Doctor(newNationalId, this);
    }

    private static String normalizeLicense(String license, String fallbackId) {
        return (license != null && !license.isBlank()) ? license.trim() : LICENSE_PREFIX + fallbackId;
    }

    private static BigDecimal normalizeFee(BigDecimal fee) {
        return (fee != null && fee.compareTo(BigDecimal.ZERO) > 0) ? fee : DEFAULT_CONSULTATION_FEE;
    }

    private void syncWorkingDayCounter() {
        String today = SystemClock.now().toGregorianDateString();
        if (this.lastConsultationDate == null || !this.lastConsultationDate.equals(today)) {
            this.dailyPatientCount = 0;
            this.lastConsultationDate = today;
        }
    }

    // ==================== Clinical Domain Logic ====================

    public MedicalRecord conductConsultation(Patient patient, String diagnosis, String prescription, String notes) {
        Objects.requireNonNull(patient, "Patient cannot be null.");
        syncWorkingDayCounter();

        if (!this.available || !this.isActive()) {
            throw InvalidWorkflowException.doctorUnavailable(this.getNationalId());
        }
        if (this.dailyPatientCount >= this.maxDailyPatients) {
            throw InvalidWorkflowException.doctorCapacityExceeded(
                    this.getNationalId(), this.dailyPatientCount, this.maxDailyPatients
            );
        }

        PatientStatus current = patient.getCurrentStatus();
        if (current != PatientStatus.WAITING_FOR_DOCTOR && current != PatientStatus.IN_VISIT) {
            throw InvalidWorkflowException.consultationWithoutWaiting(patient.getNationalId(), current);
        }

        BigDecimal effectiveFee = normalizeFee(this.consultationFee);
        patient.addCharge(effectiveFee);
        patient.setAssignedDoctor(this);
        patient.transitionTo(PatientStatus.WAITING_FOR_PAYMENT);

        this.dailyPatientCount++;

        return MedicalRecord.create(
                UUID.randomUUID().toString(),
                patient.getNationalId(),
                this.getNationalId(),
                diagnosis,
                prescription,
                notes
        );
    }

    public void resetDailyPatientCount() {
        this.dailyPatientCount = 0;
        this.lastConsultationDate = SystemClock.now().toGregorianDateString();
    }

    public boolean hasReachedDailyCapacity() {
        syncWorkingDayCounter();
        return this.dailyPatientCount >= this.maxDailyPatients;
    }

    // ==================== Getters & Setters ====================

    public String getMedicalLicenseNumber() {
        return medicalLicenseNumber;
    }

    public void setMedicalLicenseNumber(String medicalLicenseNumber) {
        this.medicalLicenseNumber = normalizeLicense(medicalLicenseNumber, getNationalId());
    }

    public Specialization getSpecialization() {
        return specialization;
    }

    public void setSpecialization(Specialization specialization) {
        this.specialization = (specialization != null) ? specialization : Specialization.GENERAL_PRACTICE;
    }

    public BigDecimal getConsultationFee() {
        return normalizeFee(consultationFee);
    }

    public void setConsultationFee(BigDecimal consultationFee) {
        this.consultationFee = normalizeFee(consultationFee);
    }

    public int getMaxDailyPatients() {
        return maxDailyPatients;
    }

    public void setMaxDailyPatients(int maxDailyPatients) {
        if (maxDailyPatients <= 0) {
            throw new IllegalArgumentException("ظرفیت پذیرش روزانه پزشک باید بزرگ‌تر از صفر باشد.");
        }
        this.maxDailyPatients = maxDailyPatients;
    }

    public int getDailyPatientCount() {
        syncWorkingDayCounter();
        return dailyPatientCount;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}