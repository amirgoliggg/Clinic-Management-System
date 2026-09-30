package models;

import system.SystemClock;
import jakarta.persistence.*;
import java.io.Serial;
import java.math.BigDecimal;

@Entity
@Table(name = "nurses")
public final class Nurse extends Staff {

    @Serial
    private static final long serialVersionUID = 30L;

    @Column(name = "nursing_license_number")
    private String nursingLicenseNumber;

    @Column(name = "department")
    private String department;

    protected Nurse() {}

    public Nurse(String nationalId, String fullName, Integer age, String contactNumber,
                 BigDecimal baseSalary, String nursingLicenseNumber, String department) {
        super(nationalId, fullName, age, contactNumber, nationalId, "NURSE", baseSalary, SystemClock.now(), EmploymentStatus.ACTIVE);
        this.nursingLicenseNumber = nursingLicenseNumber;
        this.department = department != null ? department : "بخش عمومی";
    }

    public String getNursingLicenseNumber() { return nursingLicenseNumber; }
    public String getDepartment() { return department; }
}