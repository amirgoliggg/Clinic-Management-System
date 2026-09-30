package models;

import system.SystemClock;
import jakarta.persistence.*;
import java.io.Serial;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

@Entity
@Table(name = "receptionists")
public final class Receptionist extends Staff {

    @Serial
    private static final long serialVersionUID = 26L;
    private static final Pattern WORKSTATION_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]{2,20}$");

    public enum DeskLocation {
        MAIN_LOBBY, EMERGENCY_ADMISSION, OUTPATIENT_RECEPTION, PEDIATRIC_INTAKE, VIP_CONCIERGE;
    }

    @Column(name = "workstation_id", nullable = false)
    private String workstationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "desk_location", nullable = false)
    private DeskLocation deskLocation;

    @Transient
    private volatile boolean onDuty = true;

    protected Receptionist() {}

    public Receptionist(String nationalId, String fullName, Integer age, String contactNumber,
                        BigDecimal baseSalary, String workstationId, DeskLocation deskLocation) {
        super(nationalId, fullName, age, contactNumber, nationalId, "RECEPTIONIST", baseSalary, SystemClock.now(), EmploymentStatus.ACTIVE);
        this.workstationId = validateWorkstationId(workstationId);
        this.deskLocation = deskLocation;
    }

    private Receptionist(String newNationalId, Receptionist source) {
        super(newNationalId, source);
        this.workstationId = source.workstationId;
        this.deskLocation = source.deskLocation;
    }

    @Override
    public Person cloneWithNewId(String newNationalId) {
        return new Receptionist(newNationalId, this);
    }

    private String validateWorkstationId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Workstation ID cannot be null or empty.");
        }
        return id.trim();
    }

    public String getWorkstationId() { return workstationId; }
    public DeskLocation getDeskLocation() { return deskLocation; }
    public void setDeskLocation(DeskLocation deskLocation) { this.deskLocation = Objects.requireNonNull(deskLocation); }
    public boolean isOnDuty() { return onDuty; }
    public void setOnDuty(boolean onDuty) { this.onDuty = onDuty; }
}