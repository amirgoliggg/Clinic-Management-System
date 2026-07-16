package models;
public class Nurse extends Staff {
    private String department;
    public Nurse(String fullName, String nationalId, int age, String phoneNumber, double baseSalary, String department) {
        super(fullName, nationalId, age, phoneNumber, baseSalary);
        this.department = department;
    }
    @Override
    public double calculateFinalSalary() { return getBaseSalary() + 200; }
    @Override
    public String getJobTitle() { return "Nurse (" + department + ")"; }
}