package models;
import java.io.Serializable;

public abstract class Person implements Serializable {
    private String fullName;
    private String nationalId;
    private int age;
    private String phoneNumber;

    public Person(String fullName, String nationalId, int age, String phoneNumber) {
        this.fullName = fullName;
        this.nationalId = nationalId;
        this.age = age;
        this.phoneNumber = phoneNumber;
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public final String getNationalId() { return nationalId; }
    public int getAge() { return age; }
    public String getPhoneNumber() { return phoneNumber; }
}