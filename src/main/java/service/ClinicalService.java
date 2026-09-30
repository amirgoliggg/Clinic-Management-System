package service;

import exceptions.EntityNotFoundException;
import models.Doctor;
import models.MedicalRecord;
import models.Patient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.MedicalRecordRepository;
import repository.PatientRepository;
import system.SystemClock;

import java.util.List;

@Service
public class ClinicalService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final PatientRepository patientRepository;

    public ClinicalService(MedicalRecordRepository medicalRecordRepository, PatientRepository patientRepository) {
        this.medicalRecordRepository = medicalRecordRepository;
        this.patientRepository = patientRepository;
    }

    @Transactional
    public MedicalRecord conductConsultation(Doctor doctor, String patientNationalId, String diagnosis, String prescription, String notes) {
        Patient patient = patientRepository.findByNationalId(patientNationalId)
                .orElseThrow(() -> EntityNotFoundException.patientNotFound(patientNationalId));

        MedicalRecord record = doctor.conductConsultation(patient, diagnosis, prescription, notes);

        medicalRecordRepository.save(record);
        patientRepository.save(patient);

        return record;
    }

    @Transactional
    public MedicalRecord amendMedicalRecord(String recordId, String attendingDoctorId, String updatedDiagnosis, String updatedPrescription, String reason) {
        MedicalRecord originalRecord = medicalRecordRepository.findById(recordId)
                .orElseThrow(() -> EntityNotFoundException.medicalRecordNotFound(recordId));

        MedicalRecord amendedRecord = originalRecord.createAmendment(
                attendingDoctorId,
                updatedDiagnosis,
                updatedPrescription,
                reason,
                SystemClock.now()
        );

        return medicalRecordRepository.save(amendedRecord);
    }

    @Transactional(readOnly = true)
    public List<MedicalRecord> getRecordsByPatient(String patientNationalId) {
        return medicalRecordRepository.findByPatientNationalId(patientNationalId);
    }
}