package models;
import java.util.ArrayList;

public class Doctor extends Staff {
    private String specialty;
    private int consultationFee;
    private int monthlyVisits = 0;

    private ArrayList<Patient> activeQueue;
    private ArrayList<Patient> visitedHistory;

    public Doctor(String fullName, String nationalId, int age, String phoneNumber, double baseSalary, String specialty, int consultationFee) {
        super(fullName, nationalId, age, phoneNumber, baseSalary);
        this.specialty = specialty;
        this.consultationFee = consultationFee;
        this.activeQueue = new ArrayList<>();
        this.visitedHistory = new ArrayList<>();
    }

    public String getSpecialty() { return specialty; }
    public int getConsultationFee() { return consultationFee; }

    public boolean addPatientToQueue(Patient patient) {
        activeQueue.add(patient);
        return true;
    }

    public Patient getNextPatient() {
        if (!activeQueue.isEmpty()) {
            return activeQueue.get(0);
        }
        return null;
    }

    public void finishPatientVisit() {
        if (!activeQueue.isEmpty()) {
            Patient finishedPatient = activeQueue.remove(0);
            visitedHistory.add(finishedPatient);
            monthlyVisits++;
        }
    }

    public int getQueueCount() { return activeQueue.size(); }

    @Override
    public double calculateFinalSalary() {
        return getBaseSalary() + (monthlyVisits * (consultationFee * 0.20));
    }

    @Override
    public String getJobTitle() {
        return "Doctor - " + specialty;
    }

    public void resetMonthlyVisits() {
        this.monthlyVisits = 0;
    }
}