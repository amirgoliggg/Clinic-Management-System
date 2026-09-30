package exceptions;

import java.io.Serial;
import java.time.Instant;

/**
 * Enterprise Base Domain Exception for the Clinic Management System.
 * Serves as the authoritative root unchecked exception for all operational,
 * clinical, financial, and security runtime anomalies.
 */
public class ClinicException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 500L;

    public static final String DEFAULT_ERROR_CODE = "CLINIC_GENERAL_ERROR";

    private final String errorCode;
    private final long timestampEpochMilli;

    public ClinicException(String message) {
        this(message, DEFAULT_ERROR_CODE, null);
    }

    public ClinicException(String message, Throwable cause) {
        this(message, DEFAULT_ERROR_CODE, cause);
    }

    public ClinicException(Throwable cause) {
        this(cause != null ? cause.getMessage() : "An unexpected clinical domain error occurred.", DEFAULT_ERROR_CODE, cause);
    }

    public ClinicException(String message, String errorCode) {
        this(message, errorCode, null);
    }

    public ClinicException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = (errorCode != null && !errorCode.isBlank()) ? errorCode.trim() : DEFAULT_ERROR_CODE;
        this.timestampEpochMilli = Instant.now().toEpochMilli();
    }

    public ClinicException(String message, String errorCode, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.errorCode = (errorCode != null && !errorCode.isBlank()) ? errorCode.trim() : DEFAULT_ERROR_CODE;
        this.timestampEpochMilli = Instant.now().toEpochMilli();
    }

    public String getErrorCode() {
        return errorCode;
    }

    public long getTimestampEpochMilli() {
        return timestampEpochMilli;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", errorCode, getClass().getSimpleName(), getLocalizedMessage());
    }
}