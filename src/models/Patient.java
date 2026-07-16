package models;
import enums.PatientStatus;
import java.util.ArrayList;

public class Patient extends Person {
    private static int patientIdCounter = 1;

    public static void setPatientIdCounter(int id) { patientIdCounter = id; }

    private String patientId;
    private PatientStatus currentStatus;
    private Doctor assignedDoctor;

    private ArrayList<MedicalRecord> records;
    private double pendingBill;

    public Patient(String fullName, String nationalId, int age, String phoneNumber) {
        super(fullName, nationalId, age, phoneNumber);
        this.patientId = "PAT-" + (patientIdCounter++);
        this.currentStatus = PatientStatus.NEW_ARRIVAL;
        this.records = new ArrayList<>();
        this.pendingBill = 0.0;
    }

    public String getPatientId() { return patientId; }
    public PatientStatus getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(PatientStatus currentStatus) { this.currentStatus = currentStatus; }

    public Doctor getAssignedDoctor() { return assignedDoctor; }
    public void setAssignedDoctor(Doctor assignedDoctor) { this.assignedDoctor = assignedDoctor; }

    public double getPendingBill() { return pendingBill; }
    public void addCharge(double amount) { this.pendingBill += amount; }
    public void clearBill() { this.pendingBill = 0.0; }

    public void addNewMedicalRecord(MedicalRecord record) {
        records.add(record);
    }

    public MedicalRecord getLatestRecord() {
        if (!records.isEmpty()) {
            return records.get(records.size() - 1);
        }
        return null;
    }

    public void printAllRecords() {
        System.out.println("--- Medical Records for " + getFullName() + " ---");
        for (int i = 0; i < records.size(); i++) {
            System.out.println("[" + (i+1) + "] " + records.get(i).getFullRecord());
        }
    }

    public void prepareForGarbageCollection() {
        this.assignedDoctor = null;
        this.records.clear();
    }

    public int getRecordCount() { return records.size(); }
    public ArrayList<MedicalRecord> getRecords() { return records; }
}