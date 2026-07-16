package models;
public class Cashier extends Staff {
    public Cashier(String fullName, String nationalId, int age, String phoneNumber, double baseSalary) {
        super(fullName, nationalId, age, phoneNumber, baseSalary);
    }
    @Override
    public double calculateFinalSalary() { return getBaseSalary() + 150; }
    @Override
    public String getJobTitle() { return "Cashier"; }
}