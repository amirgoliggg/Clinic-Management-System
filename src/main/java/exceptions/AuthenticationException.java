package exceptions;

import java.io.Serial;

/**
 * Enterprise Authentication Domain Exception.
 * Dispatched across clinical, administrative, and cryptographic authentication boundaries
 * whenever identity verification, session state, or privilege elevation fails.
 *
 * Specifically architected for Desktop GUI (JavaFX) workflows, providing both detailed
 * security audit telemetry for internal logging and sanitized user-facing messages
 * suitable for graphical alert dialogs and countdown timers.
 */
public class AuthenticationException extends ClinicException {

    @Serial
    private static final long serialVersionUID = 501L;

    public static final String ERROR_CODE = "AUTH_FAILED";

    /**
     * Strongly-typed taxonomy of security and authentication failure vectors.
     */
    public enum FailureReason {
        INVALID_CREDENTIALS,
        ACCOUNT_LOCKED,
        ACCOUNT_DISABLED,
        SESSION_EXPIRED,
        INVALID_RECOVERY_KEY,
        SECURITY_VIOLATION,
        PASSWORD_EXPIRED,
        UNKNOWN
    }

    private final String username;
    private final FailureReason reason;
    private final Long lockoutRemainingSeconds;
    private final String userFacingMessage;

    public AuthenticationException(String message) {
        this(message, FailureReason.UNKNOWN, null, null, null, null);
    }

    public AuthenticationException(String message, Throwable cause) {
        this(message, FailureReason.UNKNOWN, null, null, null, cause);
    }

    public AuthenticationException(String message, FailureReason reason) {
        this(message, reason, null, null, null, null);
    }

    public AuthenticationException(String message, FailureReason reason, String username) {
        this(message, reason, username, null, null, null);
    }

    public AuthenticationException(String message, FailureReason reason, String username, Throwable cause) {
        this(message, reason, username, null, null, cause);
    }

    public AuthenticationException(String message, FailureReason reason, String username, Long lockoutRemainingSeconds) {
        this(message, reason, username, lockoutRemainingSeconds, null, null);
    }

    public AuthenticationException(String message, FailureReason reason, String username,
                                   Long lockoutRemainingSeconds, String userFacingMessage) {
        this(message, reason, username, lockoutRemainingSeconds, userFacingMessage, null);
    }

    public AuthenticationException(String message, FailureReason reason, String username,
                                   Long lockoutRemainingSeconds, String userFacingMessage, Throwable cause) {
        super(formatSecurityAuditMessage(message, username, reason), ERROR_CODE, cause);

        this.reason = (reason != null) ? reason : FailureReason.UNKNOWN;
        this.username = normalizeUsername(username);
        this.lockoutRemainingSeconds = (lockoutRemainingSeconds != null && lockoutRemainingSeconds > 0)
                ? lockoutRemainingSeconds
                : (lockoutRemainingSeconds != null && lockoutRemainingSeconds == 0 ? 0L : null);
        this.userFacingMessage = (userFacingMessage != null && !userFacingMessage.isBlank())
                ? userFacingMessage.trim()
                : resolveDefaultUserMessage(this.reason, this.lockoutRemainingSeconds);
    }

    private static String normalizeUsername(String username) {
        return (username != null && !username.isBlank()) ? username.trim() : "ANONYMOUS";
    }

    private static String formatSecurityAuditMessage(String message, String username, FailureReason reason) {
        String cleanMsg = (message != null && !message.isBlank()) ? message.trim() : "Authentication challenge rejected.";
        String cleanUser = normalizeUsername(username);
        String reasonStr = (reason != null) ? reason.name() : FailureReason.UNKNOWN.name();
        return String.format("[SECURITY AUTH] User: '%s' | Reason: %s | Details: %s", cleanUser, reasonStr, cleanMsg);
    }

    private static String resolveDefaultUserMessage(FailureReason reason, Long lockoutSeconds) {
        if (reason == null) {
            return "Authentication failed. Please verify your credentials.";
        }
        return switch (reason) {
            case INVALID_CREDENTIALS -> "Invalid username or password. Please try again.";
            case ACCOUNT_LOCKED -> {
                if (lockoutSeconds != null && lockoutSeconds > 0) {
                    yield String.format("Account temporarily locked. Please try again in %s.", formatSeconds(lockoutSeconds));
                }
                yield "Account locked due to excessive failed attempts. Please contact an administrator.";
            }
            case ACCOUNT_DISABLED -> "This account has been disabled. Please contact the clinic administrator.";
            case SESSION_EXPIRED -> "Your session has expired for security reasons. Please log in again.";
            case INVALID_RECOVERY_KEY -> "The emergency break-glass recovery key is invalid or unassigned.";
            case SECURITY_VIOLATION -> "Security policy violation detected. Access request has been denied.";
            case PASSWORD_EXPIRED -> "Your password has expired. A password reset is required.";
            case UNKNOWN -> "Authentication failed. Please verify your credentials.";
        };
    }

    private static String formatSeconds(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    // =========================================================================
    // ENTERPRISE FACTORY CONSTRUCTORS (OPTIMIZED FOR SERVICES & GUI BINDING)
    // =========================================================================

    public static AuthenticationException badCredentials(String username) {
        return new AuthenticationException(
                "Invalid identity secret or password hash mismatch.",
                FailureReason.INVALID_CREDENTIALS,
                username,
                null,
                "Invalid username or password. Please check your credentials and try again."
        );
    }

    public static AuthenticationException badCredentials(String username, Throwable cause) {
        return new AuthenticationException(
                "Invalid identity secret or password hash mismatch.",
                FailureReason.INVALID_CREDENTIALS,
                username,
                null,
                "Invalid username or password. Please check your credentials and try again.",
                cause
        );
    }

    public static AuthenticationException accountLocked(String username, long remainingSeconds) {
        long safeSeconds = Math.max(0, remainingSeconds);
        String auditMsg = String.format("Login attempts exceeded threshold. Security cooldown active: %d seconds remaining.", safeSeconds);
        String userMsg = String.format("Account is temporarily locked. Cooldown remaining: %s.", formatSeconds(safeSeconds));

        return new AuthenticationException(
                auditMsg,
                FailureReason.ACCOUNT_LOCKED,
                username,
                safeSeconds,
                userMsg
        );
    }

    public static AuthenticationException accountDisabled(String username) {
        return new AuthenticationException(
                "Administrative suspension flag is active for this account.",
                FailureReason.ACCOUNT_DISABLED,
                username,
                null,
                "This account is suspended. Please contact clinic management."
        );
    }

    public static AuthenticationException invalidRecoveryKey(String username) {
        return new AuthenticationException(
                "Emergency access denied: Break-glass recovery token signature failed validation.",
                FailureReason.INVALID_RECOVERY_KEY,
                username,
                null,
                "Invalid emergency recovery key. Emergency authorization rejected."
        );
    }

    public static AuthenticationException sessionExpired(String username) {
        return new AuthenticationException(
                "Inactivity timeout elapsed or token invalidated.",
                FailureReason.SESSION_EXPIRED,
                username,
                null,
                "Your security session has expired. Please sign in again to continue."
        );
    }

    public static AuthenticationException securityViolation(String username, String detail) {
        String safeDetail = (detail != null && !detail.isBlank()) ? detail.trim() : "Cryptographic boundary violation.";
        return new AuthenticationException(
                safeDetail,
                FailureReason.SECURITY_VIOLATION,
                username,
                null,
                "Access blocked due to a security policy violation."
        );
    }

    // =========================================================================
    // GUI DATA-BINDING & AUDIT GETTERS
    // =========================================================================

    public String getUsername() {
        return username;
    }

    public FailureReason getReason() {
        return reason;
    }

    public Long getLockoutRemainingSeconds() {
        return lockoutRemainingSeconds;
    }

    public boolean hasLockoutCooldown() {
        return lockoutRemainingSeconds != null && lockoutRemainingSeconds > 0;
    }

    /**
     * Formats the remaining lockout cooldown in MM:SS or HH:MM:SS format
     * directly bindable to JavaFX UI countdown labels.
     */
    public String getFormattedRemainingCooldown() {
        if (lockoutRemainingSeconds == null || lockoutRemainingSeconds <= 0) {
            return "00:00";
        }
        return formatSeconds(lockoutRemainingSeconds);
    }

    /**
     * Retrieves the clean, sanitized message suitable for direct binding to
     * GUI Alert Dialogs, Toast notifications, or Login Error labels without
     * leaking internal stack traces or database structures.
     */
    public String getUserFacingMessage() {
        return userFacingMessage;
    }
}