package main;

import enums.PatientStatus;
import models.*;
import system.ClinicMemory;
import system.SystemBootstrap;
import system.SystemClock;

import java.io.Console;
import java.io.File;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Enterprise Hospital Management System Main Controller and Orchestration Engine.
 * Engineered for zero-crash operational resilience, dynamic command-line UX,
 * global transaction persistence, safe input cancellation, and direct JavaFX/Swing compatibility.
 */
public final class Main {

    private static final String DATABASE_FILE_NAME = "hospital_data.enc";
    private static final String BACKUP_DIRECTORY_NAME = "backups";
    private static final String ESCAPE_COMMAND = "CANCEL";

    private static final Pattern DIGIT_PATTERN = Pattern.compile("^[0-9]+$");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("^[0-9]+(\\.[0-9]{1,2})?$");

    private static File databaseFile;
    private static ClinicMemory memory;
    private static char[] masterPassword;
    private static final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    @SuppressWarnings("unused")
    public static void main(String[] args) {
        databaseFile = new File(DATABASE_FILE_NAME);

        printWelcomeHeader();

        // 1. Resilience Bootstrapping & Storage Mount
        try {
            if (SystemBootstrap.isBootstrapRequired(databaseFile)) {
                SystemBootstrap.BootstrapContext context = SystemBootstrap.runInteractiveWizard(databaseFile, ConsoleIO.getScanner());
                memory = context.getMemory();
                masterPassword = context.getMasterPassword();
            } else {
                masterPassword = promptMasterKeyUnlock();
                System.out.println("  [SYSTEM] Verifying cryptographic authenticity and decrypting storage...");
                memory = ClinicMemory.loadFromEncryptedFile(databaseFile, masterPassword);
                System.out.println("  [SECURITY] AES-256-GCM authenticated. Database online.\n");
            }
        } catch (Exception e) {
            System.err.println("\n  [FATAL ERROR] Initialization failure: " + e.getMessage());
            System.err.println("  Halting execution to prevent memory corruption.");
            System.exit(1);
            return;
        }

        // 2. Hardware-Level Panic Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (isShuttingDown.compareAndSet(false, true)) {
                try {
                    if (memory != null && masterPassword != null) {
                        memory.saveToEncryptedFile(databaseFile, masterPassword);
                        System.out.println("\n  [EMERGENCY SHUTDOWN] State synchronized to physical storage (fsync).");
                    }
                } catch (Exception e) {
                    System.err.println("  [SHUTDOWN ERROR] Emergency persist failed: " + e.getMessage());
                }
            }
        }));

        // 3. Enter Main Dynamic Router Loop
        runMainPortalLoop();
    }

    // =========================================================================
    // DYNAMIC NAVIGATION ROUTER
    // =========================================================================

    private static void runMainPortalLoop() {
        while (true) {
            try {
                printBreadcrumb("Main Enterprise Portal");
                displayLiveTelemetryStatusBar();

                System.out.println("  [OPERATIONAL MODULES]");
                System.out.println("  1. Front-Desk Reception & Admissions Desk");
                System.out.println("  2. Clinical Diagnostics & Physician Consultation");
                System.out.println("  3. Point-of-Sale (POS) Billing & Register Terminal");
                System.out.println("  4. Executive Administration & Security Governance");
                System.out.println("  5. Comprehensive Hospital Operations Dashboard");
                System.out.println("  6. Emergency Break-Glass Account Lockout Recovery");
                System.out.println("  0. Secure State Freeze & Orderly Termination");
                System.out.println("--------------------------------------------------------------------------------");

                Integer choice = ConsoleIO.promptInt("Select Operational Portal [0-6]: ", 0, 6);
                if (choice == null) {
                    continue;
                }

                switch (choice) {
                    case 1:
                        runReceptionistPortal();
                        break;
                    case 2:
                        runDoctorPortal();
                        break;
                    case 3:
                        runCashierPortal();
                        break;
                    case 4:
                        runAdminPortal();
                        break;
                    case 5:
                        displayHospitalDashboard();
                        break;
                    case 6:
                        runEmergencyRecoveryPortal();
                        break;
                    case 0:
                        executeOrderlyShutdown();
                        return;
                    default:
                        System.out.println("  [Notice] Selection out of bounds.");
                }
            } catch (Exception e) {
                System.out.println("\n  [CRITICAL RECOVERY] Handled unhandled loop anomaly: " + e.getMessage());
                ConsoleIO.pause();
            }
        }
    }

    // =========================================================================
    // 1. RECEPTION & ADMISSIONS PORTAL
    // =========================================================================

    private static void runReceptionistPortal() {
        List<Receptionist> receptionists = memory.getAllReceptionists();
        if (receptionists.isEmpty()) {
            System.out.println("\n  [Access Denied] No registered Receptionists in repository. Provision via Admin portal.");
            ConsoleIO.pause();
            return;
        }

        Receptionist receptionist = selectReceptionist(receptionists);
        if (receptionist == null) return;

        while (true) {
            printBreadcrumb("Main > Reception Desk [" + receptionist.getWorkstationId() + "]");
            System.out.println("  Active Operator: " + receptionist.getFullName() + " | Location: " + receptionist.getDeskLocation().getDisplayLabel());
            System.out.println("  Terminal Shift : " + receptionist.getOperationalSummary());
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("  1. Admit New Patient into Clinical Pipeline");
            System.out.println("  2. Re-Admit / Check-in Existing Registered Patient");
            System.out.println("  3. View Patient Triage Queue by Workflow Phase");
            System.out.println("  4. Execute Formal Patient Discharge (Pipeline Settlement)");
            System.out.println("  5. Cancel Patient Admission Queue Position");
            System.out.println("  0. Return to Main Portal");
            System.out.println("--------------------------------------------------------------------------------");

            Integer action = ConsoleIO.promptInt("Select Reception Task [0-5]: ", 0, 5);
            if (action == null || action == 0) return;

            try {
                switch (action) {
                    case 1:
                        executeTransactionalAction("Patient Admission", () -> handleNewPatientAdmission(receptionist));
                        break;
                    case 2:
                        executeTransactionalAction("Patient Re-Admission", () -> handlePatientCheckIn(receptionist));
                        break;
                    case 3:
                        handleViewQueueByStatus();
                        break;
                    case 4:
                        executeTransactionalAction("Patient Discharge", () -> handleFormalPatientDischarge(receptionist));
                        break;
                    case 5:
                        executeTransactionalAction("Admission Cancellation", () -> handleCancelAdmission(receptionist));
                        break;
                }
            } catch (Exception e) {
                System.out.println("  [Operation Aborted] " + e.getMessage());
            }
            ConsoleIO.pause();
        }
    }

    private static void handleNewPatientAdmission(Receptionist receptionist) {
        System.out.println("\n  --- CLINICAL ADMISSION INTAKE (Type 'CANCEL' to abort) ---");
        String nationalId = ConsoleIO.promptString("National Identity Number: ");
        if (nationalId == null) return;

        if (memory.findPatientById(nationalId).isPresent()) {
            throw new IllegalArgumentException("Patient ID " + nationalId + " already registered. Use Check-in option.");
        }

        String fullName = ConsoleIO.promptString("Full Legal Name: ");
        if (fullName == null) return;

        Integer age = ConsoleIO.promptInt("Age: ", 0, 130);
        if (age == null) return;

        String contact = ConsoleIO.promptString("Contact Phone: ");
        if (contact == null) return;

        displayEnumOptions(Patient.BloodType.values());
        Integer bloodChoice = ConsoleIO.promptInt("Select Blood Group [1-" + Patient.BloodType.values().length + "]: ", 1, Patient.BloodType.values().length);
        if (bloodChoice == null) return;
        Patient.BloodType bloodType = Patient.BloodType.values()[bloodChoice - 1];

        String allergies = ConsoleIO.promptString("Documented Allergies: ");
        if (allergies == null) return;

        String emergencyContact = ConsoleIO.promptString("Emergency Contact Details: ");
        if (emergencyContact == null) return;

        Patient patient = receptionist.registerPatient(nationalId, fullName, age, contact, bloodType, allergies, emergencyContact);
        patient.setStatus(PatientStatus.WAITING_FOR_DOCTOR);
        memory.registerPatient(patient, receptionist.getNationalId());

        System.out.println("  [Success] Patient " + fullName + " admitted. Assigned status: " + patient.getStatus().getLabel());
    }

    private static void handlePatientCheckIn(Receptionist receptionist) {
        String id = ConsoleIO.promptString("Enter Patient National ID: ");
        if (id == null) return;

        Optional<Patient> opt = memory.findPatientById(id);
        if (opt.isEmpty()) {
            throw new NoSuchElementException("Patient record not found.");
        }

        Patient patient = opt.get();
        receptionist.checkInExistingPatient(patient);
        patient.setStatus(PatientStatus.WAITING_FOR_DOCTOR);
        System.out.println("  [Success] Patient " + patient.getFullName() + " re-admitted to consultation queue.");
    }

    private static void handleViewQueueByStatus() {
        displayEnumOptions(PatientStatus.values());
        Integer choice = ConsoleIO.promptInt("Select Workflow Phase [1-" + PatientStatus.values().length + "]: ", 1, PatientStatus.values().length);
        if (choice == null) return;

        PatientStatus status = PatientStatus.values()[choice - 1];
        List<Patient> list = memory.findPatientsByStatus(status);

        System.out.println("\n  === TRIAGE QUEUE: " + status.getLabel() + " (" + list.size() + ") ===");
        if (list.isEmpty()) {
            System.out.println("  No patients currently in this workflow phase.");
            return;
        }

        printPatientTable(list);
    }

    private static void handleFormalPatientDischarge(Receptionist receptionist) {
        String id = ConsoleIO.promptString("Enter Patient ID for Formal Discharge: ");
        if (id == null) return;

        Optional<Patient> opt = memory.findPatientById(id);
        if (opt.isEmpty()) {
            throw new NoSuchElementException("Patient record not found.");
        }

        Patient patient = opt.get();
        receptionist.dischargePatient(patient);
        System.out.println("  [Success] Patient " + patient.getFullName() + " formally discharged. Encounter closed.");
    }

    private static void handleCancelAdmission(Receptionist receptionist) {
        String id = ConsoleIO.promptString("Enter Patient ID to Cancel: ");
        if (id == null) return;

        Optional<Patient> opt = memory.findPatientById(id);
        if (opt.isEmpty()) {
            throw new NoSuchElementException("Patient record not found.");
        }

        String reason = ConsoleIO.promptString("Reason for Admission Cancellation: ");
        if (reason == null) return;

        receptionist.cancelPatientAdmission(opt.get(), reason);
        System.out.println("  [Success] Admission revoked for patient " + opt.get().getFullName());
    }

    // =========================================================================
    // 2. CLINICAL CONSULTATION PORTAL
    // =========================================================================

    private static void runDoctorPortal() {
        List<Doctor> doctors = memory.getAllDoctors();
        if (doctors.isEmpty()) {
            System.out.println("\n  [Access Denied] No licensed Doctors found in repository. Provision via Admin portal.");
            ConsoleIO.pause();
            return;
        }

        Doctor doctor = selectDoctor(doctors);
        if (doctor == null) return;

        while (true) {
            printBreadcrumb("Main > Clinical Portal [Dr. " + doctor.getFullName() + "]");
            System.out.println("  Practitioner : Dr. " + doctor.getFullName() + " | License: " + doctor.getMedicalLicenseNumber());
            System.out.println("  Specialty    : " + doctor.getSpecialization().getDisplayTitle() + " | Fee: " + doctor.getFormattedConsultationFee());
            System.out.println("  Workload     : " + doctor.getWorkloadDisplay() + (doctor.hasReachedDailyCapacity() ? " [MAX CAPACITY]" : " [ACTIVE]"));
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("  1. Call Next Patient from Waiting Queue (WAITING_FOR_DOCTOR)");
            System.out.println("  2. Review Specific Patient Medical & Encounter History");
            System.out.println("  3. Toggle Physician On-Duty Availability");
            System.out.println("  4. Reset Daily Patient Counter (Shift Handover)");
            System.out.println("  0. Return to Main Portal");
            System.out.println("--------------------------------------------------------------------------------");

            Integer action = ConsoleIO.promptInt("Select Clinical Action [0-4]: ", 0, 4);
            if (action == null || action == 0) return;

            try {
                switch (action) {
                    case 1:
                        executeTransactionalAction("Clinical Consultation", () -> handlePhysicianConsultation(doctor));
                        break;
                    case 2:
                        handleInspectPatientHistory();
                        break;
                    case 3:
                        doctor.setAvailable(!doctor.isAvailable());
                        System.out.println("  [Status Updated] Doctor availability: " + (doctor.isAvailable() ? "AVAILABLE" : "UNAVAILABLE"));
                        break;
                    case 4:
                        doctor.resetDailyPatientCount();
                        System.out.println("  [Shift Handover] Daily patient counter reset to 0.");
                        break;
                }
            } catch (Exception e) {
                System.out.println("  [Clinical Error] " + e.getMessage());
            }
            ConsoleIO.pause();
        }
    }

    private static void handlePhysicianConsultation(Doctor doctor) {
        List<Patient> queue = memory.findPatientsByStatus(PatientStatus.WAITING_FOR_DOCTOR);
        if (queue.isEmpty()) {
            System.out.println("  [Queue Empty] No patients awaiting physician consultation.");
            return;
        }

        Patient patient = queue.getFirst();
        System.out.println("\n  === ATTENDING PATIENT ===");
        System.out.println("  ID       : " + patient.getNationalId() + " | Name: " + patient.getFullName() + " (Age: " + patient.getAge() + ")");
        System.out.println("  Blood    : " + patient.getBloodType().getDisplayLabel() + " | Allergies: " + patient.getKnownAllergies());
        System.out.println("  Emergency: " + patient.getEmergencyContactNumber());
        System.out.println("--------------------------------------------------------------------------------");

        String diagnosis = ConsoleIO.promptString("Enter Clinical Diagnosis: ");
        if (diagnosis == null) return;

        String prescription = ConsoleIO.promptString("Enter Prescription & Dosage: ");
        if (prescription == null) return;

        String notes = ConsoleIO.promptString("Enter Evaluation & Follow-up Notes: ");
        if (notes == null) return;

        MedicalRecord record = doctor.conductConsultation(patient, diagnosis, prescription, notes);

        System.out.println("\n" + record.getFormattedClinicalReport());
        System.out.println("  [Success] Consultation finalized. Record cryptographically sealed.");
        System.out.println("  Patient queued for settlement: " + patient.getStatus().getLabel());
    }

    private static void handleInspectPatientHistory() {
        String id = ConsoleIO.promptString("Enter Patient National ID: ");
        if (id == null) return;

        Optional<Patient> opt = memory.findPatientById(id);
        if (opt.isEmpty()) {
            throw new NoSuchElementException("Patient not found.");
        }

        Patient patient = opt.get();
        System.out.println("\n================================================================================");
        System.out.println("                 PATIENT CLINICAL & BILLING LEDGER RECORD                       ");
        System.out.println("================================================================================");
        System.out.println("  Patient ID: " + patient.getNationalId() + " | Name: " + patient.getFullName());
        System.out.println("  Status    : " + patient.getStatus().getLabel() + " | Total Settlement: " + patient.getFormattedTotalPaid());
        System.out.println("--------------------------------------------------------------------------------");

        List<MedicalRecord> history = patient.getMedicalHistory();
        if (history.isEmpty()) {
            System.out.println("  No medical records filed for this patient.");
        } else {
            for (MedicalRecord mr : history) {
                System.out.println(mr.getFormattedClinicalReport());
                System.out.println("  Cryptographic Integrity: " + (mr.verifyIntegrity() ? "PASSED (TAMPER-FREE)" : "FAILED (DATA CORRUPT)"));
            }
        }
    }

    // =========================================================================
    // 3. POINT-OF-SALE BILLING & CASHIER TERMINAL
    // =========================================================================

    private static void runCashierPortal() {
        List<Cashier> cashiers = memory.getAllCashiers();
        if (cashiers.isEmpty()) {
            System.out.println("\n  [Access Denied] No Cashier terminals registered. Provision via Admin portal.");
            ConsoleIO.pause();
            return;
        }

        Cashier cashier = selectCashier(cashiers);
        if (cashier == null) return;

        while (true) {
            printBreadcrumb("Main > POS Terminal [" + cashier.getRegisterTerminalId() + "]");
            System.out.println("  Operator   : " + cashier.getFullName() + " | Terminal ID: " + cashier.getRegisterTerminalId());
            System.out.println("  Shift State: " + cashier.getOperationalStatusDisplay());
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("  1. Open Register Shift (Establish Float)");
            System.out.println("  2. Process Patient Fee Settlement (WAITING_FOR_PAYMENT)");
            System.out.println("  3. Issue Supervised Payment Refund (Requires Super Admin Sign-off)");
            System.out.println("  4. View Active Shift Ledger & Transactions");
            System.out.println("  5. Close Register Shift & Produce Reconciliation Report");
            System.out.println("  0. Return to Main Portal");
            System.out.println("--------------------------------------------------------------------------------");

            Integer action = ConsoleIO.promptInt("Select Cashier Task [0-5]: ", 0, 5);
            if (action == null || action == 0) return;

            try {
                switch (action) {
                    case 1:
                        executeTransactionalAction("Open Shift", () -> handleOpenCashierShift(cashier));
                        break;
                    case 2:
                        executeTransactionalAction("Fee Settlement", () -> handleProcessSettlement(cashier));
                        break;
                    case 3:
                        executeTransactionalAction("Supervised Refund", () -> handleSupervisedRefund(cashier));
                        break;
                    case 4:
                        handleViewShiftLedger(cashier);
                        break;
                    case 5:
                        executeTransactionalAction("Close Shift", () -> handleCloseShift(cashier));
                        break;
                }
            } catch (Exception e) {
                System.out.println("  [Billing Error] " + e.getMessage());
            }
            ConsoleIO.pause();
        }
    }

    private static void handleOpenCashierShift(Cashier cashier) {
        if (cashier.isOnDuty()) {
            throw new IllegalStateException("Shift already active: " + cashier.getCurrentShiftId());
        }
        BigDecimal floatAmount = ConsoleIO.promptBigDecimal("Enter Drawer Opening Cash Float ($): ");
        if (floatAmount == null) return;

        cashier.openShift(floatAmount, SystemClock.now());
        System.out.println("  [Success] Shift " + cashier.getCurrentShiftId() + " successfully initiated.");
    }

    private static void handleProcessSettlement(Cashier cashier) {
        List<Patient> queue = memory.findPatientsByStatus(PatientStatus.WAITING_FOR_PAYMENT);
        if (queue.isEmpty()) {
            System.out.println("  [Queue Empty] No patients awaiting payment settlement.");
            return;
        }

        System.out.println("\n  === PATIENTS AWAITING PAYMENT ===");
        for (int i = 0; i < queue.size(); i++) {
            System.out.printf("  %d. %s | %s%n", (i + 1), queue.get(i).getNationalId(), queue.get(i).getFullName());
        }

        Integer index = ConsoleIO.promptInt("Select Patient [1-" + queue.size() + "]: ", 1, queue.size());
        if (index == null) return;
        Patient patient = queue.get(index - 1);

        BigDecimal chargeAmount = ConsoleIO.promptBigDecimal("Enter Billing Settlement Amount ($): ");
        if (chargeAmount == null) return;

        displayEnumOptions(PaymentRecord.PaymentMethod.values());
        Integer methodChoice = ConsoleIO.promptInt("Select Payment Channel [1-" + PaymentRecord.PaymentMethod.values().length + "]: ", 1, PaymentRecord.PaymentMethod.values().length);
        if (methodChoice == null) return;
        PaymentRecord.PaymentMethod method = PaymentRecord.PaymentMethod.values()[methodChoice - 1];

        String description = ConsoleIO.promptString("Service Description: ");
        if (description == null) return;

        Receipt receipt = cashier.processPayment(patient, chargeAmount, method, description);

        System.out.println("\n" + receipt.getPrintableReceipt());
        System.out.println("  [Success] Settlement committed. Patient eligible for formal discharge.");
    }

    private static void handleSupervisedRefund(Cashier cashier) {
        String patientId = ConsoleIO.promptString("Enter Patient ID for Refund: ");
        if (patientId == null) return;

        Optional<Patient> opt = memory.findPatientById(patientId);
        if (opt.isEmpty()) {
            throw new NoSuchElementException("Patient not found.");
        }

        Patient patient = opt.get();
        List<Receipt> history = patient.getBillingHistory();
        if (history.isEmpty()) {
            throw new IllegalStateException("Patient has no billing history on record.");
        }

        System.out.println("\n  === SETTLED VOUCHERS ===");
        for (Receipt r : history) {
            System.out.printf("  - %s | %s | %s%n", r.getReceiptId(), r.getFormattedAmount(), r.getTimestamp().toFullDisplayString());
        }

        String receiptId = ConsoleIO.promptString("Enter Target Receipt ID: ");
        if (receiptId == null) return;

        BigDecimal refundAmount = ConsoleIO.promptBigDecimal("Enter Refund Amount ($): ");
        if (refundAmount == null) return;

        String reason = ConsoleIO.promptString("Administrative Justification: ");
        if (reason == null) return;

        System.out.println("\n  --- SUPERVISOR DUAL-AUTHORIZATION ---");
        String adminUser = ConsoleIO.promptString("Supervisor Admin Username: ");
        if (adminUser == null) return;

        Optional<Admin> adminOpt = memory.findAdminByUsername(adminUser);
        if (adminOpt.isEmpty()) {
            throw new SecurityException("Supervisor username not found.");
        }

        char[] supervisorPass = ConsoleIO.readPassword("Supervisor Password: ");
        Admin supervisor = adminOpt.get();
        if (!supervisor.authenticate(new String(supervisorPass), SystemClock.now())) {
            Arrays.fill(supervisorPass, '\0');
            throw new SecurityException("Supervisor authentication denied.");
        }
        Arrays.fill(supervisorPass, '\0');

        Receipt refundReceipt = cashier.issueRefund(patient, receiptId, refundAmount, reason, supervisor, SystemClock.now());
        System.out.println("\n" + refundReceipt.getPrintableReceipt());
        System.out.println("  [Success] Refund successfully authorized and logged.");
    }

    private static void handleViewShiftLedger(Cashier cashier) {
        List<Receipt> ledger = cashier.getShiftLedger();
        System.out.println("\n  === ACTIVE SHIFT LEDGER (" + ledger.size() + " Vouchers) ===");
        if (ledger.isEmpty()) {
            System.out.println("  No transactions executed in current shift.");
            return;
        }

        System.out.printf("  %-14s %-12s %-16s %-15s%n", "RECEIPT ID", "AMOUNT", "PAYMENT METHOD", "PATIENT ID");
        System.out.println("  ------------------------------------------------------------");
        for (Receipt r : ledger) {
            System.out.printf("  %-14s %-12s %-16s %-15s%n", r.getReceiptId(), r.getFormattedAmount(), r.getPaymentMethod(), r.getPatientNationalId());
        }
        System.out.println("  ------------------------------------------------------------");
        System.out.println("  Shift Cumulative Net: " + cashier.getFormattedNetRevenue());
    }

    private static void handleCloseShift(Cashier cashier) {
        if (!cashier.isOnDuty()) {
            throw new IllegalStateException("No active shift is currently open.");
        }

        BigDecimal countedCash = ConsoleIO.promptBigDecimal("Enter Physical Cash Counted in Drawer ($): ");
        if (countedCash == null) return;

        Cashier.ShiftReconciliationReport report = cashier.closeShift(countedCash, SystemClock.now());

        System.out.println("\n" + report.getFormattedSummary());
        System.out.println("  [Shift Closed] Reconciliation report sealed with SHA-256.");
    }

    // =========================================================================
    // 4. EXECUTIVE ADMINISTRATION & GOVERNANCE PORTAL
    // =========================================================================

    private static void runAdminPortal() {
        printBreadcrumb("Main > Admin Gateway");
        String username = ConsoleIO.promptString("Administrator Username: ");
        if (username == null) return;

        Optional<Admin> opt = memory.findAdminByUsername(username);
        if (opt.isEmpty()) {
            System.out.println("  [Security Notice] User credentials not recognized.");
            ConsoleIO.pause();
            return;
        }

        Admin admin = opt.get();
        if (admin.isAccountLocked()) {
            System.out.println("  [Security Lockout] Account is locked due to security policy.");
            System.out.println("  Lockout Cooldown: " + admin.getFormattedRemainingCooldown() + " remaining.");
            ConsoleIO.pause();
            return;
        }

        char[] pass = ConsoleIO.readPassword("Administrative Password: ");
        try {
            if (!admin.authenticate(new String(pass), SystemClock.now())) {
                System.out.println("  [Access Denied] Invalid credentials. Failed attempts: " + admin.getFailedLoginAttempts() + "/5");
                ConsoleIO.pause();
                return;
            }
        } finally {
            Arrays.fill(pass, '\0');
        }

        while (true) {
            printBreadcrumb("Main > Admin Executive Console [" + admin.getUsername() + "]");
            System.out.println("  Administrator: " + admin.getFullName() + " | Role: " + admin.getRole().getDisplayTitle());
            System.out.println("  Last Access  : " + (admin.getLastLoginTimestamp() != null ? admin.getLastLoginTimestamp().toFullDisplayString() : "Active Session"));
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("  1. Provision New Clinical or Administrative Staff Member");
            System.out.println("  2. Disburse Staff Monthly Payroll (Issue PaymentRecord)");
            System.out.println("  3. View Hospital Cryptographic Audit Trail (Blockchain Ledger)");
            System.out.println("  4. Export Encrypted Cold Backup Snapshot (Server Migration)");
            System.out.println("  5. Verify Integrity of an External Backup Archive");
            System.out.println("  6. Migrate Person National Identity (Immutable Re-binding)");
            System.out.println("  7. Unlock Suspended Administrator Account");
            System.out.println("  0. Log Out of Administrative Console");
            System.out.println("--------------------------------------------------------------------------------");

            Integer action = ConsoleIO.promptInt("Select Governance Task [0-7]: ", 0, 7);
            if (action == null || action == 0) return;

            try {
                switch (action) {
                    case 1:
                        executeTransactionalAction("Staff Provisioning", () -> handleProvisionStaff(admin));
                        break;
                    case 2:
                        executeTransactionalAction("Payroll Disbursement", () -> handleDisburseSalary(admin));
                        break;
                    case 3:
                        handleViewAuditTrail(admin);
                        break;
                    case 4:
                        handleExportBackup(admin);
                        break;
                    case 5:
                        handleVerifyExternalBackup(admin);
                        break;
                    case 6:
                        executeTransactionalAction("Identity Migration", () -> handleIdentityMigration(admin));
                        break;
                    case 7:
                        executeTransactionalAction("Account Unlock", () -> handleUnlockAdminAccount(admin));
                        break;
                }
            } catch (Exception e) {
                System.out.println("  [Administrative Error] " + e.getMessage());
            }
            ConsoleIO.pause();
        }
    }

    private static void handleProvisionStaff(Admin admin) {
        if (!admin.getRole().canManageStaff()) {
            throw new SecurityException("Role " + admin.getRole().name() + " lacks staff provisioning privileges.");
        }

        System.out.println("\n  --- PROVISION NEW HOSPITAL WORKFORCE (Type 'CANCEL' to abort) ---");
        System.out.println("  1. Licensed Doctor / Specialist");
        System.out.println("  2. Front-Desk Receptionist");
        System.out.println("  3. Point-of-Sale Cashier");
        System.out.println("  4. Additional Administrator");
        Integer roleChoice = ConsoleIO.promptInt("Select Staff Role [1-4]: ", 1, 4);
        if (roleChoice == null) return;

        String nationalId = ConsoleIO.promptString("National Identity Number: ");
        if (nationalId == null) return;
        String fullName = ConsoleIO.promptString("Full Legal Name: ");
        if (fullName == null) return;
        Integer age = ConsoleIO.promptInt("Age: ", 18, 100);
        if (age == null) return;
        String contact = ConsoleIO.promptString("Phone Number: ");
        if (contact == null) return;
        BigDecimal baseSalary = ConsoleIO.promptBigDecimal("Monthly Base Salary ($): ");
        if (baseSalary == null) return;

        switch (roleChoice) {
            case 1:
                String license = ConsoleIO.promptString("Medical License Number: ");
                if (license == null) return;
                displayEnumOptions(Doctor.Specialization.values());
                Integer specChoice = ConsoleIO.promptInt("Select Specialization: ", 1, Doctor.Specialization.values().length);
                if (specChoice == null) return;
                Doctor.Specialization spec = Doctor.Specialization.values()[specChoice - 1];
                BigDecimal fee = ConsoleIO.promptBigDecimal("Consultation Fee ($): ");
                if (fee == null) return;
                Integer maxDaily = ConsoleIO.promptInt("Max Daily Patients Capacity (1-200): ", 1, 200);
                if (maxDaily == null) return;

                Doctor doc = new Doctor(nationalId, fullName, age, contact, baseSalary, license, spec, fee, maxDaily);
                memory.registerStaff(doc, admin.getNationalId());
                System.out.println("  [Success] Doctor Dr. " + fullName + " registered.");
                break;

            case 2:
                String deskId = ConsoleIO.promptString("Terminal Station ID (e.g. REC-01): ");
                if (deskId == null) return;
                displayEnumOptions(Receptionist.DeskLocation.values());
                Integer locChoice = ConsoleIO.promptInt("Select Desk Location: ", 1, Receptionist.DeskLocation.values().length);
                if (locChoice == null) return;
                Receptionist.DeskLocation loc = Receptionist.DeskLocation.values()[locChoice - 1];

                Receptionist rec = new Receptionist(nationalId, fullName, age, contact, baseSalary, deskId, loc);
                memory.registerStaff(rec, admin.getNationalId());
                System.out.println("  [Success] Receptionist " + fullName + " registered.");
                break;

            case 3:
                String termId = ConsoleIO.promptString("POS Terminal Station ID (e.g. POS-01): ");
                if (termId == null) return;
                Cashier cash = new Cashier(nationalId, fullName, age, contact, baseSalary, termId);
                memory.registerStaff(cash, admin.getNationalId());
                System.out.println("  [Success] Cashier " + fullName + " registered.");
                break;

            case 4:
                String uName = ConsoleIO.promptString("Login Username: ");
                if (uName == null) return;
                char[] nPass = ConsoleIO.readPassword("Initial Password: ");
                displayEnumOptions(Admin.AdminRole.values());
                Integer admRoleChoice = ConsoleIO.promptInt("Select Admin Role: ", 1, Admin.AdminRole.values().length);
                if (admRoleChoice == null) return;
                Admin.AdminRole aRole = Admin.AdminRole.values()[admRoleChoice - 1];

                Admin newAdmin = new Admin(nationalId, fullName, age, contact, baseSalary, uName, new String(nPass), aRole);
                Arrays.fill(nPass, '\0');
                memory.registerStaff(newAdmin, admin.getNationalId());
                System.out.println("  [Success] Administrator account '" + uName + "' provisioned.");
                break;
        }
    }

    private static void handleDisburseSalary(Admin admin) {
        List<Staff> staffList = memory.getAllStaff();
        if (staffList.isEmpty()) {
            System.out.println("  No staff members registered.");
            return;
        }

        System.out.println("\n  === HOSPITAL PAYROLL ROSTER ===");
        for (int i = 0; i < staffList.size(); i++) {
            Staff s = staffList.get(i);
            System.out.printf("  %d. %s | %s | %s | Base: $%,.2f%n", (i + 1), s.getNationalId(), s.getFullName(), s.getClass().getSimpleName(), s.getBaseSalary());
        }

        Integer index = ConsoleIO.promptInt("Select Staff Target [1-" + staffList.size() + "]: ", 1, staffList.size());
        if (index == null) return;
        Staff target = staffList.get(index - 1);

        displayEnumOptions(PaymentRecord.PaymentMethod.values());
        Integer methodChoice = ConsoleIO.promptInt("Select Payout Channel [1-" + PaymentRecord.PaymentMethod.values().length + "]: ", 1, PaymentRecord.PaymentMethod.values().length);
        if (methodChoice == null) return;
        PaymentRecord.PaymentMethod method = PaymentRecord.PaymentMethod.values()[methodChoice - 1];

        String destAccount = ConsoleIO.promptString("Destination Account / IBAN: ");
        if (destAccount == null) return;

        String refNumber = "PAYROLL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        PaymentRecord record = admin.payStaffSalary(target, method, destAccount, refNumber);

        System.out.println("\n" + record);
        System.out.println("  [Success] Salary disbursement executed and appended to staff ledger.");
    }

    private static void handleViewAuditTrail(Admin admin) {
        if (!admin.getRole().canViewAuditLogs()) {
            throw new SecurityException("Role " + admin.getRole().name() + " lacks audit inspection authorization.");
        }

        List<ClinicMemory.AuditLogEntry> logs = memory.getAuditLogs();
        System.out.println("\n================================================================================");
        System.out.println("             HOSPITAL CRYPTOGRAPHIC BLOCKCHAIN AUDIT TRAIL                      ");
        System.out.println("================================================================================");
        for (ClinicMemory.AuditLogEntry entry : logs) {
            System.out.println("  " + entry.toString());
        }
        System.out.println("================================================================================");
    }

    private static void handleExportBackup(Admin admin) throws Exception {
        File backupDir = new File(BACKUP_DIRECTORY_NAME);
        File exportedFile = admin.exportSystemBackup(memory, backupDir, masterPassword);
        System.out.println("  [Backup Success] Encrypted snapshot archived to: " + exportedFile.getAbsolutePath());
    }

    private static void handleVerifyExternalBackup(Admin admin) {
        String path = ConsoleIO.promptString("Enter path to external backup (.enc): ");
        if (path == null) return;

        File file = new File(path);
        boolean valid = admin.verifyBackupFile(file, masterPassword);
        System.out.println("  [Verification Result] Integrity Seal: " + (valid ? "AUTHENTIC & VERIFIED" : "CORRUPT / INVALID KEY"));
    }

    private static void handleIdentityMigration(Admin admin) {
        String oldId = ConsoleIO.promptString("Enter Current National Identity Number: ");
        if (oldId == null) return;
        String newId = ConsoleIO.promptString("Enter New Verified National Identity Number: ");
        if (newId == null) return;

        memory.migratePersonId(oldId, newId, admin.getNationalId());
        System.out.println("  [Success] Identity migrated safely across all clinical relationships.");
    }

    private static void handleUnlockAdminAccount(Admin admin) {
        String targetUsername = ConsoleIO.promptString("Enter Locked Administrator Username: ");
        if (targetUsername == null) return;

        Optional<Admin> opt = memory.findAdminByUsername(targetUsername);
        if (opt.isEmpty()) {
            throw new NoSuchElementException("Target administrator account not found.");
        }

        Admin target = opt.get();
        target.unlockBy(admin);
        System.out.println("  [Success] Administrative lock revoked. Account reactivated.");
    }

    // =========================================================================
    // 5. LIVE TELEMETRY & OPERATIONS DASHBOARD
    // =========================================================================

    private static void displayHospitalDashboard() {
        printBreadcrumb("Main > Hospital Telemetry Dashboard");
        System.out.println("  System Timestamp          : " + SystemClock.now().toAuditString());
        System.out.println("  Hospital Cumulative Revenue: " + memory.getFormattedTotalHospitalRevenue());
        System.out.println("  Total Admitted Patients   : " + memory.getTotalRegisteredPatientsCount());
        System.out.println("  Active Hospital Workforce : " + memory.getTotalStaffCount());
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("  PATIENT QUEUE & TRIAGE STATUS BREAKDOWN:");
        for (PatientStatus status : PatientStatus.values()) {
            System.out.printf("   * %-25s : %d patients%n", status.getLabel(), memory.countPatientsByStatus(status));
        }
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("  PHYSICIAN WORKLOAD & SPECIALTY COVERAGE:");
        List<Doctor> docs = memory.getAllDoctors();
        if (docs.isEmpty()) {
            System.out.println("   No licensed physicians currently registered.");
        } else {
            for (Doctor d : docs) {
                System.out.printf("   * Dr. %-18s [%-18s] Workload: %-12s Available: %s%n",
                        d.getFullName(), d.getSpecialization().getDisplayTitle(), d.getWorkloadDisplay(), (d.isAvailable() ? "YES" : "NO"));
            }
        }
        System.out.println("================================================================================");
        ConsoleIO.pause();
    }

    // =========================================================================
    // 6. EMERGENCY BREAK-GLASS PORTAL
    // =========================================================================

    private static void runEmergencyRecoveryPortal() {
        printBreadcrumb("Main > Emergency Break-Glass Portal");
        System.out.println("  Notice: This recovery pipeline bypasses security lockouts using the physical");
        System.out.println("  break-glass key generated during root provisioning.\n");

        String username = ConsoleIO.promptString("Enter Locked Administrator Username: ");
        if (username == null) return;

        Optional<Admin> opt = memory.findAdminByUsername(username);
        if (opt.isEmpty()) {
            System.out.println("  [Notice] Target user not found.");
            ConsoleIO.pause();
            return;
        }

        Admin admin = opt.get();
        String token = ConsoleIO.promptString("Enter Recovery Token (RECOVERY-...): ");
        if (token == null) return;

        if (admin.emergencyUnlockWithRecoveryKey(token)) {
            System.out.println("  [SUCCESS] Token validated. Administrative lockout bypassed.");
            char[] newPass = ConsoleIO.readPassword("Enter New Secure Password: ");
            admin.changePassword(new String(newPass), new String(newPass));
            Arrays.fill(newPass, '\0');
            executeTransactionalAction("Emergency Unlock", () -> {});
            System.out.println("  [Success] Password updated. You may now log in normally.");
        } else {
            System.out.println("  [SECURITY ALERT] Invalid recovery token. Attempt logged.");
        }
        ConsoleIO.pause();
    }

    // =========================================================================
    // TRANSACTION PERSISTENCE PIPELINE (CRASH-RESISTANT FSYNC)
    // =========================================================================

    private interface Action {
        void execute() throws Exception;
    }

    private static void executeTransactionalAction(String actionName, Action action) {
        try {
            action.execute();
            memory.saveToEncryptedFile(databaseFile, masterPassword);
        } catch (Exception e) {
            System.out.println("  [Transaction Failed - " + actionName + "] " + e.getMessage());
        }
    }

    private static void executeOrderlyShutdown() {
        System.out.println("\n  [SHUTDOWN] Performing clean state freeze and hardware synchronization...");
        if (isShuttingDown.compareAndSet(false, true)) {
            try {
                memory.saveToEncryptedFile(databaseFile, masterPassword);
                System.out.println("  [SUCCESS] All records encrypted and hardware-synced. System terminated cleanly.");
            } catch (Exception e) {
                System.err.println("  [ERROR] Clean shutdown failed: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // PRESENTATION & UI FORMATTING UTILITIES
    // =========================================================================

    private static void printWelcomeHeader() {
        System.out.println("================================================================================");
        System.out.println("         ENTERPRISE HOSPITAL GOVERNANCE & CLINICAL PIPELINE SYSTEM              ");
        System.out.println("           High-Assurance Medical Ledger - Zero-Tolerance Resilience            ");
        System.out.println("================================================================================");
    }

    private static void printBreadcrumb(String path) {
        System.out.println("\n================================================================================");
        System.out.println("  LOCATION: " + path);
        System.out.println("================================================================================");
    }

    private static void displayLiveTelemetryStatusBar() {
        System.out.printf("  [LIVE TELEMETRY] %s | Patients: %d | Staff: %d | Revenue: %s%n",
                SystemClock.now().toTimeString(),
                memory.getTotalRegisteredPatientsCount(),
                memory.getTotalStaffCount(),
                memory.getFormattedTotalHospitalRevenue());
        System.out.println("--------------------------------------------------------------------------------");
    }

    private static void printPatientTable(List<Patient> patients) {
        System.out.printf("  %-15s %-20s %-5s %-8s %-15s%n", "NATIONAL ID", "FULL NAME", "AGE", "BLOOD", "CONTACT");
        System.out.println("  ------------------------------------------------------------------");
        for (Patient p : patients) {
            System.out.printf("  %-15s %-20s %-5d %-8s %-15s%n",
                    p.getNationalId(), p.getFullName(), p.getAge(), p.getBloodType().getDisplayLabel(), p.getContactNumber());
        }
        System.out.println("  ------------------------------------------------------------------");
    }

    private static char[] promptMasterKeyUnlock() {
        System.out.println("\n  [AUTHENTICATION REQUIRED] Enter Database Master Encryption Password.");
        for (int attempts = 3; attempts > 0; attempts--) {
            char[] pass = ConsoleIO.readPassword("Master Encryption Key: ");
            if (pass.length >= 8) {
                return pass;
            }
            System.out.println("  [Error] Invalid key. Minimum length is 8 characters. Attempts left: " + (attempts - 1));
        }
        throw new SecurityException("Too many failed master key unlock attempts. Terminating.");
    }

    private static <T extends Enum<T>> void displayEnumOptions(T[] values) {
        System.out.println();
        for (int i = 0; i < values.length; i++) {
            System.out.printf("   %d. %s%n", (i + 1), values[i].toString());
        }
    }

    private static Receptionist selectReceptionist(List<Receptionist> list) {
        System.out.println("\n  === SELECT WORKSTATION OPERATOR ===");
        for (int i = 0; i < list.size(); i++) {
            System.out.printf("  %d. %s (%s)%n", (i + 1), list.get(i).getFullName(), list.get(i).getWorkstationId());
        }
        Integer index = ConsoleIO.promptInt("Select Operator [1-" + list.size() + "]: ", 1, list.size());
        return index != null ? list.get(index - 1) : null;
    }

    private static Doctor selectDoctor(List<Doctor> list) {
        System.out.println("\n  === SELECT LICENSED DOCTOR ===");
        for (int i = 0; i < list.size(); i++) {
            System.out.printf("  %d. Dr. %s (%s)%n", (i + 1), list.get(i).getFullName(), list.get(i).getSpecialization().getDisplayTitle());
        }
        Integer index = ConsoleIO.promptInt("Select Physician [1-" + list.size() + "]: ", 1, list.size());
        return index != null ? list.get(index - 1) : null;
    }

    private static Cashier selectCashier(List<Cashier> list) {
        System.out.println("\n  === SELECT CASHIER TERMINAL ===");
        for (int i = 0; i < list.size(); i++) {
            System.out.printf("  %d. %s (%s)%n", (i + 1), list.get(i).getFullName(), list.get(i).getRegisterTerminalId());
        }
        Integer index = ConsoleIO.promptInt("Select Terminal [1-" + list.size() + "]: ", 1, list.size());
        return index != null ? list.get(index - 1) : null;
    }

    // =========================================================================
    // CRASH-PROOF SECURE CONSOLE I/O SUBSYSTEM (CONSOLEIO)
    // =========================================================================

    private static final class ConsoleIO {
        private static final Scanner scanner = new Scanner(System.in);

        public static Scanner getScanner() {
            return scanner;
        }

        public static String promptString(String prompt) {
            while (true) {
                System.out.print("  " + prompt);
                String line = readLineSafely();
                if (line == null) return null;
                if (line.equalsIgnoreCase(ESCAPE_COMMAND)) {
                    System.out.println("  [Action Cancelled by User]");
                    return null;
                }
                if (!line.isEmpty()) {
                    return line;
                }
                System.out.println("  [Input Notice] Field cannot be empty. Type 'CANCEL' to abort.");
            }
        }

        public static Integer promptInt(String prompt, int min, int max) {
            while (true) {
                System.out.print("  " + prompt);
                String line = readLineSafely();
                if (line == null) return null;
                if (line.equalsIgnoreCase(ESCAPE_COMMAND)) {
                    System.out.println("  [Action Cancelled by User]");
                    return null;
                }
                if (DIGIT_PATTERN.matcher(line).matches()) {
                    try {
                        int val = Integer.parseInt(line);
                        if (val >= min && val <= max) {
                            return val;
                        }
                    } catch (NumberFormatException ignored) {}
                }
                System.out.printf("  [Input Notice] Enter a number between %d and %d. Type 'CANCEL' to abort.%n", min, max);
            }
        }

        public static BigDecimal promptBigDecimal(String prompt) {
            while (true) {
                System.out.print("  " + prompt);
                String line = readLineSafely();
                if (line == null) return null;
                if (line.equalsIgnoreCase(ESCAPE_COMMAND)) {
                    System.out.println("  [Action Cancelled by User]");
                    return null;
                }
                if (AMOUNT_PATTERN.matcher(line).matches()) {
                    try {
                        BigDecimal val = new BigDecimal(line);
                        if (val.compareTo(BigDecimal.ZERO) >= 0) {
                            return val;
                        }
                    } catch (Exception ignored) {}
                }
                System.out.println("  [Input Notice] Enter a valid non-negative monetary figure (e.g. 150.00). Type 'CANCEL' to abort.");
            }
        }

        public static char[] readPassword(String prompt) {
            Console console = System.console();
            if (console != null) {
                char[] pass = console.readPassword("  " + prompt);
                return (pass != null) ? pass : new char[0];
            }
            System.out.print("  " + prompt);
            String line = readLineSafely();
            return (line != null) ? line.toCharArray() : new char[0];
        }

        public static void pause() {
            System.out.print("\n  Press Enter to continue...");
            readLineSafely();
        }

        private static String readLineSafely() {
            try {
                if (scanner.hasNextLine()) {
                    return scanner.nextLine().trim();
                }
            } catch (Exception ignored) {}
            return null;
        }
    }
}