package enums;

import java.io.Serializable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Enterprise Clinical Lifecycle State Machine for hospital patients.
 * Enforces valid transition paths, categories, GUI visual telemetry,
 * and high-assurance parsing contracts.
 */
public enum PatientStatus implements Serializable {

    REGISTERED(
            100,
            "Admitted & Registered",
            StatusCategory.INTAKE,
            "#17a2b8",
            "badge-info"
    ),
    WAITING_FOR_DOCTOR(
            200,
            "Awaiting Physician Consultation",
            StatusCategory.CLINICAL,
            "#ffc107",
            "badge-warning"
    ),
    IN_VISIT(
            250,
            "Under Clinical Examination",
            StatusCategory.CLINICAL,
            "#007bff",
            "badge-primary"
    ),
    WAITING_FOR_PAYMENT(
            300,
            "Awaiting Fee Settlement",
            StatusCategory.FINANCIAL,
            "#fd7e14",
            "badge-secondary"
    ),
    PAYMENT_COMPLETED(
            350,
            "Settlement Finalized",
            StatusCategory.FINANCIAL,
            "#20c997",
            "badge-teal"
    ),
    DISCHARGED(
            400,
            "Formally Discharged",
            StatusCategory.TERMINAL,
            "#28a745",
            "badge-success"
    ),
    CANCELLED(
            500,
            "Encounter Cancelled",
            StatusCategory.TERMINAL,
            "#dc3545",
            "badge-danger"
    );

    public enum StatusCategory {
        INTAKE("Front-Desk Intake & Triage"),
        CLINICAL("Physician & Nursing Care"),
        FINANCIAL("Billing & Cashier Register"),
        TERMINAL("Archived & Concluded");

        private final String displayTitle;

        StatusCategory(String displayTitle) {
            this.displayTitle = displayTitle;
        }

        public String getDisplayTitle() {
            return displayTitle;
        }
    }

    private final int statusCode;
    private final String label;
    private final StatusCategory category;
    private final String badgeColorHex;
    private final String cssClass;

    PatientStatus(int statusCode, String label, StatusCategory category, String badgeColorHex, String cssClass) {
        this.statusCode = statusCode;
        this.label = label;
        this.category = category;
        this.badgeColorHex = badgeColorHex;
        this.cssClass = cssClass;
    }

    // Static initialization of the State Machine Transition Matrix
    static {
        REGISTERED.allowedTransitions = EnumSet.of(WAITING_FOR_DOCTOR, IN_VISIT, CANCELLED);
        WAITING_FOR_DOCTOR.allowedTransitions = EnumSet.of(IN_VISIT, WAITING_FOR_PAYMENT, CANCELLED);
        IN_VISIT.allowedTransitions = EnumSet.of(WAITING_FOR_PAYMENT, CANCELLED);
        WAITING_FOR_PAYMENT.allowedTransitions = EnumSet.of(PAYMENT_COMPLETED, CANCELLED);
        PAYMENT_COMPLETED.allowedTransitions = EnumSet.of(DISCHARGED);
        DISCHARGED.allowedTransitions = EnumSet.of(REGISTERED); // Readmission
        CANCELLED.allowedTransitions = EnumSet.of(REGISTERED);  // Readmission
    }

    private Set<PatientStatus> allowedTransitions;

    public int getStatusCode() {
        return statusCode;
    }

    public String getLabel() {
        return label;
    }

    public StatusCategory getCategory() {
        return category;
    }

    public String getBadgeColorHex() {
        return badgeColorHex;
    }

    public String getCssClass() {
        return cssClass;
    }

    public boolean isTerminal() {
        return this.category == StatusCategory.TERMINAL;
    }

    public boolean isClinical() {
        return this.category == StatusCategory.CLINICAL;
    }

    public boolean isFinancial() {
        return this.category == StatusCategory.FINANCIAL;
    }

    /**
     * Validates whether a state transition conforms to the hospital clinical governance workflow.
     */
    public boolean canTransitionTo(PatientStatus targetStatus) {
        if (targetStatus == null) {
            return false;
        }
        if (this == targetStatus) {
            return true;
        }
        return allowedTransitions != null && allowedTransitions.contains(targetStatus);
    }

    public Set<PatientStatus> getLegalTransitions() {
        return (allowedTransitions != null)
                ? Collections.unmodifiableSet(allowedTransitions)
                : Collections.emptySet();
    }

    /**
     * Robust lookup by integer status code.
     */
    public static PatientStatus fromCode(int code) {
        for (PatientStatus status : values()) {
            if (status.statusCode == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown patient status code: " + code);
    }

    /**
     * Resilient lookup by label or enum name.
     */
    public static PatientStatus fromLabel(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Patient status input cannot be null or empty.");
        }
        String normalized = input.trim().toUpperCase();
        for (PatientStatus status : values()) {
            if (status.name().equals(normalized) || status.label.equalsIgnoreCase(input.trim())) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown patient status descriptor: " + input);
    }

    @Override
    public String toString() {
        return label;
    }
}