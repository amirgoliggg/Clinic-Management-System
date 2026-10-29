package com.clinic.service;

import com.clinic.dto.AuthDTOs.RegisterRequest;
import com.clinic.dto.AuthDTOs.RoleDefinitionDTO;
import com.clinic.dto.AuthDTOs.UserProfileResponse;
import com.clinic.exceptions.EntityNotFoundException;
import com.clinic.models.*;
import com.clinic.repository.StaffRepository;
import com.clinic.system.SystemClock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;

    private static final Map<String, RoleDefinitionDTO> BUILT_IN_ROLES = new LinkedHashMap<>();
    private final Map<String, RoleDefinitionDTO> customRoles = new ConcurrentHashMap<>();

    static {
        BUILT_IN_ROLES.put("DOCTOR", new RoleDefinitionDTO("DOCTOR", "پزشک معالج", "DOCTOR", true));
        BUILT_IN_ROLES.put("NURSE", new RoleDefinitionDTO("NURSE", "پرستار بالینی و تریاژ", "NURSE", true));
        BUILT_IN_ROLES.put("CASHIER", new RoleDefinitionDTO("CASHIER", "مسئول صندوق", "CASHIER", true));
        BUILT_IN_ROLES.put("RECEPTIONIST", new RoleDefinitionDTO("RECEPTIONIST", "مسئول پذیرش", "RECEPTIONIST", true));
        BUILT_IN_ROLES.put("FINANCIAL_ADMIN", new RoleDefinitionDTO("FINANCIAL_ADMIN", "مدیر مالی", "FINANCIAL_ADMIN", true));
        BUILT_IN_ROLES.put("CLINICAL_ADMIN", new RoleDefinitionDTO("CLINICAL_ADMIN", "مدیر بالینی", "DOCTOR", true));
        BUILT_IN_ROLES.put("HR_ADMIN", new RoleDefinitionDTO("HR_ADMIN", "مدیر منابع انسانی", "HR_ADMIN", true));
        BUILT_IN_ROLES.put("AUDITOR", new RoleDefinitionDTO("AUDITOR", "بازرس و حسابرس امنیتی", "AUDITOR", true));
        BUILT_IN_ROLES.put("SUPER_ADMIN", new RoleDefinitionDTO("SUPER_ADMIN", "مدیر ارشد سامانه", "SUPER_ADMIN", true));
    }

    public AuthService(StaffRepository staffRepository, PasswordEncoder passwordEncoder) {
        this.staffRepository = staffRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<RoleDefinitionDTO> getAllAvailableRoles() {
        List<RoleDefinitionDTO> all = new ArrayList<>(BUILT_IN_ROLES.values());
        all.addAll(customRoles.values());
        return all;
    }

    public RoleDefinitionDTO createCustomRole(RoleDefinitionDTO dto) {
        String code = dto.roleCode().trim().toUpperCase().replace("ROLE_", "");
        if (BUILT_IN_ROLES.containsKey(code) || customRoles.containsKey(code)) {
            throw new IllegalArgumentException("نقش سازمانی '" + code + "' از قبل در سامانه وجود دارد.");
        }
        RoleDefinitionDTO created = new RoleDefinitionDTO(
                code,
                dto.displayName().trim(),
                dto.baseClearance().trim().toUpperCase(),
                false
        );
        customRoles.put(code, created);
        return created;
    }

    public String resolveBaseClearance(String roleCode) {
        if (roleCode == null) return "RECEPTIONIST";
        String clean = roleCode.trim().toUpperCase().replace("ROLE_", "");
        if (BUILT_IN_ROLES.containsKey(clean)) {
            return clean;
        }
        RoleDefinitionDTO custom = customRoles.get(clean);
        return custom != null ? custom.baseClearance() : clean;
    }

    @Transactional
    public Staff registerStaffMember(RegisterRequest req) {
        if (staffRepository.existsByUsername(req.username())) {
            throw new IllegalArgumentException("نام کاربری '" + req.username() + "' قبلاً ثبت شده است.");
        }
        if (staffRepository.existsByNationalId(req.nationalId())) {
            throw new IllegalArgumentException("کد ملی '" + req.nationalId() + "' در سیستم پرسنلی موجود است.");
        }

        String encodedPwd = passwordEncoder.encode(req.password());
        BigDecimal salary = req.baseSalary() != null ? req.baseSalary() : new BigDecimal("15000.00");
        String requestedRole = (req.role() != null) ? req.role().toUpperCase().replace("ROLE_", "").trim() : "STAFF";
        String effectiveClearance = resolveBaseClearance(requestedRole);

        Staff newStaff;
        switch (effectiveClearance) {
            case "SUPER_ADMIN", "ADMIN" -> {
                newStaff = new Admin(
                        req.nationalId(), req.fullName(), req.age(), req.contactNumber(),
                        salary, req.username(), encodedPwd, Admin.AdminRole.SUPER_ADMIN
                );
            }
            case "FINANCIAL_ADMIN" -> {
                newStaff = new Admin(
                        req.nationalId(), req.fullName(), req.age(), req.contactNumber(),
                        salary, req.username(), encodedPwd, Admin.AdminRole.FINANCIAL_ADMIN
                );
            }
            case "CLINICAL_ADMIN" -> {
                newStaff = new Admin(
                        req.nationalId(), req.fullName(), req.age(), req.contactNumber(),
                        salary, req.username(), encodedPwd, Admin.AdminRole.CLINICAL_ADMIN
                );
            }
            case "HR_ADMIN" -> {
                newStaff = new Admin(
                        req.nationalId(), req.fullName(), req.age(), req.contactNumber(),
                        salary, req.username(), encodedPwd, Admin.AdminRole.HR_ADMIN
                );
            }
            case "AUDITOR" -> {
                newStaff = new Admin(
                        req.nationalId(), req.fullName(), req.age(), req.contactNumber(),
                        salary, req.username(), encodedPwd, Admin.AdminRole.AUDITOR
                );
            }
            case "DOCTOR" -> {
                Doctor.Specialization spec = resolveSpecialization(req.specialization());
                String license = (req.medicalLicenseNumber() != null && !req.medicalLicenseNumber().isBlank())
                        ? req.medicalLicenseNumber().trim()
                        : "DOC-" + req.nationalId();

                Doctor doctor = new Doctor(
                        req.nationalId(), req.fullName(), req.age(), req.contactNumber(),
                        salary, license, spec, new BigDecimal("150.00"), 30
                );
                doctor.setUsername(req.username());
                doctor.setEncodedPassword(encodedPwd);
                doctor.setRole(requestedRole);
                newStaff = doctor;
            }
            case "CASHIER" -> {
                BigDecimal maxLimit = req.maxTransactionLimit() != null
                        ? req.maxTransactionLimit()
                        : new BigDecimal("50000.00");

                Cashier cashier = new Cashier(
                        req.nationalId(), req.fullName(), req.age(), req.contactNumber(),
                        salary, maxLimit
                );
                cashier.setUsername(req.username());
                cashier.setEncodedPassword(encodedPwd);
                cashier.setRole(requestedRole);
                newStaff = cashier;
            }
            case "RECEPTIONIST" -> {
                Receptionist receptionist = new Receptionist(
                        req.nationalId(), req.fullName(), req.age(), req.contactNumber(),
                        salary, "DESK-01", Receptionist.DeskLocation.MAIN_LOBBY
                );
                receptionist.setUsername(req.username());
                receptionist.setEncodedPassword(encodedPwd);
                receptionist.setRole(requestedRole);
                newStaff = receptionist;
            }
            case "NURSE" -> {
                Nurse nurse = new Nurse(
                        req.nationalId(), req.fullName(), req.age(), req.contactNumber(),
                        salary, "NRS-" + req.nationalId(), "بخش عمومی",
                        Nurse.ShiftType.ROTATING, true
                );
                nurse.setUsername(req.username());
                nurse.setEncodedPassword(encodedPwd);
                nurse.setRole(requestedRole);
                newStaff = nurse;
            }
            default -> {
                if (customRoles.containsKey(requestedRole)) {
                    newStaff = new Staff(
                            req.nationalId(), req.fullName(), req.age(), req.contactNumber(),
                            req.username(), encodedPwd, requestedRole, salary,
                            SystemClock.now(), Staff.EmploymentStatus.ACTIVE
                    );
                } else {
                    throw new IllegalArgumentException("نقش پرسنلی نامعتبر است: " + req.role() + ". ابتدا نقش را در بخش تعریف نقش بسازید.");
                }
            }
        }

        return staffRepository.save(newStaff);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(String username) {
        Staff staff = staffRepository.findByUsername(username)
                .orElseThrow(() -> EntityNotFoundException.staffNotFound(username));

        String role = staff.getRole();
        if (staff instanceof Admin a) {
            role = a.getAdminRole().name();
        }

        return new UserProfileResponse(staff.getUsername(), staff.getFullName(), role, staff.getNationalId());
    }

    private Doctor.Specialization resolveSpecialization(String specName) {
        if (specName == null || specName.isBlank()) {
            return Doctor.Specialization.GENERAL_PRACTICE;
        }
        for (Doctor.Specialization s : Doctor.Specialization.values()) {
            if (s.name().equalsIgnoreCase(specName.trim())) {
                return s;
            }
        }
        return Doctor.Specialization.GENERAL_PRACTICE;
    }
}