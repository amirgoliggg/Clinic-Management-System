package exceptions;

import java.io.Serial;

/**
 * Enterprise Insufficient Permission Domain Exception.
 * Dispatched whenever an authenticated operator or clinical actor attempts
 * an operation that exceeds their designated role, security clearance, or clinical scope.
 *
 * Designed for seamless integration with Graphical User Interfaces (GUI/JavaFX),
 * offering both detailed internal audit telemetry and sanitized user-facing alerts.
 */
public class InsufficientPermissionException extends ClinicException {

    @Serial
    private static final long serialVersionUID = 504L;

    public static final String ERROR_CODE = "ACCESS_DENIED";

    private final String actorUsername;
    private final String requiredRole;
    private final String attemptedAction;
    private final String resourceTarget;
    private final String userFacingMessage;

    public InsufficientPermissionException(String message) {
        super(message, ERROR_CODE);
        this.actorUsername = "ANONYMOUS";
        this.requiredRole = "NONE";
        this.attemptedAction = "UNSPECIFIED";
        this.resourceTarget = "GLOBAL";
        this.userFacingMessage = "Access denied: You do not have permission to perform this action.";
    }

    public InsufficientPermissionException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
        this.actorUsername = "ANONYMOUS";
        this.requiredRole = "NONE";
        this.attemptedAction = "UNSPECIFIED";
        this.resourceTarget = "GLOBAL";
        this.userFacingMessage = "Access denied: You do not have permission to perform this action.";
    }

    public InsufficientPermissionException(String actorUsername, String requiredRole,
                                           String attemptedAction, String resourceTarget,
                                           String detailedReason, String userFacingMessage) {
        this(actorUsername, requiredRole, attemptedAction, resourceTarget, detailedReason, userFacingMessage, null);
    }

    public InsufficientPermissionException(String actorUsername, String requiredRole,
                                           String attemptedAction, String resourceTarget,
                                           String detailedReason, String userFacingMessage,
                                           Throwable cause) {
        super(formatSecurityAuditMessage(actorUsername, requiredRole, attemptedAction, resourceTarget, detailedReason),
                ERROR_CODE, cause);

        this.actorUsername = normalize(actorUsername, "ANONYMOUS");
        this.requiredRole = normalize(requiredRole, "UNRESTRICTED");
        this.attemptedAction = normalize(attemptedAction, "UNSPECIFIED");
        this.resourceTarget = normalize(resourceTarget, "GLOBAL");
        this.userFacingMessage = (userFacingMessage != null && !userFacingMessage.isBlank())
                ? userFacingMessage.trim()
                : "Permission denied: Operation not authorized for your role.";
    }

    private static String normalize(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value.trim() : fallback;
    }

    private static String formatSecurityAuditMessage(String actor, String role, String action, String target, String reason) {
        String cleanActor = normalize(actor, "ANONYMOUS");
        String cleanRole = normalize(role, "UNRESTRICTED");
        String cleanAction = normalize(action, "UNSPECIFIED");
        String cleanTarget = normalize(target, "GLOBAL");
        String cleanReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Privilege verification failed.";

        return String.format("[SECURITY ALERT] Actor '%s' attempted unauthorized action '%s' on resource '%s'. Required Authority: '%s'. Reason: %s",
                cleanActor, cleanAction, cleanTarget, cleanRole, cleanReason);
    }

    // =========================================================================
    // ENTERPRISE FACTORY CONSTRUCTORS (OPTIMIZED FOR SERVICES & GUI WORKFLOWS)
    // =========================================================================

    public static InsufficientPermissionException adminRequired(String username, String attemptedAction) {
        return new InsufficientPermissionException(
                username,
                "ADMINISTRATOR",
                attemptedAction,
                "ADMIN_SECURITY_REALM",
                "Restricted administrative operation requires elevated administrator privileges.",
                "Access Denied: This operation requires Administrator rights."
        );
    }

    public static InsufficientPermissionException clinicalRoleRequired(String username, String attemptedAction) {
        return new InsufficientPermissionException(
                username,
                "LICENSED_CLINICIAN",
                attemptedAction,
                "PATIENT_MEDICAL_DATA",
                "Diagnostic and medical consultation records can only be authored by certified medical doctors.",
                "Access Denied: Only certified physicians can perform this medical action."
        );
    }

    public static InsufficientPermissionException refundApprovalRequired(String username, String receiptId) {
        return new InsufficientPermissionException(
                username,
                "FINANCIAL_SUPERVISOR",
                "EXECUTE_REFUND",
                "RECEIPT#" + (receiptId != null ? receiptId.trim() : "UNKNOWN"),
                "Cashier initiated a transaction reversal without documented supervisor authorization.",
                "Refund Denied: Financial supervisor approval is required to reverse transactions."
        );
    }

    public static InsufficientPermissionException triageOnlyForNurses(String username) {
        return new InsufficientPermissionException(
                username,
                "NURSE",
                "SUBMIT_TRIAGE_VITALS",
                "TRIAGE_QUEUE",
                "Patient physiological triage intake must be conducted exclusively by registered nursing staff.",
                "Access Denied: Only registered nurses are permitted to record clinical triage vitals."
        );
    }

    public static InsufficientPermissionException accessDenied(String username, String resourceName) {
        return new InsufficientPermissionException(
                username,
                "AUTHORIZED_PERSONNEL",
                "VIEW_RESOURCE",
                resourceName,
                "General role authorization rejection across sensitive boundary.",
                "You do not possess the necessary access credentials to view this resource."
        );
    }

    // =========================================================================
    // GUI DATA-BINDING & AUDIT GETTERS
    // =========================================================================

    public String getActorUsername() {
        return actorUsername;
    }

    public String getRequiredRole() {
        return requiredRole;
    }

    public String getAttemptedAction() {
        return attemptedAction;
    }

    public String getResourceTarget() {
        return resourceTarget;
    }

    /**
     * Retrieves the clean, sanitized message suitable for direct binding to
     * GUI Alert Dialogs, banners, or popups without leaking internal architecture details.
     */
    public String getUserFacingMessage() {
        return userFacingMessage;
    }
}