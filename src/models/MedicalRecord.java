package models;
import java.io.Serializable;
public class MedicalRecord implements Serializable {
    private String visitDate;
    private String symptoms;
    private String diagnosis;
    private String prescription;

    public MedicalRecord(String visitDate, String symptoms) {
        this.visitDate = visitDate;
        this.symptoms = symptoms;
        this.diagnosis = "Not diagnosed yet";
        this.prescription = "No prescription";
    }

    public void setDoctorNotes(String diagnosis, String prescription) {
        this.diagnosis = diagnosis;
        this.prescription = prescription;
    }

    public String getFullRecord() {
        return "Date: " + visitDate + " | Symptoms: " + symptoms +
                " | Diagnosis: " + diagnosis + " | Prescription: " + prescription;
    }
}