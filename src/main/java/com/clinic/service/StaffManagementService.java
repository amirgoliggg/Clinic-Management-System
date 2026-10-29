package com.clinic.service;

import com.clinic.exceptions.EntityNotFoundException;
import com.clinic.exceptions.InsufficientPermissionException;
import com.clinic.models.Admin;
import com.clinic.models.Staff;
import com.clinic.repository.StaffRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class StaffManagementService {

    private final StaffRepository staffRepository;
    private final AuditService auditService;

    public StaffManagementService(StaffRepository staffRepository, AuditService auditService) {
        this.staffRepository = staffRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Staff registerStaff(Staff staff) {
        Objects.requireNonNull(staff, "اطلاعات پرسنل نمی‌تواند خالی باشد.");

        if (staffRepository.existsByNationalId(staff.getNationalId())) {
            throw new IllegalArgumentException("پرسنلی با کد ملی " + staff.getNationalId() + " قبلاً ثبت شده است.");
        }
        if (staffRepository.existsByUsername(staff.getUsername())) {
            throw new IllegalArgumentException("نام کاربری '" + staff.getUsername() + "' قبلاً استفاده شده است.");
        }
        if (staff.getBaseSalary() != null && staff.getBaseSalary().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("حقوق پایه نمی‌تواند منفی باشد.");
        }

        Staff saved = staffRepository.save(staff);
        auditService.logCurrentActorAction("HR_ADMIN", "STAFF_CREATED", "SUCCESS",
                "ثبت پرسنل جدید: " + saved.getUsername() + " (" + saved.getRole() + ")");
        return saved;
    }

    @Transactional(readOnly = true)
    public Staff getStaffByUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("نام کاربری نمی‌تواند خالی باشد.");
        }
        return staffRepository.findByUsername(username.trim())
                .orElseThrow(() -> EntityNotFoundException.staffNotFound(username.trim()));
    }

    @Transactional(readOnly = true)
    public Staff getStaffByNationalId(String nationalId) {
        if (nationalId == null || nationalId.isBlank()) {
            throw new IllegalArgumentException("کد ملی پرسنل نمی‌تواند خالی باشد.");
        }
        return staffRepository.findByNationalId(nationalId.trim())
                .orElseThrow(() -> EntityNotFoundException.staffNotFound(nationalId.trim()));
    }

    @Transactional(readOnly = true)
    public List<Staff> getAllStaff() {
        return staffRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Staff> getStaffByRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("نقش مورد جستجو نمی‌تواند خالی باشد.");
        }
        String normalizedRole = role.trim().toUpperCase().replace("ROLE_", "");

        // اگر نقش یکی از زیرنقش‌های مدیریتی (AdminRole) باشد، از جدول Admin واکشی شود
        for (Admin.AdminRole adminRole : Admin.AdminRole.values()) {
            if (adminRole.name().equals(normalizedRole)) {
                return staffRepository.findByAdminRole(adminRole);
            }
        }

        return staffRepository.findByRole(normalizedRole);
    }

    @Transactional
    public void toggleStaffStatus(String username, boolean isActive) {
        toggleStaffStatus(null, username, isActive);
    }

    @Transactional
    public void toggleStaffStatus(Authentication actorAuth, String username, boolean isActive) {
        Staff targetStaff = getStaffByUsername(username);

        if (actorAuth != null && actorAuth.isAuthenticated()) {
            String actorUsername = actorAuth.getName();

            // ۱. جلوگیری از تعلیق حساب خودِ کاربر جاری
            if (!isActive && actorUsername.equalsIgnoreCase(targetStaff.getUsername())) {
                throw new IllegalStateException("کاربر نمی‌تواند حساب کاربری فعال خودش را مسدود کند.");
            }

            // ۲. جلوگیری از تغییر وضعیت مدیران توسط HR_ADMIN
            boolean isCallerSuperAdmin = actorAuth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(a -> a.equals("SUPER_ADMIN") || a.equals("ROLE_SUPER_ADMIN"));

            if (targetStaff instanceof Admin && !isCallerSuperAdmin) {
                throw InsufficientPermissionException.adminRequired(actorUsername, "MODIFY_ADMIN_STATUS");
            }
        }

        targetStaff.setActive(isActive);
        staffRepository.save(targetStaff);

        auditService.logCurrentActorAction("HR_ADMIN", "STAFF_STATUS_CHANGED", "SUCCESS",
                "تغییر وضعیت حساب پرسنل '" + targetStaff.getUsername() + "' به: " + (isActive ? "ACTIVE" : "SUSPENDED"));
    }
}