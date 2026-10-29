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
@Table(name = "nurses")
@PrimaryKeyJoinColumn(name = "national_id")
public class Nurse extends Staff {

    @Serial
    private static final long serialVersionUID = 30L;

    private static final String ROLE_NURSE = "NURSE";
    private static final String LICENSE_PREFIX = "NRS-";
    private static final String DEFAULT_DEPARTMENT = "بخش عمومی";

    private static final double MIN_VALID_TEMP = 30.0;
    private static final double MAX_VALID_TEMP = 45.0;
    private static final int MIN_HEART_RATE = 20;
    private static final int MAX_HEART_RATE = 250;

    public enum ShiftType {
        MORNING("شیفت صبح"),
        EVENING("شیفت عصر"),
        NIGHT("شیفت شب"),
        ROTATING("شیفت چرخشی");

        private final String displayName;

        ShiftType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    @Column(name = "nursing_license_number", unique = true, nullable = false)
    private String nursingLicenseNumber;

    @Column(name = "department", nullable = false)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift_type", nullable = false)
    private ShiftType shiftType = ShiftType.ROTATING;

    @Column(name = "triage_qualified", nullable = false)
    private boolean triageQualified = true;

    @Column(name = "daily_triage_count", nullable = false)
    private int dailyTriageCount = 0;

    @Column(name = "last_activity_date", length = 16)
    private String lastActivityDate;

    // ==================== Constructors ====================

    protected Nurse() {
        super();
    }

    public Nurse(String nationalId, String fullName, Integer age, String contactNumber,
                 BigDecimal baseSalary, String nursingLicenseNumber, String department) {
        this(nationalId, fullName, age, contactNumber, baseSalary,
                nursingLicenseNumber, department, ShiftType.ROTATING, true);
    }

    public Nurse(String nationalId, String fullName, Integer age, String contactNumber,
                 BigDecimal baseSalary, String nursingLicenseNumber, String department,
                 ShiftType shiftType, boolean triageQualified) {
        super(nationalId, fullName, age, contactNumber, nationalId,
                ROLE_NURSE, baseSalary, SystemClock.now(), EmploymentStatus.ACTIVE);
        this.nursingLicenseNumber = normalizeLicenseNumber(nursingLicenseNumber, getNationalId());
        this.department = normalizeDepartment(department);
        this.shiftType = (shiftType != null) ? shiftType : ShiftType.ROTATING;
        this.triageQualified = triageQualified;
        this.dailyTriageCount = 0;
        this.lastActivityDate = SystemClock.now().toGregorianDateString();
    }

    private Nurse(String newNationalId, Nurse source) {
        super(newNationalId, source);
        this.nursingLicenseNumber = LICENSE_PREFIX + newNationalId;
        this.department = source.department;
        this.shiftType = source.shiftType;
        this.triageQualified = source.triageQualified;
        this.dailyTriageCount = 0;
        this.lastActivityDate = SystemClock.now().toGregorianDateString();
    }

    // ==================== Lifecycle & Helper Methods ====================

    @PrePersist
    public void ensureNurseDefaults() {
        super.ensureStaffDefaults();
        if (this.nursingLicenseNumber == null || this.nursingLicenseNumber.isBlank()) {
            this.nursingLicenseNumber = LICENSE_PREFIX + getNationalId();
        }
        if (this.department == null || this.department.isBlank()) {
            this.department = DEFAULT_DEPARTMENT;
        }
        if (this.shiftType == null) {
            this.shiftType = ShiftType.ROTATING;
        }
        if (this.lastActivityDate == null) {
            this.lastActivityDate = SystemClock.now().toGregorianDateString();
        }
    }

    @Override
    public Person cloneWithNewId(String newNationalId) {
        return new Nurse(newNationalId, this);
    }

    private static String normalizeLicenseNumber(String license, String fallbackId) {
        return (license != null && !license.isBlank()) ? license.trim() : LICENSE_PREFIX + fallbackId;
    }

    private static String normalizeDepartment(String dept) {
        return (dept != null && !dept.isBlank()) ? dept.trim() : DEFAULT_DEPARTMENT;
    }

    private void syncDailyCounter() {
        String today = SystemClock.now().toGregorianDateString();
        if (this.lastActivityDate == null || !this.lastActivityDate.equals(today)) {
            this.dailyTriageCount = 0;
            this.lastActivityDate = today;
        }
    }

    // ==================== Clinical & Triage Domain Logic ====================

    /**
     * انجام تریاژ، ثبت علائم حیاتی اولیه و تشکیل پرونده ارزیابی پرستاری پیش از ویزیت پزشک.
     */
    public MedicalRecord performTriage(Patient patient, String bloodPressure,
                                       double bodyTemperatureCelsius, int heartRateBpm,
                                       String chiefComplaint, String nursingNotes) {
        Objects.requireNonNull(patient, "بیمار نمی‌تواند خالی باشد.");
        syncDailyCounter();

        if (!isActive()) {
            throw new IllegalStateException("پرستار غیرفعال مجاز به ثبت تریاژ نیست.");
        }
        if (!this.triageQualified) {
            throw new IllegalStateException("این پرستار مجوز انجام تریاژ را ندارد.");
        }

        PatientStatus status = patient.getCurrentStatus();
        if (status != PatientStatus.REGISTERED
                && status != PatientStatus.NEW_ARRIVAL
                && status != PatientStatus.WAITING_FOR_DOCTOR
                && status != PatientStatus.IN_VISIT) {
            throw InvalidWorkflowException.triageWithoutRegistration(patient.getNationalId(), status);
        }

        validateVitalSigns(bloodPressure, bodyTemperatureCelsius, heartRateBpm);

        if (status == PatientStatus.REGISTERED || status == PatientStatus.NEW_ARRIVAL) {
            patient.transitionTo(PatientStatus.WAITING_FOR_DOCTOR);
        }

        String formattedVitals = String.format(
                "BP: %s | Temp: %.1f°C | HR: %d bpm | شکایت اصلی: %s",
                bloodPressure.trim(),
                bodyTemperatureCelsius,
                heartRateBpm,
                (chiefComplaint != null && !chiefComplaint.isBlank()) ? chiefComplaint.trim() : "ثبت نشده"
        );

        String combinedNotes = (nursingNotes != null && !nursingNotes.isBlank())
                ? formattedVitals + " | یادداشت پرستار: " + nursingNotes.trim()
                : formattedVitals;

        this.dailyTriageCount++;

        return MedicalRecord.create(
                UUID.randomUUID().toString(),
                patient.getNationalId(),
                this.getNationalId(),
                "ارزیابی اولیه و تریاژ پرستاری (" + this.department + ")",
                "نیازمند معاینه و تجویز پزشک",
                combinedNotes
        );
    }

    /**
     * ثبت خدمات پرستاری (مانند تزریقات، پانسمان، سرم‌تراپی) و افزودن هزینه خدمت به صورتحساب بیمار.
     */
    public MedicalRecord administerNursingCare(Patient patient, String careProcedure,
                                               BigDecimal serviceFee, String careNotes) {
        Objects.requireNonNull(patient, "بیمار نمی‌تواند خالی باشد.");
        if (!isActive()) {
            throw new IllegalStateException("پرستار غیرفعال مجاز به ثبت خدمات پرستاری نیست.");
        }
        if (careProcedure == null || careProcedure.isBlank()) {
            throw new IllegalArgumentException("شرح خدمت پرستاری نمی‌تواند خالی باشد.");
        }

        PatientStatus currentStatus = patient.getCurrentStatus();
        if (currentStatus == null || currentStatus.isTerminal()) {
            throw InvalidWorkflowException.illegalTransition(
                    patient.getNationalId(), currentStatus, PatientStatus.IN_TREATMENT
            );
        }

        if (serviceFee != null && serviceFee.compareTo(BigDecimal.ZERO) > 0) {
            patient.addCharge(serviceFee);
            if (currentStatus != PatientStatus.WAITING_FOR_PAYMENT) {
                patient.transitionTo(PatientStatus.WAITING_FOR_PAYMENT);
            }
        }

        syncDailyCounter();
        this.dailyTriageCount++;

        return MedicalRecord.create(
                UUID.randomUUID().toString(),
                patient.getNationalId(),
                this.getNationalId(),
                "خدمت پرستاری: " + careProcedure.trim(),
                "انجام شده در بخش: " + this.department,
                (careNotes != null && !careNotes.isBlank()) ? careNotes.trim() : "بدون توضیحات تکمیلی"
        );
    }

    private static void validateVitalSigns(String bloodPressure, double temp, int heartRate) {
        if (bloodPressure == null || bloodPressure.isBlank()) {
            throw new IllegalArgumentException("فشار خون بیمار باید ثبت شود.");
        }
        if (!Double.isFinite(temp) || temp < MIN_VALID_TEMP || temp > MAX_VALID_TEMP) {
            throw new IllegalArgumentException("دمای بدن ثبت شده خارج از محدوده معتبر بالینی است.");
        }
        if (heartRate < MIN_HEART_RATE || heartRate > MAX_HEART_RATE) {
            throw new IllegalArgumentException("ضربان قلب ثبت شده خارج از محدوده معتبر بالینی است.");
        }
    }

    // ==================== Getters & Setters ====================

    public String getNursingLicenseNumber() {
        return nursingLicenseNumber;
    }

    public void setNursingLicenseNumber(String nursingLicenseNumber) {
        this.nursingLicenseNumber = normalizeLicenseNumber(nursingLicenseNumber, getNationalId());
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = normalizeDepartment(department);
    }

    public ShiftType getShiftType() {
        return shiftType;
    }

    public void setShiftType(ShiftType shiftType) {
        this.shiftType = (shiftType != null) ? shiftType : ShiftType.ROTATING;
    }

    public boolean isTriageQualified() {
        return triageQualified;
    }

    public void setTriageQualified(boolean triageQualified) {
        this.triageQualified = triageQualified;
    }

    public int getDailyTriageCount() {
        syncDailyCounter();
        return dailyTriageCount;
    }
}