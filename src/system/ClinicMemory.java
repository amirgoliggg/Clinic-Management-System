package system;
import models.*;
import java.io.*;
import java.util.Scanner;
import java.util.ArrayList;

public class ClinicMemory {
    public JalaliDate systemDate;
    public ArrayList<Staff> staffDatabase;
    public ArrayList<Patient> patientDatabase;

    public ClinicMemory() {
        this.systemDate = new JalaliDate(1405, 1, 1);
        this.staffDatabase = new ArrayList<>();
        this.patientDatabase = new ArrayList<>();
    }

    public void registerStaff(Staff staffMember) { staffDatabase.add(staffMember); }
    public void registerPatient(Patient patient) { patientDatabase.add(patient); }

    public Staff searchStaffById(String id) {
        for (Staff s : staffDatabase) {
            if (s.getStaffId().equalsIgnoreCase(id.trim())) return s;
        }
        return null;
    }

    public Patient searchPatientById(String id) {
        for (Patient p : patientDatabase) {
            if (p.getPatientId().equalsIgnoreCase(id.trim())) return p;
        }
        return null;
    }

    public void loadDatabase() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("ClinicDB.dat"))) {
            this.staffDatabase = (ArrayList<Staff>) ois.readObject();
            this.patientDatabase = (ArrayList<Patient>) ois.readObject();
            this.systemDate = (JalaliDate) ois.readObject();

            int maxStaff = 4999;
            for (Staff s : staffDatabase) {
                int idNum = Integer.parseInt(s.getStaffId().replace("EMP-", ""));
                if (idNum > maxStaff) maxStaff = idNum;
            }
            Staff.setGlobalStaffId(maxStaff + 1);

            int maxPat = 0;
            for (Patient p : patientDatabase) {
                int idNum = Integer.parseInt(p.getPatientId().replace("PAT-", ""));
                if (idNum > maxPat) maxPat = idNum;
            }
            Patient.setPatientIdCounter(maxPat + 1);

            System.out.println("System Database loaded successfully!");
        } catch (Exception e) {
            System.out.println("No existing database found. Starting fresh...");
        }
    }

    public void saveDatabase() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("ClinicDB.dat"))) {
            oos.writeObject(this.staffDatabase);
            oos.writeObject(this.patientDatabase);
            oos.writeObject(this.systemDate);
        } catch (Exception e) {
            System.out.println("Critical Error: Could not save binary database.");
        }
    }

    public void exportSystemReportToFile() {
        try (PrintWriter writer = new PrintWriter(new File("ClinicGeneralReport.txt"))) {
            writer.println("================================================================================");
            writer.println("                   CLINIC MASTER SYSTEM REPORT (DETAILED)                       ");
            writer.println("                   Date: " + systemDate.getFullDate());
            writer.println("================================================================================");

            writer.println("\n>>> [1] COMPREHENSIVE STAFF ROSTER <<<");
            for (Staff s : staffDatabase) {
                writer.println("------------------------------------------------");
                writer.println("Staff ID: " + s.getStaffId() + " | Name: " + s.getFullName() + " | Role: " + s.getJobTitle());
            }

            writer.println("\n>>> [2] COMPREHENSIVE PATIENT DATABASE <<<");
            for (Patient p : patientDatabase) {
                writer.println("------------------------------------------------");
                writer.println("Patient ID: " + p.getPatientId() + " | Name: " + p.getFullName() + " | Status: " + p.getCurrentStatus());
            }
            writer.println("================================================================================\n");
        } catch (Exception e) {
            System.out.println("Error generating report file.");
        }
    }

    public void readReportFromFile() {
        File file = new File("ClinicGeneralReport.txt");
        if (!file.exists()) {
            System.out.println("Report file does not exist. Please shutdown the system properly to save data first.");
            return;
        }

        try (Scanner fileScanner = new Scanner(file)) {
            while (fileScanner.hasNextLine()) {
                System.out.println(fileScanner.nextLine());
            }
        } catch (Exception e) {
            System.out.println("Error reading file from hard drive.");
        }
    }
}