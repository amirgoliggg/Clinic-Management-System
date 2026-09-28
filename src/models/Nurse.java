package models;

import enums.PatientStatus;
import system.SystemClock;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Enterprise Nurse entity representing clinical nursing and triage professionals.
 * Integrates shift governance, vital signs telemetry, active triage processing,
 * thread-safe duty status, and seamless integration with GUI presentation layers.
 */
public final class Nurse extends Staff {
    private static final long serialVersionUID = 12L;

    private static final Pattern LICENSE_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]{4,32}$");

    public enum QualificationLevel {
        REGISTERED_NURSE(301, "Registered Nurse (RN)"),
        NURSE_PRACTITIONER(302, "Nurse Practitioner (NP)"),
        LICENSED_PRACTICAL_NURSE(303, "Licensed Practical Nurse (LPN)"),
        CLINICAL_SPECIALIST(304, "Clinical Nurse Specialist (CNS)");

        private final int code;
        private final String displayTitle;

        QualificationLevel(int code, String displayTitle) {
            this.code = code;
            this.displayTitle = displayTitle;
        }

        public int getCode() {
            return code;
        }

        public String getDisplayTitle() {
            return displayTitle;
        }

        public static QualificationLevel fromString(String input) {
            if (input == null || input.trim().isEmpty()) {
                throw new IllegalArgumentException("Qualification level input cannot be empty.");
            }
            String normalized = input.trim().toUpperCase();
            for (QualificationLevel level : QualificationLevel.values()) {
                if (level.name().equals(normalized) || level.displayTitle.equalsIgnoreCase(input.trim())) {
                    return level;
                }
            }
            throw new IllegalArgumentException("Unknown qualification level: " + input);
        }

        @Override
        public String toString() {
            return displayTitle;
        }
    }

    public enum Department {
        TRIAGE(401, "Emergency & Triage"),
        GENERAL_WARD(402, "General Inpatient Ward"),
        ICU(403, "Intensive Care Unit"),
        PEDIATRICS(404, "Pediatrics Unit"),
        OUTPATIENT(405, "Outpatient Clinic");

        private final int code;
        private final String departmentName;

        Department(int code, String departmentName) {
            this.code = code;
            this.departmentName = departmentName;
        }

        public int getCode() {
            return code;
        }

        public String getDepartmentName() {
            return departmentName;
        }

        public static Department fromString(String input) {
            if (input == null || input.trim().isEmpty()) {
                throw new IllegalArgumentException("Department input cannot be empty.");
            }
            String normalized = input.trim().toUpperCase();
            for (Department dept : Department.values()) {
                if (dept.name().equals(normalized) || dept.departmentName.equalsIgnoreCase(input.trim())) {
                    return dept;
                }
            }
            throw new IllegalArgumentException("Unknown department: " + input);
        }

        @Override
        public String toString() {
            return departmentName;
        }
    }

    public enum Shift {
        MORNING("Morning Shift", "07:00 - 15:00"),
        EVENING("Evening Shift", "15:00 - 23:00"),
        NIGHT("Night Shift", "23:00 - 07:00"),
        ROTATING("Rotating Roster", "Flexible Hours");

        private final String displayLabel;
        private final String schedule;

        Shift(String displayLabel, String schedule) {
            this.displayLabel = displayLabel;
            this.schedule = schedule;
        }

        public String getDisplayLabel() {
            return displayLabel;
        }

        public String getSchedule() {
            return schedule;
        }

        public static Shift fromString(String input) {
            if (input == null || input.trim().isEmpty()) {
                throw new IllegalArgumentException("Shift input cannot be empty.");
            }
            String normalized = input.trim().toUpperCase();
            for (Shift shift : Shift.values()) {
                if (shift.name().equals(normalized) ||
                        shift.displayLabel.equalsIgnoreCase(input.trim()) ||
                        shift.schedule.equalsIgnoreCase(input.trim())) {
                    return shift;
                }
            }
            throw new IllegalArgumentException("Unknown shift schedule: " + input);
        }

        @Override
        public String toString() {
            return displayLabel + " (" + schedule + ")";
        }
    }

    /**
     * Immutable physiological telemetry value object.
     * Contains comprehensive GUI formatting helpers and clinical alert logic.
     */
    public static final class VitalSigns implements Serializable {
        private static final long serialVersionUID = 13L;

        private final int systolicBp;
        private final int diastolicBp;
        private final int heartRateBpm;
        private final double temperatureCelsius;
        private final int oxygenSaturationPercent;
        private final int respiratoryRate;
        private final SystemClock recordedAt;

        public VitalSigns(int systolicBp, int diastolicBp, int heartRateBpm,
                          double temperatureCelsius, int oxygenSaturationPercent,
                          int respiratoryRate, SystemClock recordedAt) {

            if (systolicBp < 40 || systolicBp > 300) {
                throw new IllegalArgumentException("Systolic BP out of physiological bounds (40-300 mmHg).");
            }
            if (diastolicBp < 20 || diastolicBp > 200 || diastolicBp >= systolicBp) {
                throw new IllegalArgumentException("Invalid diastolic BP measurement.");
            }
            if (heartRateBpm < 20 || heartRateBpm > 260) {
                throw new IllegalArgumentException("Heart rate out of bounds (20-260 bpm).");
            }
            if (Double.isNaN(temperatureCelsius) || Double.isInfinite(temperatureCelsius) ||
                    temperatureCelsius < 30.0 || temperatureCelsius > 45.0) {
                throw new IllegalArgumentException("Body temperature out of biological limits (30.0-45.0 C).");
            }
            if (oxygenSaturationPercent < 40 || oxygenSaturationPercent > 100) {
                throw new IllegalArgumentException("Oxygen saturation (SpO2) out of range (40-100%).");
            }
            if (respiratoryRate < 4 || respiratoryRate > 80) {
                throw new IllegalArgumentException("Respiratory rate out of range (4-80 bpm).");
            }

            this.systolicBp = systolicBp;
            this.diastolicBp = diastolicBp;
            this.heartRateBpm = heartRateBpm;
            this.temperatureCelsius = temperatureCelsius;
            this.oxygenSaturationPercent = oxygenSaturationPercent;
            this.respiratoryRate = respiratoryRate;
            this.recordedAt = Objects.requireNonNull(recordedAt, "Recorded timestamp cannot be null.");
        }

        public int getSystolicBp() { return systolicBp; }
        public int getDiastolicBp() { return diastolicBp; }
        public int getHeartRateBpm() { return heartRateBpm; }
        public double getTemperatureCelsius() { return temperatureCelsius; }
        public int getOxygenSaturationPercent() { return oxygenSaturationPercent; }
        public int getRespiratoryRate() { return respiratoryRate; }
        public SystemClock getRecordedAt() { return recordedAt; }

        public boolean isCritical() {
            return systolicBp >= 180 || systolicBp <= 90 ||
                    diastolicBp >= 120 || diastolicBp <= 50 ||
                    heartRateBpm >= 130 || heartRateBpm <= 45 ||
                    temperatureCelsius >= 39.5 || temperatureCelsius <= 35.0 ||
                    oxygenSaturationPercent < 90 ||
                    respiratoryRate >= 30 || respiratoryRate <= 8;
        }

        public String getClinicalAlertSummary() {
            if (!isCritical()) {
                return "STABLE";
            }
            StringBuilder alert = new StringBuilder("CRITICAL ALERT: ");
            if (oxygenSaturationPercent < 90) alert.append(String.format("[Hypoxia SpO2: %d%%] ", oxygenSaturationPercent));
            if (systolicBp >= 180) alert.append(String.format("[Hypertensive Crisis BP: %d/%d] ", systolicBp, diastolicBp));
            if (systolicBp <= 90) alert.append(String.format("[Hypotension BP: %d/%d] ", systolicBp, diastolicBp));
            if (temperatureCelsius >= 39.5) alert.append(String.format("[High Fever Temp: %.1f C] ", temperatureCelsius));
            if (heartRateBpm >= 130 || heartRateBpm <= 45) alert.append(String.format("[Abnormal HR: %d bpm] ", heartRateBpm));
            return alert.toString().trim();
        }

        public String getBloodPressureDisplay() {
            return systolicBp + "/" + diastolicBp + " mmHg";
        }

        public String getHeartRateDisplay() {
            return heartRateBpm + " bpm";
        }

        public String getTemperatureDisplay() {
            return String.format("%.1f °C", temperatureCelsius);
        }

        public String getOxygenSaturationDisplay() {
            return oxygenSaturationPercent + "%";
        }

        public String getRespiratoryRateDisplay() {
            return respiratoryRate + " bpm";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VitalSigns that = (VitalSigns) o;
            return systolicBp == that.systolicBp &&
                    diastolicBp == that.diastolicBp &&
                    heartRateBpm == that.heartRateBpm &&
                    Double.compare(that.temperatureCelsius, temperatureCelsius) == 0 &&
                    oxygenSaturationPercent == that.oxygenSaturationPercent &&
                    respiratoryRate == that.respiratoryRate &&
                    recordedAt.equals(that.recordedAt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(systolicBp, diastolicBp, heartRateBpm, temperatureCelsius,
                    oxygenSaturationPercent, respiratoryRate, recordedAt);
        }

        @Override
        public String toString() {
            return String.format("BP: %s | HR: %s | Temp: %s | SpO2: %s | RR: %s",
                    getBloodPressureDisplay(), getHeartRateDisplay(), getTemperatureDisplay(),
                    getOxygenSaturationDisplay(), getRespiratoryRateDisplay());
        }
    }

    private final String nursingLicenseNumber;
    private QualificationLevel qualification;
    private Department assignedDepartment;
    private Shift currentShift;
    private volatile boolean onDuty;

    public Nurse(String nationalId, String fullName, Integer age, String contactNumber,
                 BigDecimal baseSalary, String nursingLicenseNumber, QualificationLevel qualification,
                 Department assignedDepartment, Shift currentShift) {

        super(nationalId, fullName, age, contactNumber, baseSalary);

        this.nursingLicenseNumber = validateLicenseNumber(nursingLicenseNumber);
        this.setQualification(qualification);
        this.setAssignedDepartment(assignedDepartment);
        this.setCurrentShift(currentShift);
        this.onDuty = true;
    }

    private Nurse(String newNationalId, Nurse source) {
        super(newNationalId, source);
        this.nursingLicenseNumber = source.nursingLicenseNumber;
        this.qualification = source.qualification;
        this.assignedDepartment = source.assignedDepartment;
        this.currentShift = source.currentShift;
        this.onDuty = source.onDuty;
    }

    @Override
    public Person cloneWithNewId(String newNationalId) {
        return new Nurse(newNationalId, this);
    }

    private String validateLicenseNumber(String license) {
        if (license == null || license.trim().isEmpty()) {
            throw new IllegalArgumentException("Nursing license number cannot be null or empty.");
        }
        String trimmed = license.trim();
        if (!LICENSE_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid nursing license format. Must be 4-32 alphanumeric characters.");
        }
        return trimmed;
    }

    /**
     * Executes clinical triage, enforces workflow lifecycle, and attaches a telemetry-backed record.
     */
    public MedicalRecord performTriage(Patient patient, String attendingDoctorId,
                                       VitalSigns vitals, String triageNotes, SystemClock timestamp) {
        Objects.requireNonNull(patient, "Patient cannot be null for triage.");
        Objects.requireNonNull(vitals, "Vital signs measurement cannot be null.");
        Objects.requireNonNull(timestamp, "Timestamp cannot be null.");

        if (attendingDoctorId == null || attendingDoctorId.trim().isEmpty()) {
            throw new IllegalArgumentException("Attending doctor ID must be assigned for triage intake.");
        }

        if (!this.onDuty) {
            throw new IllegalStateException("Nurse " + getFullName() + " is currently off-duty and cannot triage.");
        }

        if (patient.getStatus() != PatientStatus.REGISTERED) {
            throw new IllegalStateException("Invalid clinical transition: Patient " + patient.getNationalId() +
                    " must be in REGISTERED status for triage intake. Current status: " + patient.getStatus().getLabel());
        }

        patient.setStatus(PatientStatus.WAITING_FOR_DOCTOR);

        boolean critical = vitals.isCritical();
        String assessmentSummary = (critical ? "[CRITICAL ALERT] " : "[ROUTINE TRIAGE] ") + vitals.toString();

        String cleanNotes = (triageNotes == null || triageNotes.trim().isEmpty())
                ? "No subjective complaints noted."
                : triageNotes.trim();

        String fullAuditNotes = String.format("Triage Staff: %s (License: %s) | Status: %s | Notes: %s",
                getFullName(), nursingLicenseNumber, vitals.getClinicalAlertSummary(), cleanNotes);

        MedicalRecord triageRecord = new MedicalRecord(
                null,
                patient.getNationalId(),
                attendingDoctorId.trim(),
                timestamp,
                critical ? MedicalRecord.RecordType.EMERGENCY : MedicalRecord.RecordType.GENERAL_CHECKUP,
                MedicalRecord.RecordStatus.PRELIMINARY,
                critical ? MedicalRecord.ConfidentialityLevel.RESTRICTED : MedicalRecord.ConfidentialityLevel.NORMAL,
                assessmentSummary,
                "NONE",
                fullAuditNotes
        );

        patient.addMedicalRecord(triageRecord);
        return triageRecord;
    }

    public String getNursingLicenseNumber() {
        return nursingLicenseNumber;
    }

    public QualificationLevel getQualification() {
        return qualification;
    }

    public void setQualification(QualificationLevel qualification) {
        this.qualification = Objects.requireNonNull(qualification, "Qualification cannot be null.");
    }

    public Department getAssignedDepartment() {
        return assignedDepartment;
    }

    public void setAssignedDepartment(Department assignedDepartment) {
        this.assignedDepartment = Objects.requireNonNull(assignedDepartment, "Department cannot be null.");
    }

    public Shift getCurrentShift() {
        return currentShift;
    }

    public void setCurrentShift(Shift currentShift) {
        this.currentShift = Objects.requireNonNull(currentShift, "Shift cannot be null.");
    }

    public boolean isOnDuty() {
        return onDuty;
    }

    public void setOnDuty(boolean onDuty) {
        this.onDuty = onDuty;
    }

    @Override
    public String toString() {
        return super.toString() + String.format(
                " | License: %s | Level: %s | Dept: %s | Shift: %s | On-Duty: %s",
                nursingLicenseNumber, qualification.getDisplayTitle(),
                assignedDepartment.getDepartmentName(), currentShift.getDisplayLabel(), onDuty ? "YES" : "NO"
        );
    }
}