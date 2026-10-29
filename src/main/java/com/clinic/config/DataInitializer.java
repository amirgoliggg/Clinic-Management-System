package com.clinic.config;

import com.clinic.models.Admin;
import com.clinic.models.Cashier;
import com.clinic.models.Doctor;
import com.clinic.models.Nurse;
import com.clinic.models.Receptionist;
import com.clinic.models.Staff;
import com.clinic.repository.StaffRepository;
import com.clinic.service.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataInitializer implements CommandLineRunner {

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Value("${clinic.security.seed-demo-users:${clinic.security.seed-default-staff:true}}")
    private boolean seedDefaultStaff;

    @Value("${clinic.security.reset-existing-passwords:false}")
    private boolean resetExistingPasswords;

    @Value("${clinic.security.bootstrap-admin-password:Admin@123}")
    private String bootstrapAdminPassword;

    public DataInitializer(StaffRepository staffRepository,
                           PasswordEncoder passwordEncoder,
                           AuditService auditService) {
        this.staffRepository = staffRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Override
    public void run(String... args) {
        AuditService.AuditChainState chainState = auditService.diagnoseChainState();
        if (chainState == AuditService.AuditChainState.RECOVERY_PENDING_COMMITTED
                || chainState == AuditService.AuditChainState.RECOVERY_PENDING_ROLLED_BACK) {
            auditService.recoverPendingAnchor();
        }

        if (auditService.getAuditTrail().isEmpty()) {
            auditService.logAction("SYSTEM", "CORE", "GENESIS_BOOTSTRAP", "SUCCESS", "127.0.0.1",
                    "ایجاد بلوک جنسیس و فعال‌سازی زنجیره حسابرسی HMAC-SHA256 سامانه درمانگاه");
        }
        // ادامه کدهای قبلی DataInitializer ...

        // ایجاد حساب مدیر ارشد پایه سامانه (رمز از متغیر محیطی یا مقدار پیش‌فرض امن خوانده می‌شود)
        ensureAdmin("admin", bootstrapAdminPassword, "0000000001", "مدیر ارشد سامانه", Admin.AdminRole.SUPER_ADMIN);

        if (!seedDefaultStaff) {
            return;
        }

        // حساب‌های رسمی کادر درمان و مالی (مورد استفاده در محیط اولیه و تست‌های یکپارچه)
        ensureDoctor("doctor", "Doctor@123", "0000000002", "دکتر علی رضایی", "MED-88421", Doctor.Specialization.GENERAL_PRACTICE);
        ensureCashier("cashier", "Cashier@123", "0000000003", "مسئول صندوق");
        ensureNurse("nurse", "Nurse@123", "0000000004", "مریم سلیمانی");
        ensureReceptionist("receptionist", "Reception@123", "0000000005", "سارا محمدی");
        ensureAdmin("finance", "Finance@123", "0000000006", "مدیر مالی و نظارت", Admin.AdminRole.FINANCIAL_ADMIN);
    }

    private void ensureAdmin(String username, String rawPassword, String nationalId, String fullName, Admin.AdminRole adminRole) {
        Staff existing = staffRepository.findByUsername(username).orElse(null);
        if (existing == null) {
            String encoded = passwordEncoder.encode(rawPassword);
            Admin admin = new Admin(nationalId, fullName, 35, "09120000001",
                    new BigDecimal("30000.00"), username, encoded, adminRole);
            admin.setActive(true);
            staffRepository.save(admin);
        } else if (resetExistingPasswords) {
            existing.setEncodedPassword(passwordEncoder.encode(rawPassword));
            existing.setActive(true);
            if (existing instanceof Admin a) {
                a.setAdminRole(adminRole);
                a.setAccountLocked(false);
            }
            staffRepository.save(existing);
        }
    }

    private void ensureDoctor(String username, String rawPassword, String nationalId, String fullName,
                              String license, Doctor.Specialization spec) {
        Staff existing = staffRepository.findByUsername(username).orElse(null);
        if (existing == null) {
            String encoded = passwordEncoder.encode(rawPassword);
            Doctor doc = new Doctor(nationalId, fullName, 42, "09120000002",
                    new BigDecimal("25000.00"), license, spec, new BigDecimal("150.00"), 50);
            doc.setUsername(username);
            doc.setEncodedPassword(encoded);
            doc.setActive(true);
            staffRepository.save(doc);
        } else if (resetExistingPasswords) {
            existing.setEncodedPassword(passwordEncoder.encode(rawPassword));
            existing.setActive(true);
            staffRepository.save(existing);
        }
    }

    private void ensureCashier(String username, String rawPassword, String nationalId, String fullName) {
        Staff existing = staffRepository.findByUsername(username).orElse(null);
        if (existing == null) {
            String encoded = passwordEncoder.encode(rawPassword);
            Cashier cashier = new Cashier(nationalId, fullName, 29, "09120000003",
                    new BigDecimal("14000.00"), new BigDecimal("100000.00"));
            cashier.setUsername(username);
            cashier.setEncodedPassword(encoded);
            cashier.setActive(true);
            staffRepository.save(cashier);
        } else if (resetExistingPasswords) {
            existing.setEncodedPassword(passwordEncoder.encode(rawPassword));
            existing.setActive(true);
            staffRepository.save(existing);
        }
    }

    private void ensureReceptionist(String username, String rawPassword, String nationalId, String fullName) {
        Staff existing = staffRepository.findByUsername(username).orElse(null);
        if (existing == null) {
            String encoded = passwordEncoder.encode(rawPassword);
            Receptionist rec = new Receptionist(nationalId, fullName, 26, "09120000004",
                    new BigDecimal("12000.00"), "DESK-01", Receptionist.DeskLocation.MAIN_LOBBY);
            rec.setUsername(username);
            rec.setEncodedPassword(encoded);
            rec.setActive(true);
            staffRepository.save(rec);
        } else if (resetExistingPasswords) {
            existing.setEncodedPassword(passwordEncoder.encode(rawPassword));
            existing.setActive(true);
            staffRepository.save(existing);
        }
    }

    private void ensureNurse(String username, String rawPassword, String nationalId, String fullName) {
        Staff existing = staffRepository.findByUsername(username).orElse(null);
        if (existing == null) {
            String encoded = passwordEncoder.encode(rawPassword);
            Nurse nurse = new Nurse(nationalId, fullName, 30, "09120000005",
                    new BigDecimal("13000.00"), "NRS-" + nationalId, "بخش تریاژ و اورژانس");
            nurse.setUsername(username);
            nurse.setEncodedPassword(encoded);
            nurse.setActive(true);
            staffRepository.save(nurse);
        } else if (resetExistingPasswords) {
            existing.setEncodedPassword(passwordEncoder.encode(rawPassword));
            existing.setActive(true);
            staffRepository.save(existing);
        }
    }
}