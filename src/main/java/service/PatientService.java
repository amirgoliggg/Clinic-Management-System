package service;

import enums.PatientStatus;
import exceptions.EntityNotFoundException;
import exceptions.InvalidWorkflowException;
import models.Patient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.PatientRepository;

import java.util.List;
import java.util.UUID;

@Service
public class PatientService {

    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @Transactional
    public Patient registerNewPatient(Patient patient) {
        if (patientRepository.existsByNationalId(patient.getNationalId())) {
            throw new IllegalArgumentException("بیماری با کد ملی " + patient.getNationalId() + " از قبل ثبت شده است.");
        }
        if (patient.getPatientId() == null || patient.getPatientId().isBlank()) {
            patient.setPatientId("PAT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        patient.setStatus(PatientStatus.WAITING_FOR_DOCTOR);
        return patientRepository.save(patient);
    }

    @Transactional(readOnly = true)
    public Patient getPatientByNationalId(String nationalId) {
        return patientRepository.findByNationalId(nationalId)
                .orElseThrow(() -> EntityNotFoundException.patientNotFound(nationalId));
    }

    @Transactional
    public void checkInExistingPatient(String nationalId) {
        Patient patient = getPatientByNationalId(nationalId);
        if (patient.getCurrentStatus() == PatientStatus.IN_VISIT ||
                patient.getCurrentStatus() == PatientStatus.IN_TREATMENT ||
                patient.getCurrentStatus() == PatientStatus.WAITING_FOR_DOCTOR) {
            throw new IllegalStateException("بیمار در حال حاضر در صف انتظار یا در حال معاینه است.");
        }
        patient.setStatus(PatientStatus.WAITING_FOR_DOCTOR);
        patientRepository.save(patient);
    }

    @Transactional
    public void dischargePatient(String nationalId) {
        Patient patient = getPatientByNationalId(nationalId);
        if (patient.getCurrentStatus() == PatientStatus.DISCHARGED || patient.getCurrentStatus() == PatientStatus.CLEARED_AND_FINISHED) {
            throw new IllegalStateException("بیمار با شناسه " + patient.getNationalId() + " پیش‌تر ترخیص شده است.");
        }
        if (patient.getCurrentStatus() != PatientStatus.PAYMENT_COMPLETED) {
            throw InvalidWorkflowException.dischargeWithoutPayment(nationalId, patient.getCurrentStatus());
        }
        patient.setStatus(PatientStatus.DISCHARGED);
        patientRepository.save(patient);
    }

    @Transactional
    public void cancelAdmission(String nationalId) {
        Patient patient = getPatientByNationalId(nationalId);
        if (patient.getCurrentStatus() == PatientStatus.DISCHARGED ||
                patient.getCurrentStatus() == PatientStatus.CLEARED_AND_FINISHED ||
                patient.getCurrentStatus() == PatientStatus.PAYMENT_COMPLETED) {
            throw new IllegalStateException("امکان لغو پذیرش برای بیماری که تسویه حساب کرده یا روند درمان آن پایان یافته وجود ندارد.");
        }
        patient.setStatus(PatientStatus.CANCELLED);
        patientRepository.save(patient);
    }

    @Transactional(readOnly = true)
    public List<Patient> getPatientsByStatus(PatientStatus status) {
        return patientRepository.findByCurrentStatus(status);
    }

    @Transactional(readOnly = true)
    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }
}