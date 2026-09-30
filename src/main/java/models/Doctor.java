package models;

import enums.PatientStatus;
import system.SystemClock;
import jakarta.persistence.*;
import java.io.Serial;
import java.math.BigDecimal;

@Entity
@Table(name = "doctors")
public final class Doctor extends Staff {

    @Serial
    private static final long serialVersionUID = 25L;

    public enum Specialization {
        GENERAL_PRACTICE, CARDIOLOGY, DERMATOLOGY, PEDIATRICS, NEUROLOGY,
        ORTHOPEDICS, PSYCHIATRY, INTERNAL_MEDICINE, OPHTHALMOLOGY, EMERGENCY_MEDICINE, OTHER;
    }

    @Column(name = "medical_license_number", unique = true, nullable = false)
    private String medicalLicenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "specialization", nullable = false)
    private Specialization specialization;

    @Column(name = "consultation_fee", precision = 12, scale = 2)
    private BigDecimal consultationFee;

    @Column(name = "max_daily_patients")
    private int maxDailyPatients;

    @Transient
    private int dailyPatientCount = 0;

    @Transient
    private volatile boolean available = true;

    protected Doctor() {}

    public Doctor(String nationalId, String fullName, Integer age, String contactNumber,
                  BigDecimal baseSalary, String medicalLicenseNumber, Specialization specialization,
                  BigDecimal consultationFee, int maxDailyPatients) {
        super(nationalId, fullName, age, contactNumber, nationalId, "DOCTOR", baseSalary, SystemClock.now(), EmploymentStatus.ACTIVE);
        this.medicalLicenseNumber = medicalLicenseNumber.trim();
        this.specialization = specialization;
        this.consultationFee = (consultationFee != null && consultationFee.compareTo(BigDecimal.ZERO) > 0) ? consultationFee : new BigDecimal("150.00");
        this.maxDailyPatients = maxDailyPatients;
    }

    private Doctor(String newNationalId, Doctor source) {
        super(newNationalId, source);
        this.medicalLicenseNumber = source.medicalLicenseNumber;
        this.specialization = source.specialization;
        this.consultationFee = source.consultationFee;
        this.maxDailyPatients = source.maxDailyPatients;
    }

    @Override
    public Person cloneWithNewId(String newNationalId) { return new Doctor(newNationalId, this); }

    public MedicalRecord conductConsultation(Patient patient, String diagnosis, String prescription, String notes) {
        if (!this.available || this.dailyPatientCount >= this.maxDailyPatients) {
            throw new IllegalStateException("ظرفیت ویزیت روزانه پزشک تکمیل است.");
        }

        // افزودن هزینه ویزیت به بدهی بیمار
        BigDecimal fee = (this.consultationFee != null && this.consultationFee.compareTo(BigDecimal.ZERO) > 0)
                ? this.consultationFee
                : new BigDecimal("150.00");
        patient.addCharge(fee);

        patient.setStatus(PatientStatus.WAITING_FOR_PAYMENT);
        this.dailyPatientCount++;
        return MedicalRecord.create(java.util.UUID.randomUUID().toString(), patient.getNationalId(), this.getNationalId(), diagnosis, prescription, notes);
    }

    public void resetDailyPatientCount() { this.dailyPatientCount = 0; }
    public boolean hasReachedDailyCapacity() { return this.dailyPatientCount >= this.maxDailyPatients; }
    public String getMedicalLicenseNumber() { return medicalLicenseNumber; }
    public Specialization getSpecialization() { return specialization; }
    public BigDecimal getConsultationFee() { return consultationFee; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}