package models;

import enums.PatientStatus;
import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "patients")
public class Patient extends Person implements Serializable {

    @Serial
    private static final long serialVersionUID = 10L;

    public enum BloodType {
        A_POSITIVE, A_NEGATIVE, B_POSITIVE, B_NEGATIVE,
        AB_POSITIVE, AB_NEGATIVE, O_POSITIVE, O_NEGATIVE, UNKNOWN
    }

    @Column(name = "patient_id", unique = true, nullable = false, updatable = false)
    private String patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_type", nullable = false)
    private BloodType bloodType = BloodType.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false)
    private PatientStatus currentStatus = PatientStatus.WAITING_FOR_DOCTOR;

    @Column(name = "pending_bill", precision = 12, scale = 2, nullable = false)
    private BigDecimal pendingBill = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_doctor_id")
    private Doctor assignedDoctor;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_nid")
    private List<Receipt> receipts = new ArrayList<>();

    public Patient() {
        super();
        this.patientId = "PAT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.currentStatus = PatientStatus.WAITING_FOR_DOCTOR;
        this.pendingBill = BigDecimal.ZERO;
        this.bloodType = BloodType.UNKNOWN;
    }

    public Patient(String nationalId, String fullName, Integer age, String contactNumber, BloodType bloodType) {
        super(nationalId, fullName, age, contactNumber);
        this.patientId = "PAT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.bloodType = bloodType != null ? bloodType : BloodType.UNKNOWN;
        this.currentStatus = PatientStatus.WAITING_FOR_DOCTOR;
        this.pendingBill = BigDecimal.ZERO;
    }

    @PrePersist
    public void ensurePatientId() {
        if (this.patientId == null || this.patientId.isBlank()) {
            this.patientId = "PAT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        if (this.currentStatus == null) {
            this.currentStatus = PatientStatus.WAITING_FOR_DOCTOR;
        }
        if (this.pendingBill == null) {
            this.pendingBill = BigDecimal.ZERO;
        }
        if (this.bloodType == null) {
            this.bloodType = BloodType.UNKNOWN;
        }
    }

    @Override
    public Person cloneWithNewId(String newId) {
        Patient clone = new Patient(newId, this.getFullName(), this.getAge(), this.getContactNumber(), this.bloodType);
        clone.currentStatus = this.currentStatus;
        clone.pendingBill = this.pendingBill;
        return clone;
    }

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public BloodType getBloodType() { return bloodType; }
    public void setBloodType(BloodType bloodType) { this.bloodType = bloodType; }
    public PatientStatus getCurrentStatus() { return currentStatus; }
    public void setStatus(PatientStatus status) { this.currentStatus = status; }
    public BigDecimal getPendingBill() { return pendingBill != null ? pendingBill : BigDecimal.ZERO; }
    public void setPendingBill(BigDecimal pendingBill) { this.pendingBill = pendingBill; }
    public void addCharge(BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            this.pendingBill = getPendingBill().add(amount);
        }
    }
    public void deductPayment(BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            this.pendingBill = getPendingBill().subtract(amount);
            if (this.pendingBill.compareTo(BigDecimal.ZERO) < 0) {
                this.pendingBill = BigDecimal.ZERO;
            }
        }
    }
    public Doctor getAssignedDoctor() { return assignedDoctor; }
    public void setAssignedDoctor(Doctor assignedDoctor) { this.assignedDoctor = assignedDoctor; }
    public List<Receipt> getReceipts() { return receipts; }
    public void addReceipt(Receipt receipt) { this.receipts.add(receipt); }
}