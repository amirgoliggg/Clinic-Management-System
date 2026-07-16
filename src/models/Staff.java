package models;
import java.util.ArrayList;

public abstract class Staff extends Person {
    private static int globalStaffId = 5000;

    public static void setGlobalStaffId(int id) { globalStaffId = id; }

    private int lastPaidYear = 0;
    private int lastPaidMonthNumber = 0;

    private String staffId;
    private double baseSalary;
    private ArrayList<String> paymentHistory;

    public Staff(String fullName, String nationalId, int age, String phoneNumber, double baseSalary) {
        super(fullName, nationalId, age, phoneNumber);
        this.staffId = "EMP-" + (globalStaffId++);
        this.baseSalary = baseSalary;
        this.paymentHistory = new ArrayList<>();
    }

    public String getStaffId() { return staffId; }
    public double getBaseSalary() { return baseSalary; }

    public boolean addPaymentRecord(int currentYear, int currentMonthNumber, String monthName, double amount) {
        if (this.lastPaidYear == currentYear && this.lastPaidMonthNumber == currentMonthNumber) {
            return false;
        }
        paymentHistory.add("Year: " + currentYear + " | Month: " + monthName + " - Paid: $" + amount);
        this.lastPaidYear = currentYear;
        this.lastPaidMonthNumber = currentMonthNumber;
        return true;
    }

    public void showPaymentHistory() {
        System.out.println("--- Payment History for " + getFullName() + " ---");
        if (paymentHistory.isEmpty()) {
            System.out.println("No payments recorded yet.");
        } else {
            for (String record : paymentHistory) {
                System.out.println(record);
            }
        }
    }

    public int getPaymentCount() { return paymentHistory.size(); }
    public ArrayList<String> getPaymentHistory() { return paymentHistory; }

    public abstract double calculateFinalSalary();
    public abstract String getJobTitle();
}