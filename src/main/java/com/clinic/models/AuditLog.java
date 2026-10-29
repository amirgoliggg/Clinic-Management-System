package com.clinic.models;

import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Entity
@Table(
        name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_seq", columnList = "sequence_number", unique = true)
        }
)
public class AuditLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 50L;

    public static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sequence_number", nullable = false, unique = true, updatable = false)
    private long sequenceNumber;

    @Column(name = "timestamp_str", nullable = false, updatable = false)
    private String timestamp;

    @Column(name = "actor_username", nullable = false, updatable = false)
    private String actorUsername;

    @Column(name = "actor_role", nullable = false, updatable = false)
    private String actorRole;

    @Column(name = "action_name", nullable = false, updatable = false)
    private String action;

    @Column(name = "status_val", nullable = false, updatable = false)
    private String status;

    @Column(name = "previous_hash", length = 64, nullable = false, updatable = false)
    private String previousHash;

    @Column(name = "entry_hash", length = 64, nullable = false, updatable = false)
    private String entryHash;

    @Column(name = "terminal_ip", nullable = false, updatable = false)
    private String terminalOrIp;

    @Column(name = "details", length = 1000, updatable = false)
    private String details;

    // ==================== Constructors ====================

    protected AuditLog() {
        // Required by JPA
    }

    public AuditLog(long sequenceNumber,
                    String timestamp,
                    String actorUsername,
                    String actorRole,
                    String action,
                    String status,
                    String previousHash,
                    String entryHash,
                    String terminalOrIp,
                    String details) {
        this.sequenceNumber = sequenceNumber;
        this.timestamp = (timestamp != null && !timestamp.isBlank())
                ? timestamp.trim()
                : LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        this.actorUsername = requireNonBlank(actorUsername, "Actor username cannot be blank.");
        this.actorRole = requireNonBlank(actorRole, "Actor role cannot be blank.");
        this.action = requireNonBlank(action, "Action cannot be blank.");
        this.status = requireNonBlank(status, "Status cannot be blank.");
        this.previousHash = requireNonBlank(previousHash, "Previous hash cannot be blank.");
        this.entryHash = requireNonBlank(entryHash, "Entry hash cannot be blank.");
        this.terminalOrIp = (terminalOrIp != null && !terminalOrIp.isBlank()) ? terminalOrIp.trim() : "127.0.0.1";
        this.details = (details != null) ? details.trim() : "";
    }

    public AuditLog(long sequenceNumber,
                    String actorUsername,
                    String actorRole,
                    String action,
                    String status,
                    String previousHash,
                    String entryHash,
                    String terminalOrIp,
                    String details) {
        this(
                sequenceNumber,
                LocalDateTime.now().format(TIMESTAMP_FORMATTER),
                actorUsername,
                actorRole,
                action,
                status,
                previousHash,
                entryHash,
                terminalOrIp,
                details
        );
    }

    private static String requireNonBlank(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value.trim();
    }

    // ==================== Getters ====================

    public Long getId() {
        return id;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getAction() {
        return action;
    }

    public String getStatus() {
        return status;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getEntryHash() {
        return entryHash;
    }

    public String getTerminalOrIp() {
        return terminalOrIp;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditLog auditLog)) return false;
        return sequenceNumber == auditLog.sequenceNumber
                && Objects.equals(entryHash, auditLog.entryHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequenceNumber, entryHash);
    }
}