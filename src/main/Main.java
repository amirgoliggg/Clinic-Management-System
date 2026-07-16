package main;

import models.*;
import system.ClinicMemory;
import enums.PatientStatus;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        ClinicMemory memory = new ClinicMemory();

        memory.loadDatabase();

        if (memory.staffDatabase.isEmpty()) {
            Admin masterAdmin = new Admin("System Manager", "0000000000", 45, "555-0000", 3000.0);
            memory.registerStaff(masterAdmin);
        }

        boolean programRunning = true;

        while (programRunning) {
            System.out.println("\n--- MAIN PORTAL ---");
            System.out.println("1. Administrator Login");
            System.out.println("2. Receptionist Login");
            System.out.println("3. Doctor Login");
            System.out.println("4. Cashier Login");
            System.out.println("5. Shutdown System");
            System.out.print("Select module: ");

            int moduleChoice = getIntInput(scanner, 1, 5);

            switch (moduleChoice) {
                case 1: runAdminModule(scanner, memory); break;
                case 2: runReceptionistModule(scanner, memory); break;
                case 3: runDoctorModule(scanner, memory); break;
                case 4: runCashierModule(scanner, memory); break;
                case 5:
                    System.out.println("Saving binary database state...");
                    memory.saveDatabase();
                    System.out.println("Exporting text report...");
                    memory.exportSystemReportToFile();
                    System.out.println("System Shutdown complete.");
                    programRunning = false;
                    break;
            }
        }
        scanner.close();
    }

    private static int getIntInput(Scanner scanner, int min, int max) {
        while (true) {
            try {
                int val = Integer.parseInt(scanner.nextLine().trim());
                if (val < min || val > max) {
                    System.out.print("Please enter a number between " + min + " and " + max + ": ");
                    continue;
                }
                return val;
            } catch (NumberFormatException e) {
                System.out.print("Invalid input! Please enter a valid number: ");
            }
        }
    }

    private static double getDoubleInput(Scanner scanner, double min) {
        while (true) {
            try {
                double val = Double.parseDouble(scanner.nextLine().trim());
                if (val < min) {
                    System.out.print("Value cannot be less than " + min + "! Try again: ");
                    continue;
                }
                return val;
            } catch (NumberFormatException e) {
                System.out.print("Invalid input! Please enter a valid amount: ");
            }
        }
    }

    private static String getStringInput(Scanner scanner) {
        while (true) {
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                System.out.print("Input cannot be empty! Please type something: ");
            } else {
                return input;
            }
        }
    }

    private static void runAdminModule(Scanner scanner, ClinicMemory memory) {
        boolean active = true;
        while (active) {
            System.out.println("\n--- ADMIN DASHBOARD (" + memory.systemDate.getFullDate() + ") ---");
            System.out.println("1. Hire New Staff");
            System.out.println("2. View All Staff Details");
            System.out.println("3. Edit Person's Name (Staff or Patient)");
            System.out.println("4. Fire/Dismiss Staff");
            System.out.println("5. Search & View Patient Full Details");
            System.out.println("6. Discharge Patient (Memory CleanUp)");
            System.out.println("7. Advance System Date (Next Day)");
            System.out.println("8. Back to Main Portal");
            System.out.print("Choice: ");

            int choice = getIntInput(scanner, 1, 8);

            switch (choice) {
                case 1:
                    System.out.print("National ID: ");
                    String nid = getStringInput(scanner);

                    boolean isDuplicateStaff = false;
                    for (Staff s : memory.staffDatabase) {
                        if (s.getNationalId().equalsIgnoreCase(nid)) {
                            System.out.println("ERROR: A staff member with this ID already exists!");
                            isDuplicateStaff = true; break;
                        }
                    }
                    if (isDuplicateStaff) break;

                    System.out.print("Full Name: ");
                    String name = getStringInput(scanner);
                    System.out.print("Age (18-70): ");
                    int age = getIntInput(scanner, 18, 70);
                    System.out.print("Phone: ");
                    String phone = getStringInput(scanner);
                    System.out.print("Base Salary: $");
                    double salary = getDoubleInput(scanner, 100.0);

                    System.out.println("Select Department: 1.Doctor 2.Nurse 3.Receptionist 4.Cashier");
                    int dept = getIntInput(scanner, 1, 4);

                    if (dept == 1) {
                        System.out.print("Specialty: ");
                        String spec = getStringInput(scanner);
                        System.out.print("Consultation Fee: $");
                        int fee = getIntInput(scanner, 10, 5000);
                        memory.registerStaff(new Doctor(name, nid, age, phone, salary, spec, fee));
                    } else if (dept == 2) {
                        System.out.print("Nursing Department: ");
                        String nDept = getStringInput(scanner);
                        memory.registerStaff(new Nurse(name, nid, age, phone, salary, nDept));
                    } else if (dept == 3) {
                        memory.registerStaff(new Receptionist(name, nid, age, phone, salary));
                    } else if (dept == 4) {
                        memory.registerStaff(new Cashier(name, nid, age, phone, salary));
                    }
                    System.out.println("Staff hired successfully.");
                    break;
                case 2:
                    for (Staff staff : memory.staffDatabase) {
                        System.out.println(staff.getStaffId() + " - " + staff.getFullName() + " (" + staff.getJobTitle() + ")");
                    }
                    break;
                case 3:
                    System.out.print("Enter ID (EMP-xxx for Staff or PAT-xxx for Patient): ");
                    String editId = getStringInput(scanner);

                    if (editId.toUpperCase().startsWith("EMP")) {
                        Staff s = memory.searchStaffById(editId);
                        if (s != null) {
                            System.out.print("Enter new Name: ");
                            s.setFullName(getStringInput(scanner));
                            System.out.println("Staff name updated successfully.");
                        } else { System.out.println("Staff not found."); }
                    } else {
                        Patient p = memory.searchPatientById(editId);
                        if (p != null) {
                            System.out.print("Enter new Name: ");
                            p.setFullName(getStringInput(scanner));
                            System.out.println("Patient name updated successfully.");
                        } else { System.out.println("Patient not found."); }
                    }
                    break;
                case 4:
                    System.out.print("Enter Staff ID to Fire/Dismiss: ");
                    String fireId = getStringInput(scanner);
                    boolean fired = false;

                    for (int i = 0; i < memory.staffDatabase.size(); i++) {
                        Staff target = memory.staffDatabase.get(i);
                        if (target.getStaffId().equalsIgnoreCase(fireId)) {

                            if (target instanceof Admin) {
                                System.out.println("ERROR: Cannot fire a System Manager!");
                                fired = true; break;
                            }
                            if (target instanceof Doctor) {
                                if (((Doctor) target).getQueueCount() > 0) {
                                    System.out.println("ERROR: Cannot fire this Doctor. They have patients currently in queue!");
                                    fired = true; break;
                                }
                            }

                            memory.staffDatabase.remove(i);
                            System.out.println("Staff member " + target.getFullName() + " has been successfully fired.");
                            fired = true; break;
                        }
                    }
                    if (!fired) System.out.println("Staff not found.");
                    break;
                case 5:
                    System.out.print("Enter Patient ID: ");
                    String pidToSearch = getStringInput(scanner);
                    Patient pToSearch = memory.searchPatientById(pidToSearch);

                    if (pToSearch != null) {
                        System.out.println("\nPatient Info:");
                        System.out.println("Name: " + pToSearch.getFullName());
                        System.out.println("National ID: " + pToSearch.getNationalId());
                        System.out.println("Phone: " + pToSearch.getPhoneNumber());
                        pToSearch.printAllRecords();
                    } else {
                        System.out.println("Patient not found.");
                    }
                    break;
                case 6:
                    System.out.print("Enter Patient ID to discharge and delete: ");
                    String pidToDischarge = getStringInput(scanner);
                    boolean found = false;

                    for (int i = 0; i < memory.patientDatabase.size(); i++) {
                        Patient targetPatient = memory.patientDatabase.get(i);
                        if (targetPatient.getPatientId().equalsIgnoreCase(pidToDischarge)) {
                            if (targetPatient.getPendingBill() > 0) {
                                System.out.println("ERROR: Patient owes $" + targetPatient.getPendingBill() + ". Pay first.");
                                found = true; break;
                            }
                            if (targetPatient.getCurrentStatus() == PatientStatus.WAITING_FOR_DOCTOR ||
                                    targetPatient.getCurrentStatus() == PatientStatus.IN_TREATMENT) {
                                System.out.println("ERROR: Patient is in Doctor's queue.");
                                found = true; break;
                            }
                            targetPatient.prepareForGarbageCollection();
                            memory.patientDatabase.remove(i);
                            System.out.println("Patient discharged successfully.");
                            found = true; break;
                        }
                    }
                    if (!found) System.out.println("Patient not found.");
                    break;
                case 7:
                    memory.systemDate.advanceOneDay();
                    System.out.println("Date advanced. Current Date is: " + memory.systemDate.getFullDate());
                    break;
                case 8:
                    active = false;
                    break;
            }
        }
    }

    private static void runReceptionistModule(Scanner scanner, ClinicMemory memory) {
        boolean active = true;
        while (active) {
            System.out.println("\n--- RECEPTION DASHBOARD ---");
            System.out.println("1. Register New Patient");
            System.out.println("2. Assign Doctor to Patient");
            System.out.println("3. Back");
            System.out.print("Choice: ");

            int choice = getIntInput(scanner, 1, 3);

            switch (choice) {
                case 1:
                    System.out.print("National ID: ");
                    String nid = getStringInput(scanner);
                    boolean isDuplicatePat = false;
                    for (Patient p : memory.patientDatabase) {
                        if (p.getNationalId().equalsIgnoreCase(nid)) {
                            System.out.println("ERROR: Patient exists! (ID: " + p.getPatientId() + ")");
                            isDuplicatePat = true; break;
                        }
                    }
                    if (isDuplicatePat) break;

                    System.out.print("Patient Name: ");
                    String name = getStringInput(scanner);
                    System.out.print("Age (0 to 120): ");
                    int age = getIntInput(scanner, 0, 120);
                    System.out.print("Phone: ");
                    String phone = getStringInput(scanner);

                    Patient newPat = new Patient(name, nid, age, phone);
                    memory.registerPatient(newPat);
                    System.out.println("Registered! Patient ID: " + newPat.getPatientId());
                    break;
                case 2:
                    System.out.print("Enter Patient ID: ");
                    String pid = getStringInput(scanner);
                    Patient p = memory.searchPatientById(pid);

                    if (p != null && (p.getCurrentStatus() == PatientStatus.NEW_ARRIVAL || p.getCurrentStatus() == PatientStatus.CLEARED_AND_FINISHED)) {
                        boolean isDoctorAvailable = false;
                        for (Staff staff : memory.staffDatabase) {
                            if (staff instanceof Doctor) { isDoctorAvailable = true; break; }
                        }
                        if (!isDoctorAvailable) {
                            System.out.println("No doctors available.");
                            break;
                        }
                        System.out.println("Available Doctors:");
                        for (Staff staff : memory.staffDatabase) {
                            if (staff instanceof Doctor) {
                                Doctor d = (Doctor) staff;
                                System.out.println(d.getStaffId() + " - " + d.getFullName() + " (" + d.getSpecialty() + ")");
                            }
                        }
                        System.out.print("Enter Doctor ID: ");
                        String docId = getStringInput(scanner);
                        Staff s = memory.searchStaffById(docId);

                        if (s != null && s instanceof Doctor) {
                            Doctor doctor = (Doctor) s;
                            p.setAssignedDoctor(doctor);
                            p.addCharge(doctor.getConsultationFee());
                            p.setCurrentStatus(PatientStatus.WAITING_FOR_PAYMENT);
                            System.out.println("Assigned. Patient must pay $" + doctor.getConsultationFee() + " at Cashier.");
                        } else {
                            System.out.println("Invalid Doctor ID.");
                        }
                    } else {
                        System.out.println("Patient not found or status unresolved.");
                    }
                    break;
                case 3:
                    active = false;
                    break;
            }
        }
    }

    private static void runDoctorModule(Scanner scanner, ClinicMemory memory) {
        System.out.print("Enter your Doctor ID: ");
        String docId = getStringInput(scanner);
        Staff s = memory.searchStaffById(docId);

        if (s == null || !(s instanceof Doctor)) {
            System.out.println("Authentication Failed.");
            return;
        }
        Doctor me = (Doctor) s;
        boolean active = true;

        while (active) {
            System.out.println("\n--- DOCTOR DASHBOARD: " + me.getFullName() + " ---");
            System.out.println("1. View My Queue");
            System.out.println("2. Call Next Patient");
            System.out.println("3. View Patient's LATEST Record");
            System.out.println("4. View My Payment History");
            System.out.println("5. Back");
            System.out.print("Choice: ");

            int choice = getIntInput(scanner, 1, 5);

            switch (choice) {
                case 1:
                    System.out.println("Patients waiting: " + me.getQueueCount());
                    break;
                case 2:
                    Patient p = me.getNextPatient();
                    if (p != null) {
                        p.setCurrentStatus(PatientStatus.IN_TREATMENT);
                        System.out.println("Treating: " + p.getFullName());

                        System.out.print("Enter Symptoms: ");
                        String sym = getStringInput(scanner);
                        MedicalRecord record = new MedicalRecord(memory.systemDate.getFullDate(), sym);

                        System.out.print("Enter Diagnosis: ");
                        String diag = getStringInput(scanner);
                        System.out.print("Enter Prescription: ");
                        String pres = getStringInput(scanner);

                        record.setDoctorNotes(diag, pres);
                        p.addNewMedicalRecord(record);

                        me.finishPatientVisit();
                        p.setCurrentStatus(PatientStatus.CLEARED_AND_FINISHED);
                        System.out.println("Visit completed.");
                    } else {
                        System.out.println("Queue is empty.");
                    }
                    break;
                case 3:
                    System.out.print("Enter Patient ID to view their most recent visit: ");
                    String pIdHist = getStringInput(scanner);
                    Patient patHist = memory.searchPatientById(pIdHist);

                    if (patHist != null) {
                        MedicalRecord latest = patHist.getLatestRecord();
                        if (latest != null) {
                            System.out.println(">>> LATEST RECORD FOR " + patHist.getFullName() + " <<<");
                            System.out.println(latest.getFullRecord());
                        } else {
                            System.out.println("This patient has no previous medical records.");
                        }
                    } else {
                        System.out.println("Patient not found.");
                    }
                    break;
                case 4:
                    me.showPaymentHistory();
                    break;
                case 5:
                    active = false;
                    break;
            }
        }
    }

    private static void runCashierModule(Scanner scanner, ClinicMemory memory) {
        boolean active = true;
        while (active) {
            System.out.println("\n--- CASHIER DASHBOARD ---");
            System.out.println("1. Process Patient Payment");
            System.out.println("2. Execute Payroll (Pay Salaries)");
            System.out.println("3. Back");
            System.out.print("Choice: ");

            int choice = getIntInput(scanner, 1, 3);

            switch (choice) {
                case 1:
                    System.out.print("Enter Patient ID: ");
                    String pid = getStringInput(scanner);
                    Patient p = memory.searchPatientById(pid);

                    if (p != null && p.getCurrentStatus() == PatientStatus.WAITING_FOR_PAYMENT) {
                        System.out.println("Pending Bill: $" + p.getPendingBill());
                        System.out.print("Enter cash received: $");
                        double cash = getDoubleInput(scanner, 0.0);

                        if (cash >= p.getPendingBill()) {
                            System.out.println("Change given: $" + (cash - p.getPendingBill()));
                            p.clearBill();
                            p.setCurrentStatus(PatientStatus.WAITING_FOR_DOCTOR);
                            p.getAssignedDoctor().addPatientToQueue(p);
                            System.out.println("Payment done. Added to doctor's queue.");
                        } else {
                            double rem = p.getPendingBill() - cash;
                            p.clearBill();
                            p.addCharge(rem);
                            System.out.println("Partial payment. Remaining Bill: $" + p.getPendingBill());
                        }
                    } else {
                        System.out.println("No pending payments for this ID.");
                    }
                    break;
                case 2:
                    int currentYear = memory.systemDate.getYear();
                    int currentMonth = memory.systemDate.getMonth();
                    String monthName = memory.systemDate.getMonthName();

                    System.out.println("Processing Payroll for: " + monthName + " " + currentYear);

                    for (Staff staff : memory.staffDatabase) {
                        double salary = staff.calculateFinalSalary();
                        boolean isPaid = staff.addPaymentRecord(currentYear, currentMonth, monthName, salary);

                        if (isPaid) {
                            System.out.println("Paid $" + salary + " to " + staff.getFullName());
                            if (staff instanceof Doctor doc) doc.resetMonthlyVisits();
                        } else {
                            System.out.println("Skipped: " + staff.getFullName() + " (Already paid)");
                        }
                    }
                    break;
                case 3:
                    active = false;
                    break;
            }
        }
    }
}