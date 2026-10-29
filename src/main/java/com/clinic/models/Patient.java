package com.clinic.models;

import com.clinic.enums.PatientStatus;
import com.clinic.exceptions.InvalidWorkflowException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "patients")
@PrimaryKeyJoinColumn(name = "national_id")
public class Patient extends Person implements Serializable {

    @Serial
    private static final long serialVersionUID = 10L;

    private static final String PATIENT_ID_PREFIX = "PAT-";

    public enum BloodType {
        A_POSITIVE, A_NEGATIVE,
        B_POSITIVE, B_NEGATIVE,
        AB_POSITIVE, AB_NEGATIVE,
        O_POSITIVE, O_NEGATIVE,
        UNKNOWN
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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assigned_doctor_id")
    @JsonIgnoreProperties({
            "encodedPassword", "passwordHash", "paymentHistory",
            "baseSalary", "contactNumber", "age", "hireDate",
            "lastPaidMonthNumber", "hibernateLazyInitializer", "handler"
    })
    private Doctor assignedDoctor;

    @JsonIgnore
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_nid")
    private List<Receipt> receipts = new ArrayList<>();

    // ==================== Constructors ====================

    public Patient() {
        super();
        this.patientId = generatePatientId();
        this.bloodType = BloodType.UNKNOWN;
        this.currentStatus = PatientStatus.WAITING_FOR_DOCTOR;
        this.pendingBill = BigDecimal.ZERO;
    }

    public Patient(String nationalId, String fullName, Integer age, String contactNumber, BloodType bloodType) {
        super(nationalId, fullName, age, contactNumber);
        this.patientId = generatePatientId();
        this.bloodType = (bloodType != null) ? bloodType : BloodType.UNKNOWN;
        this.currentStatus = PatientStatus.WAITING_FOR_DOCTOR;
        this.pendingBill = BigDecimal.ZERO;
    }

    // ==================== Lifecycle & Helper Methods ====================

    @PrePersist
    public void ensurePatientId() {
        if (this.patientId == null || this.patientId.isBlank()) {
            this.patientId = generatePatientId();
        }
        if (this.bloodType == null) {
            this.bloodType = BloodType.UNKNOWN;
        }
        if (this.currentStatus == null) {
            this.currentStatus = PatientStatus.WAITING_FOR_DOCTOR;
        }
        if (this.pendingBill == null) {
            this.pendingBill = BigDecimal.ZERO;
        }
    }

    private static String generatePatientId() {
        return PATIENT_ID_PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public Person cloneWithNewId(String newId) {
        Patient clone = new Patient(newId, this.getFullName(), this.getAge(), this.getContactNumber(), this.bloodType);
        clone.currentStatus = this.currentStatus;
        clone.pendingBill = this.pendingBill;
        return clone;
    }

    // ==================== Domain Business Logic ====================

    public void transitionTo(PatientStatus targetStatus) {
        if (targetStatus == null) {
            throw new IllegalArgumentException("وضعیت هدف بیمار نمی‌تواند خالی باشد.");
        }
        if (this.currentStatus != null && !this.currentStatus.canTransitionTo(targetStatus)) {
            throw InvalidWorkflowException.illegalTransition(getNationalId(), this.currentStatus, targetStatus);
        }
        this.currentStatus = targetStatus;
    }

    public void addCharge(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("مبلغ هزینه اضافه شده باید بزرگ‌تر از صفر باشد.");
        }
        this.pendingBill = getPendingBill().add(amount);
    }

    public void deductPayment(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("مبلغ کسر شده باید بزرگ‌تر از صفر باشد.");
        }
        if (amount.compareTo(getPendingBill()) > 0) {
            throw new IllegalArgumentException("مبلغ پرداختی بیشتر از بدهی جاری بیمار است.");
        }
        this.pendingBill = getPendingBill().subtract(amount);
    }

    public void addReceipt(Receipt receipt) {
        Objects.requireNonNull(receipt, "Receipt cannot be null.");
        this.receipts.add(receipt);
    }

    // ==================== Getters & Setters ====================

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        if (patientId != null && !patientId.isBlank()) {
            this.patientId = patientId.trim();
        }
    }

    public BloodType getBloodType() {
        return bloodType;
    }

    public void setBloodType(BloodType bloodType) {
        this.bloodType = (bloodType != null) ? bloodType : BloodType.UNKNOWN;
    }

    public PatientStatus getCurrentStatus() {
        return currentStatus;
    }

    public void setStatus(PatientStatus status) {
        transitionTo(status);
    }

    public BigDecimal getPendingBill() {
        return (pendingBill != null) ? pendingBill : BigDecimal.ZERO;
    }

    public void setPendingBill(BigDecimal pendingBill) {
        if (pendingBill != null && pendingBill.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("مبلغ بدهی نمی‌تواند منفی باشد.");
        }
        this.pendingBill = (pendingBill != null) ? pendingBill : BigDecimal.ZERO;
    }

    public Doctor getAssignedDoctor() {
        return assignedDoctor;
    }

    public void setAssignedDoctor(Doctor assignedDoctor) {
        this.assignedDoctor = assignedDoctor;
    }

    @JsonIgnore
    public List<Receipt> getReceipts() {
        return receipts;
    }
}