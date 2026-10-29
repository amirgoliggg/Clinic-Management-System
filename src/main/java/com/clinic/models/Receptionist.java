package com.clinic.models;

import com.clinic.system.SystemClock;
import jakarta.persistence.*;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

@Entity
@Table(name = "receptionists")
@PrimaryKeyJoinColumn(name = "national_id")
public class Receptionist extends Staff {

    @Serial
    private static final long serialVersionUID = 26L;

    private static final String ROLE_RECEPTIONIST = "RECEPTIONIST";
    private static final String DEFAULT_WORKSTATION_ID = "DESK-01";
    private static final Pattern WORKSTATION_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]{2,20}$");

    public enum DeskLocation {
        MAIN_LOBBY,
        EMERGENCY_ADMISSION,
        OUTPATIENT_RECEPTION,
        PEDIATRIC_INTAKE,
        VIP_CONCIERGE
    }

    @Column(name = "workstation_id", nullable = false, length = 20)
    private String workstationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "desk_location", nullable = false)
    private DeskLocation deskLocation;

    @Transient
    private volatile boolean onDuty = true;

    // ==================== Constructors ====================

    protected Receptionist() {
        super();
    }

    public Receptionist(String nationalId, String fullName, Integer age, String contactNumber,
                        BigDecimal baseSalary, String workstationId, DeskLocation deskLocation) {
        super(nationalId, fullName, age, contactNumber, nationalId,
                ROLE_RECEPTIONIST, baseSalary, SystemClock.now(), EmploymentStatus.ACTIVE);
        this.workstationId = validateWorkstationId(workstationId);
        this.deskLocation = (deskLocation != null) ? deskLocation : DeskLocation.MAIN_LOBBY;
    }

    private Receptionist(String newNationalId, Receptionist source) {
        super(newNationalId, source);
        this.workstationId = source.workstationId;
        this.deskLocation = source.deskLocation;
        this.onDuty = source.onDuty;
    }

    // ==================== Lifecycle & Validation ====================

    @PrePersist
    public void ensureReceptionistDefaults() {
        super.ensureStaffDefaults();
        if (this.workstationId == null || this.workstationId.isBlank()) {
            this.workstationId = DEFAULT_WORKSTATION_ID;
        }
        if (this.deskLocation == null) {
            this.deskLocation = DeskLocation.MAIN_LOBBY;
        }
    }

    @Override
    public Person cloneWithNewId(String newNationalId) {
        return new Receptionist(newNationalId, this);
    }

    private static String validateWorkstationId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Workstation ID cannot be null or empty.");
        }
        String trimmed = id.trim();
        if (!WORKSTATION_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid Workstation ID format (2-20 alphanumeric characters).");
        }
        return trimmed;
    }

    // ==================== Getters & Setters ====================

    public String getWorkstationId() {
        return workstationId;
    }

    public void setWorkstationId(String workstationId) {
        this.workstationId = validateWorkstationId(workstationId);
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
}