package exceptions;

import java.io.Serial;

/**
 * Enterprise Invalid Workflow State Domain Exception.
 * Dispatched whenever a clinical state machine transition, terminal shift lifecycle,
 * physician consultation sequence, or triage intake sequence is violated.
 */
public class InvalidWorkflowException extends ClinicException {

    @Serial
    private static final long serialVersionUID = 503L;

    public static final String ERROR_CODE = "WORKFLOW_VIOLATION";

    private final String workflowDomain;
    private final String currentState;
    private final String targetState;
    private final String attemptedAction;
    private final String entityIdentifier;

    public InvalidWorkflowException(String message) {
        super(message, ERROR_CODE);
        this.workflowDomain = "GeneralWorkflow";
        this.currentState = "UNKNOWN";
        this.targetState = "NONE";
        this.attemptedAction = "UNSPECIFIED";
        this.entityIdentifier = "N/A";
    }

    public InvalidWorkflowException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
        this.workflowDomain = "GeneralWorkflow";
        this.currentState = "UNKNOWN";
        this.targetState = "NONE";
        this.attemptedAction = "UNSPECIFIED";
        this.entityIdentifier = "N/A";
    }

    public InvalidWorkflowException(String workflowDomain, String currentState, String targetState,
                                    String attemptedAction, String entityIdentifier, String reason) {
        this(workflowDomain, currentState, targetState, attemptedAction, entityIdentifier, reason, null);
    }

    public InvalidWorkflowException(String workflowDomain, String currentState, String targetState,
                                    String attemptedAction, String entityIdentifier, String reason, Throwable cause) {
        super(formatWorkflowMessage(workflowDomain, currentState, targetState, attemptedAction, entityIdentifier, reason),
                ERROR_CODE, cause);

        this.workflowDomain = normalize(workflowDomain, "GeneralWorkflow");
        this.currentState = normalize(currentState, "UNKNOWN");
        this.targetState = normalize(targetState, "NONE");
        this.attemptedAction = normalize(attemptedAction, "UNSPECIFIED");
        this.entityIdentifier = normalize(entityIdentifier, "N/A");
    }

    private static String normalize(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value.trim() : fallback;
    }

    private static String formatWorkflowMessage(String domain, String current, String target,
                                                String action, String id, String reason) {
        String cleanDomain = normalize(domain, "GeneralWorkflow");
        String cleanCurrent = normalize(current, "UNKNOWN");
        String cleanTarget = normalize(target, "NONE");
        String cleanAction = normalize(action, "UNSPECIFIED");
        String cleanId = normalize(id, "N/A");
        String cleanReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Illegal clinical transition requested.";

        return String.format("[%s] Action '%s' rejected for entity [%s]. Current State: '%s', Target State: '%s'. Reason: %s",
                cleanDomain, cleanAction, cleanId, cleanCurrent, cleanTarget, cleanReason);
    }

    // =========================================================================
    // ENTERPRISE FACTORY CONSTRUCTORS
    // =========================================================================

    public static InvalidWorkflowException illegalTransition(String entityId, Object currentStatus, Object targetStatus) {
        String current = (currentStatus != null) ? currentStatus.toString() : "UNKNOWN";
        String target = (targetStatus != null) ? targetStatus.toString() : "NONE";
        return new InvalidWorkflowException(
                "PatientLifecycle",
                current,
                target,
                "STATE_TRANSITION",
                entityId,
                String.format("State transition from '%s' to '%s' violates hospital clinical governance.", current, target)
        );
    }

    public static InvalidWorkflowException dischargeWithoutPayment(String patientId, Object currentStatus) {
        String current = (currentStatus != null) ? currentStatus.toString() : "UNKNOWN";
        return new InvalidWorkflowException(
                "PatientDischarge",
                current,
                "DISCHARGED",
                "FORMAL_DISCHARGE",
                patientId,
                "Formal hospital discharge denied: All clinical fees must be fully settled first (status must be PAYMENT_COMPLETED)."
        );
    }

    public static InvalidWorkflowException consultationWithoutWaiting(String patientId, Object currentStatus) {
        String current = (currentStatus != null) ? currentStatus.toString() : "UNKNOWN";
        return new InvalidWorkflowException(
                "ClinicalConsultation",
                current,
                "IN_VISIT",
                "START_EXAMINATION",
                patientId,
                "Physician consultation cannot commence: Patient must be in WAITING_FOR_DOCTOR status."
        );
    }

    public static InvalidWorkflowException triageWithoutRegistration(String patientId, Object currentStatus) {
        String current = (currentStatus != null) ? currentStatus.toString() : "UNKNOWN";
        return new InvalidWorkflowException(
                "NursingTriage",
                current,
                "WAITING_FOR_DOCTOR",
                "PERFORM_TRIAGE",
                patientId,
                "Nursing triage requires the patient to be in active REGISTERED status."
        );
    }

    public static InvalidWorkflowException doctorCapacityExceeded(String doctorId, int currentCount, int maxCapacity) {
        return new InvalidWorkflowException(
                "ClinicalCapacity",
                String.format("CAPACITY_%d_OF_%d", currentCount, maxCapacity),
                "OVER_CAPACITY",
                "ASSIGN_CONSULTATION",
                doctorId,
                String.format("Doctor daily patient capacity limit reached (%d/%d patients). Shift handover required.",
                        currentCount, maxCapacity)
        );
    }

    public static InvalidWorkflowException doctorUnavailable(String doctorId) {
        return new InvalidWorkflowException(
                "ClinicalAvailability",
                "UNAVAILABLE",
                "AVAILABLE",
                "ASSIGN_CONSULTATION",
                doctorId,
                "Physician is marked as off-duty or temporarily unavailable for consultations."
        );
    }

    public static InvalidWorkflowException cashierTerminalOffline(String cashierId, String terminalId) {
        return new InvalidWorkflowException(
                "PointOfSale",
                "OFF_DUTY",
                "ACTIVE_SHIFT",
                "PROCESS_TRANSACTION",
                cashierId,
                String.format("POS Terminal [%s] is offline. A register shift must be opened with float before processing transactions.", terminalId)
        );
    }

    public static InvalidWorkflowException shiftAlreadyOpen(String terminalId, String activeShiftId) {
        return new InvalidWorkflowException(
                "ShiftLifecycle",
                "SHIFT_ACTIVE",
                "SHIFT_OPEN",
                "OPEN_SHIFT",
                terminalId,
                String.format("Terminal register already has an active operational shift: [%s].", activeShiftId)
        );
    }

    public static InvalidWorkflowException noActiveShift(String terminalId) {
        return new InvalidWorkflowException(
                "ShiftLifecycle",
                "STANDBY",
                "SHIFT_CLOSED",
                "CLOSE_SHIFT",
                terminalId,
                "Cannot perform shift close or reconciliation because no active shift is open."
        );
    }

    // =========================================================================
    // AUDIT & TELEMETRY ACCESSORS
    // =========================================================================

    public String getWorkflowDomain() {
        return workflowDomain;
    }

    public String getCurrentState() {
        return currentState;
    }

    public String getTargetState() {
        return targetState;
    }

    public String getAttemptedAction() {
        return attemptedAction;
    }

    public String getEntityIdentifier() {
        return entityIdentifier;
    }
}