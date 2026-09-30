package models;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "audit_logs")
public class AuditLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 50L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Column(name = "timestamp_str", nullable = false)
    private String timestamp;

    @Column(name = "actor_username", nullable = false)
    private String actorUsername;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(name = "action_name", nullable = false)
    private String action;

    @Column(name = "status_val", nullable = false)
    private String status;

    @Column(name = "previous_hash", length = 64, nullable = false)
    private String previousHash;

    @Column(name = "entry_hash", length = 64, nullable = false)
    private String entryHash;

    @Column(name = "terminal_ip")
    private String terminalOrIp;

    @Column(name = "details", length = 1000)
    private String details;

    public AuditLog() {}

    public AuditLog(long sequenceNumber, String actorUsername, String actorRole, String action,
                    String status, String previousHash, String entryHash, String terminalOrIp, String details) {
        this.sequenceNumber = sequenceNumber;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.actorUsername = actorUsername;
        this.actorRole = actorRole;
        this.action = action;
        this.status = status;
        this.previousHash = previousHash;
        this.entryHash = entryHash;
        this.terminalOrIp = terminalOrIp;
        this.details = details;
    }

    public Long getId() { return id; }
    public long getSequenceNumber() { return sequenceNumber; }
    public String getTimestamp() { return timestamp; }
    public String getActorUsername() { return actorUsername; }
    public String getActorRole() { return actorRole; }
    public String getAction() { return action; }
    public String getStatus() { return status; }
    public String getPreviousHash() { return previousHash; }
    public String getEntryHash() { return entryHash; }
    public String getTerminalOrIp() { return terminalOrIp; }
    public String getDetails() { return details; }
}