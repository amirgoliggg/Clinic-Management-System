package config;

import models.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import repository.StaffRepository;
import service.AuditService;

import java.math.BigDecimal;

@Component
public class DataInitializer implements CommandLineRunner {

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public DataInitializer(StaffRepository staffRepository,
                           PasswordEncoder passwordEncoder,
                           AuditService auditService) {
        this.staffRepository = staffRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Override
    public void run(String... args) {
        // ایجاد بلوک اولیه جنسیس در صورت خالی بودن لاگ‌ها
        if (auditService.getAuditTrail().isEmpty()) {
            auditService.logAction("SYSTEM", "CORE", "GENESIS_BOOTSTRAP", "SUCCESS", "127.0.0.1", "ایجاد بلوک جنسیس و فعال‌سازی زنجیره ضدجعل SHA-256 سامانه درمانگاه");
        }

        // ۱. ادمین
        String adminPwd = passwordEncoder.encode("Admin@123");
        Staff admin = staffRepository.findByUsername("admin").orElse(null);
        if (admin == null) {
            Admin rootAdmin = new Admin(
                    "0000000001", "مدیر ارشد سامانه", 38, "09120000001",
                    new BigDecimal("30000.00"), "admin", adminPwd, Admin.AdminRole.SUPER_ADMIN
            );
            rootAdmin.setEncodedPassword(adminPwd);
            rootAdmin.setPasswordHash(adminPwd);
            staffRepository.save(rootAdmin);
            auditService.logAction("SYSTEM", "CORE", "PROVISION_ADMIN", "SUCCESS", "127.0.0.1", "تخصیص حساب پایه مدیر ارشد سامانه");
        } else {
            admin.setEncodedPassword(adminPwd);
            if (admin instanceof Admin a) a.setPasswordHash(adminPwd);
            staffRepository.save(admin);
        }

        // ۲. پزشک
        String docPwd = passwordEncoder.encode("Doctor@123");
        Staff doctor = staffRepository.findByUsername("doctor").orElse(null);
        if (doctor == null) {
            Doctor defaultDoctor = new Doctor(
                    "0000000002", "دکتر علی رضایی", 45, "09120000002",
                    new BigDecimal("25000.00"), "MED-88421",
                    Doctor.Specialization.GENERAL_PRACTICE, new BigDecimal("150.00"), 40
            );
            defaultDoctor.setUsername("doctor");
            defaultDoctor.setEncodedPassword(docPwd);
            staffRepository.save(defaultDoctor);
        } else {
            doctor.setEncodedPassword(docPwd);
            staffRepository.save(doctor);
        }

        // ۳. صندوق‌دار
        String cashPwd = passwordEncoder.encode("Cashier@123");
        Staff cashier = staffRepository.findByUsername("cashier").orElse(null);
        if (cashier == null) {
            Cashier defaultCashier = new Cashier(
                    "0000000003", "مسئول صندوق", 29, "09120000003",
                    new BigDecimal("14000.00"), new BigDecimal("100000.00")
            );
            defaultCashier.setUsername("cashier");
            defaultCashier.setEncodedPassword(cashPwd);
            staffRepository.save(defaultCashier);
        } else {
            cashier.setEncodedPassword(cashPwd);
            staffRepository.save(cashier);
        }

        // ۴. پرستار
        String nursePwd = passwordEncoder.encode("Nurse@123");
        Staff nurse = staffRepository.findByUsername("nurse").orElse(null);
        if (nurse == null) {
            Nurse defaultNurse = new Nurse(
                    "0000000004", "مریم سلیمانی (پرستار)", 31, "09120000004",
                    new BigDecimal("13000.00"), "NUR-55219", "بخش بستری و تریاژ"
            );
            defaultNurse.setUsername("nurse");
            defaultNurse.setEncodedPassword(nursePwd);
            staffRepository.save(defaultNurse);
        } else {
            nurse.setEncodedPassword(nursePwd);
            staffRepository.save(nurse);
        }

        // ۵. پذیرش
        String recPwd = passwordEncoder.encode("Reception@123");
        Staff receptionist = staffRepository.findByUsername("receptionist").orElse(null);
        if (receptionist == null) {
            Receptionist.DeskLocation deskLoc = (Receptionist.DeskLocation.values().length > 0)
                    ? Receptionist.DeskLocation.values()[0] : null;

            Receptionist defaultReceptionist = new Receptionist(
                    "0000000005", "سارا محمدی (پذیرش)", 26, "09120000005",
                    new BigDecimal("11000.00"), "DESK-MAIN", deskLoc
            );
            defaultReceptionist.setUsername("receptionist");
            defaultReceptionist.setEncodedPassword(recPwd);
            staffRepository.save(defaultReceptionist);
        } else {
            receptionist.setEncodedPassword(recPwd);
            staffRepository.save(receptionist);
        }
    }
}