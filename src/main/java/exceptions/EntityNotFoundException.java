package exceptions;

import java.io.Serial;

/**
 * Enterprise Entity Not Found Domain Exception.
 * Thrown whenever a requested clinical, administrative, or financial entity
 * cannot be resolved within repository or in-memory persistence boundaries.
 */
public class EntityNotFoundException extends ClinicException {

    @Serial
    private static final long serialVersionUID = 502L;

    public static final String ERROR_CODE = "ENTITY_NOT_FOUND";

    private final String entityType;
    private final String searchField;
    private final String searchValue;

    public EntityNotFoundException(String message) {
        super(message, ERROR_CODE);
        this.entityType = "UnknownEntity";
        this.searchField = "unknown";
        this.searchValue = "N/A";
    }

    public EntityNotFoundException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
        this.entityType = "UnknownEntity";
        this.searchField = "unknown";
        this.searchValue = "N/A";
    }

    public EntityNotFoundException(String entityType, String searchField, Object searchValue) {
        this(entityType, searchField, searchValue, null);
    }

    public EntityNotFoundException(String entityType, String searchField, Object searchValue, Throwable cause) {
        super(formatMessage(entityType, searchField, searchValue), ERROR_CODE, cause);
        this.entityType = (entityType != null && !entityType.isBlank()) ? entityType.trim() : "Entity";
        this.searchField = (searchField != null && !searchField.isBlank()) ? searchField.trim() : "identifier";
        this.searchValue = (searchValue != null) ? String.valueOf(searchValue).trim() : "null";
    }

    private static String formatMessage(String entityType, String searchField, Object searchValue) {
        String cleanType = (entityType != null && !entityType.isBlank()) ? entityType.trim() : "Entity";
        String cleanField = (searchField != null && !searchField.isBlank()) ? searchField.trim() : "identifier";
        String cleanVal = (searchValue != null) ? String.valueOf(searchValue).trim() : "null";
        return String.format("%s not found with %s: [%s]", cleanType, cleanField, cleanVal);
    }

    // =========================================================================
    // ENTERPRISE FACTORY CONSTRUCTORS
    // =========================================================================

    public static EntityNotFoundException forEntity(String entityType, Object identifier) {
        return new EntityNotFoundException(entityType, "id", identifier);
    }

    public static EntityNotFoundException forEntity(Class<?> entityClass, Object identifier) {
        String typeName = (entityClass != null) ? entityClass.getSimpleName() : "Entity";
        return new EntityNotFoundException(typeName, "id", identifier);
    }

    public static EntityNotFoundException forField(Class<?> entityClass, String fieldName, Object fieldValue) {
        String typeName = (entityClass != null) ? entityClass.getSimpleName() : "Entity";
        return new EntityNotFoundException(typeName, fieldName, fieldValue);
    }

    public static EntityNotFoundException patientNotFound(String nationalId) {
        return new EntityNotFoundException("Patient", "nationalId", nationalId);
    }

    public static EntityNotFoundException staffNotFound(String nationalId) {
        return new EntityNotFoundException("Staff", "nationalId", nationalId);
    }

    public static EntityNotFoundException doctorNotFound(String licenseOrId) {
        return new EntityNotFoundException("Doctor", "medicalLicenseOrId", licenseOrId);
    }

    public static EntityNotFoundException adminNotFound(String username) {
        return new EntityNotFoundException("Admin", "username", username);
    }

    public static EntityNotFoundException medicalRecordNotFound(String recordId) {
        return new EntityNotFoundException("MedicalRecord", "recordId", recordId);
    }

    public static EntityNotFoundException receiptNotFound(String receiptId) {
        return new EntityNotFoundException("Receipt", "receiptId", receiptId);
    }

    public static EntityNotFoundException shiftNotFound(String shiftId) {
        return new EntityNotFoundException("Shift", "shiftId", shiftId);
    }

    // =========================================================================
    // AUDIT GETTERS
    // =========================================================================

    public String getEntityType() {
        return entityType;
    }

    public String getSearchField() {
        return searchField;
    }

    public String getSearchValue() {
        return searchValue;
    }
}