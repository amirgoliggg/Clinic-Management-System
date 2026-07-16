package models;
public class Admin extends Staff {
    public Admin(String fullName, String nationalId, int age, String phoneNumber, double baseSalary) {
        super(fullName, nationalId, age, phoneNumber, baseSalary);
    }
    @Override
    public double calculateFinalSalary() { return getBaseSalary() + 1000; }
    @Override
    public String getJobTitle() { return "System Administrator"; }
}