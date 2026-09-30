package service;

import dto.AuthDTOs.RegisterRequest;
import dto.AuthDTOs.UserProfileResponse;
import exceptions.EntityNotFoundException;
import models.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.StaffRepository;

import java.math.BigDecimal;

@Service
public class AuthService {

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(StaffRepository staffRepository, PasswordEncoder passwordEncoder) {
        this.staffRepository = staffRepository;
        this.passwordEncoder = passwordEncoder;
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
        String roleStr = req.role().toUpperCase().replace("ROLE_", "");

        Staff newStaff;
        switch (roleStr) {
            case "SUPER_ADMIN", "ADMIN" -> {
                Admin admin = new Admin(
                        req.nationalId(), req.fullName(), req.age(), req.contactNumber(),
                        salary, req.username(), encodedPwd, Admin.AdminRole.SUPER_ADMIN
                );
                admin.setEncodedPassword(encodedPwd);
                newStaff = admin;
            }
            case "DOCTOR" -> {
                Doctor.Specialization spec = resolveSpecialization(req.specialization());
                String license = (req.medicalLicenseNumber() != null && !req.medicalLicenseNumber().isBlank())
                        ? req.medicalLicenseNumber()
                        : "DOC-" + req.nationalId();

                Doctor doctor = new Doctor(
                        req.nationalId(), req.fullName(), req.age(), req.contactNumber(),
                        salary, license, spec, new BigDecimal("150.00"), 30
                );
                doctor.setUsername(req.username());
                doctor.setEncodedPassword(encodedPwd);
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
                newStaff = cashier;
            }
            case "RECEPTIONIST" -> {
                models.Receptionist.DeskLocation deskLocation = models.Receptionist.DeskLocation.values().length > 0
                        ? models.Receptionist.DeskLocation.values()[0]
                        : null;

                Receptionist receptionist = new Receptionist(
                        req.nationalId(), req.fullName(), req.age(), req.contactNumber(),
                        salary, "DESK-A1", deskLocation
                );
                receptionist.setUsername(req.username());
                receptionist.setEncodedPassword(encodedPwd);
                newStaff = receptionist;
            }
            case "NURSE" -> {
                Nurse nurse = new Nurse(
                        req.nationalId(), req.fullName(), req.age(), req.contactNumber(),
                        salary, "NUR-" + req.nationalId(), "بخش اورژانس"
                );
                nurse.setUsername(req.username());
                nurse.setEncodedPassword(encodedPwd);
                newStaff = nurse;
            }
            default -> throw new IllegalArgumentException("نقش نامعتبر است: " + req.role());
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