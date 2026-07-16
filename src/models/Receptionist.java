package models;
public class Receptionist extends Staff {
    public Receptionist(String fullName, String nationalId, int age, String phoneNumber, double baseSalary) {
        super(fullName, nationalId, age, phoneNumber, baseSalary);
    }
    @Override
    public double calculateFinalSalary() { return getBaseSalary() + 50; }
    @Override
    public String getJobTitle() { return "Receptionist"; }
}