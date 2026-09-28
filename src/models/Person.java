package models;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

public abstract class Person implements Serializable {
    private static final long serialVersionUID = 3L;

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]{2,64}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[\\+0-9\\-\\s()]{3,30}$");

    private final String nationalId;
    private String fullName;
    private Integer age;
    private String contactNumber;

    protected Person(String nationalId, String fullName, Integer age, String contactNumber) {
        this.nationalId = validateId(nationalId);
        this.setFullName(fullName);
        this.setAge(age);
        this.setContactNumber(contactNumber);
    }

    protected Person(String newNationalId, Person sourcePerson) {
        Objects.requireNonNull(sourcePerson, "Source person cannot be null.");

        this.nationalId = validateId(newNationalId);
        this.setFullName(sourcePerson.getFullName());
        this.setAge(sourcePerson.getAge());
        this.setContactNumber(sourcePerson.getContactNumber());
    }

    private String validateId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Identity cannot be null or empty.");
        }
        String trimmedId = id.trim();
        if (!ID_PATTERN.matcher(trimmedId).matches()) {
            throw new IllegalArgumentException("Invalid Identity format. Must be 2-64 characters.");
        }
        return trimmedId;
    }

    public abstract Person cloneWithNewId(String newNationalId);

    public String getNationalId() {
        return nationalId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name cannot be null or empty.");
        }
        this.fullName = fullName.trim();
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        if (age != null && (age < 0 || age > 200)) {
            throw new IllegalArgumentException("Age must be between 0 and 200, or null for unknown records.");
        }
        this.age = age;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        if (contactNumber == null || contactNumber.trim().isEmpty()) {
            this.contactNumber = "UNKNOWN";
            return;
        }
        String trimmedContact = contactNumber.trim();
        if (!PHONE_PATTERN.matcher(trimmedContact).matches()) {
            throw new IllegalArgumentException("Invalid contact number format.");
        }
        this.contactNumber = trimmedContact;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person person = (Person) o;
        return nationalId.equals(person.nationalId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nationalId);
    }

    @Override
    public String toString() {
        return String.format("ID: %s | Name: %s | Age: %s | Contact: %s",
                nationalId, fullName, (age == null ? "N/A" : age), contactNumber);
    }
}