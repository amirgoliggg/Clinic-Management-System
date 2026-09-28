package models;

import enums.PatientStatus;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Enterprise Receptionist entity representing front-desk and patient lifecycle intake officers.
 * Governs initial admissions, return check-ins, admission cancellations, formal patient discharges,
 * and tracks frontline operational metrics for administrative GUI dashboards.
 */
public final class Receptionist extends Staff {
    private static final long serialVersionUID = 26L;

    private static final Pattern WORKSTATION_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]{2,20}$");

    public enum DeskLocation {
        MAIN_LOBBY(501, "Main Lobby - Desk A"),
        EMERGENCY_ADMISSION(502, "Emergency Triage Intake"),
        OUTPATIENT_RECEPTION(503, "Outpatient Care Desk"),
        PEDIATRIC_INTAKE(504, "Pediatric Clinic Reception"),
        VIP_CONCIERGE(505, "VIP & Executive Services Desk");

        private final int code;
        private final String displayLabel;

        DeskLocation(int code, String displayLabel) {
            this.code = code;
            this.displayLabel = displayLabel;
        }

        public int getCode() {
            return code;
        }

        public String getDisplayLabel() {
            return displayLabel;
        }

        public static DeskLocation fromString(String input) {
            if (input == null || input.trim().isEmpty()) {
                throw new IllegalArgumentException("Desk location input cannot be empty.");
            }
            String normalized = input.trim().toUpperCase();
            for (DeskLocation loc : DeskLocation.values()) {
                if (loc.name().equals(normalized) || loc.displayLabel.equalsIgnoreCase(input.trim())) {
                    return loc;
                }
            }
            throw new IllegalArgumentException("Unknown desk location: " + input);
        }

        @Override
        public String toString() {
            return displayLabel;
        }
    }

    private final String workstationId;
    private DeskLocation deskLocation;
    private volatile boolean onDuty;
    private int admittedPatientsCount;
    private int dischargedPatientsCount;
    private int cancelledAdmissionsCount;

    /**
     * Primary constructor for provisioning an authorized receptionist.
     */
    public Receptionist(String nationalId, String fullName, Integer age, String contactNumber,
                        BigDecimal baseSalary, String workstationId, DeskLocation deskLocation) {

        super(nationalId, fullName, age, contactNumber, baseSalary);

        this.workstationId = validateWorkstationId(workstationId);
        this.setDeskLocation(deskLocation);
        this.onDuty = true;
        this.admittedPatientsCount = 0;
        this.dischargedPatientsCount = 0;
        this.cancelledAdmissionsCount = 0;
    }

    /**
     * Protected Copy Constructor for Identity Migration (ID change).
     */
    private Receptionist(String newNationalId, Receptionist source) {
        super(newNationalId, source);
        this.workstationId = source.workstationId;
        this.deskLocation = source.deskLocation;
        this.onDuty = source.onDuty;
        this.admittedPatientsCount = source.admittedPatientsCount;
        this.dischargedPatientsCount = source.dischargedPatientsCount;
        this.cancelledAdmissionsCount = source.cancelledAdmissionsCount;
    }

    @Override
    public Person cloneWithNewId(String newNationalId) {
        return new Receptionist(newNationalId, this);
    }

    private String validateWorkstationId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Workstation ID cannot be null or empty.");
        }
        String trimmed = id.trim();
        if (!WORKSTATION_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid workstation ID format. Must be 2-20 alphanumeric characters.");
        }
        return trimmed;
    }

    /**
     * Admittance Factory: Instantiates and admits a new Patient into the hospital clinical pipeline.
     */
    public Patient registerPatient(String nationalId, String fullName, Integer age, String contactNumber,
                                   Patient.BloodType bloodType, String allergies, String emergencyContact) {

        if (!this.onDuty) {
            throw new IllegalStateException("Receptionist " + getFullName() + " is currently off-duty and cannot admit patients.");
        }

        Patient newPatient = new Patient(
                nationalId,
                fullName,
                age,
                contactNumber,
                bloodType,
                allergies,
                emergencyContact
        );

        this.admittedPatientsCount++;
        return newPatient;
    }

    /**
     * Re-admits an existing registered patient back into the clinical workflow.
     */
    public void checkInExistingPatient(Patient patient) {
        Objects.requireNonNull(patient, "Patient cannot be null.");

        if (!this.onDuty) {
            throw new IllegalStateException("Receptionist " + getFullName() + " is currently off-duty.");
        }

        if (patient.getStatus() == PatientStatus.IN_VISIT || patient.getStatus() == PatientStatus.WAITING_FOR_DOCTOR) {
            throw new IllegalStateException("Patient is already actively in the queue or being examined.");
        }

        patient.setStatus(PatientStatus.REGISTERED);
        this.admittedPatientsCount++;
    }

    /**
     * Formal Hospital Discharge: Finalizes patient visit and closes clinical pipeline.
     */
    public void dischargePatient(Patient patient) {
        dischargePatient(patient, "Routine discharge after clinical completion and fee settlement.");
    }

    /**
     * Formal Hospital Discharge with audit remarks.
     */
    public void dischargePatient(Patient patient, String dischargeRemarks) {
        Objects.requireNonNull(patient, "Patient cannot be null for discharge.");

        if (!this.onDuty) {
            throw new IllegalStateException("Receptionist " + getFullName() + " is off-duty and cannot discharge patients.");
        }

        if (patient.getStatus() == PatientStatus.DISCHARGED) {
            throw new IllegalStateException("Patient " + patient.getNationalId() + " has already been discharged.");
        }

        if (patient.getStatus() != PatientStatus.PAYMENT_COMPLETED) {
            throw new IllegalStateException("Discharge denied: Patient " + patient.getNationalId() +
                    " must settle all billing charges before formal hospital discharge. Current status: " +
                    patient.getStatus().getLabel());
        }

        // Finalize state
        patient.setStatus(PatientStatus.DISCHARGED);
        this.dischargedPatientsCount++;
    }

    /**
     * Cancels an admission queue position prior to treatment or checkout.
     */
    public void cancelPatientAdmission(Patient patient, String reason) {
        Objects.requireNonNull(patient, "Patient reference cannot be null.");

        if (!this.onDuty) {
            throw new IllegalStateException("Receptionist " + getFullName() + " is off-duty and cannot modify admission state.");
        }

        if (patient.getStatus() == PatientStatus.DISCHARGED || patient.getStatus() == PatientStatus.PAYMENT_COMPLETED) {
            throw new IllegalStateException("Cannot cancel an encounter that has already settled payment or concluded.");
        }

        patient.setStatus(PatientStatus.CANCELLED);
        this.cancelledAdmissionsCount++;
    }

    public String getWorkstationId() {
        return workstationId;
    }

    public DeskLocation getDeskLocation() {
        return deskLocation;
    }

    public void setDeskLocation(DeskLocation deskLocation) {
        this.deskLocation = Objects.requireNonNull(deskLocation, "Desk location cannot be null.");
    }

    public boolean isOnDuty() {
        return onDuty;
    }

    public void setOnDuty(boolean onDuty) {
        this.onDuty = onDuty;
    }

    public int getAdmittedPatientsCount() {
        return admittedPatientsCount;
    }

    public int getDischargedPatientsCount() {
        return dischargedPatientsCount;
    }

    public int getCancelledAdmissionsCount() {
        return cancelledAdmissionsCount;
    }

    public String getOperationalSummary() {
        return String.format("Admissions: %d | Discharged: %d | Cancelled: %d",
                admittedPatientsCount, dischargedPatientsCount, cancelledAdmissionsCount);
    }

    @Override
    public String toString() {
        return super.toString() + String.format(
                " | Terminal: %s | Location: %s | On-Duty: %s | %s",
                workstationId, deskLocation.getDisplayLabel(), onDuty ? "YES" : "NO", getOperationalSummary()
        );
    }
}