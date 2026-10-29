package com.clinic.models;

import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

@Entity
@Table(name = "persons")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Person implements Serializable {

    @Serial
    private static final long serialVersionUID = 3L;

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]{1,64}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+0-9\\-\\s()]{3,30}$");

    private static final String DEFAULT_CONTACT_NUMBER = "09120000000";
    private static final int MIN_AGE = 0;
    private static final int MAX_AGE = 200;

    @Id
    @Column(name = "national_id", length = 64, nullable = false, updatable = false)
    private String nationalId;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "age")
    private Integer age;

    @Column(name = "contact_number", length = 30)
    private String contactNumber;

    // ==================== Constructors ====================

    protected Person() {
        // Required by JPA
    }

    protected Person(String nationalId, String fullName, Integer age, String contactNumber) {
        this.nationalId = validateAndFormatId(nationalId);
        this.setFullName(fullName);
        this.setAge(age);
        this.setContactNumber(contactNumber);
    }

    protected Person(String newNationalId, Person sourcePerson) {
        Objects.requireNonNull(sourcePerson, "Source person cannot be null.");
        this.nationalId = validateAndFormatId(newNationalId);
        this.setFullName(sourcePerson.getFullName());
        this.setAge(sourcePerson.getAge());
        this.setContactNumber(sourcePerson.getContactNumber());
    }

    // ==================== Domain & Validation Methods ====================

    public abstract Person cloneWithNewId(String newNationalId);

    private static String validateAndFormatId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Identity cannot be null or empty.");
        }
        String trimmedId = id.trim();
        if (!ID_PATTERN.matcher(trimmedId).matches()) {
            throw new IllegalArgumentException("Invalid Identity format.");
        }
        return trimmedId;
    }

    // ==================== Getters & Setters ====================

    public String getNationalId() {
        return nationalId;
    }

    public Long getVersion() {
        return version;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name cannot be null or empty.");
        }
        this.fullName = fullName.trim();
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        if (age != null && (age < MIN_AGE || age > MAX_AGE)) {
            throw new IllegalArgumentException("Age must be between " + MIN_AGE + " and " + MAX_AGE + ".");
        }
        this.age = age;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        if (contactNumber == null || contactNumber.isBlank()) {
            this.contactNumber = DEFAULT_CONTACT_NUMBER;
            return;
        }
        String trimmedContact = contactNumber.trim();
        if (!PHONE_PATTERN.matcher(trimmedContact).matches()) {
            throw new IllegalArgumentException("Invalid contact number format.");
        }
        this.contactNumber = trimmedContact;
    }

    // ==================== Equals & HashCode ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Person person)) return false;
        return nationalId != null && Objects.equals(nationalId, person.getNationalId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(nationalId);
    }
}