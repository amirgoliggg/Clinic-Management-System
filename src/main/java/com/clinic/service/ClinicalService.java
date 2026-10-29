package com.clinic.service;

import com.clinic.exceptions.EntityNotFoundException;
import com.clinic.exceptions.InsufficientPermissionException;
import com.clinic.models.Doctor;
import com.clinic.models.MedicalRecord;
import com.clinic.models.Nurse;
import com.clinic.models.Patient;
import com.clinic.models.Staff;
import com.clinic.repository.MedicalRecordRepository;
import com.clinic.repository.PatientRepository;
import com.clinic.repository.StaffRepository;
import com.clinic.system.SystemClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class ClinicalService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final PatientRepository patientRepository;
    private final StaffRepository staffRepository;
    private final AuditService auditService;

    public ClinicalService(MedicalRecordRepository medicalRecordRepository,
                           PatientRepository patientRepository,
                           StaffRepository staffRepository,
                           AuditService auditService) {
        this.medicalRecordRepository = medicalRecordRepository;
        this.patientRepository = patientRepository;
        this.staffRepository = staffRepository;
        this.auditService = auditService;
    }

    @Transactional
    public MedicalRecord conductConsultation(Doctor doctor, String patientNationalId, String diagnosis, String prescription, String notes) {
        Objects.requireNonNull(doctor, "پزشک نمی‌تواند خالی باشد.");

        Doctor managedDoctor = resolveManagedDoctor(doctor);
        Patient patient = patientRepository.findByNationalIdForUpdate(patientNationalId)
                .orElseThrow(() -> EntityNotFoundException.patientNotFound(patientNationalId));

        MedicalRecord record = managedDoctor.conductConsultation(patient, diagnosis, prescription, notes);

        medicalRecordRepository.save(record);
        patientRepository.save(patient);
        staffRepository.save(managedDoctor);

        auditService.logCurrentActorAction("DOCTOR", "CLINICAL_CONSULTATION", "SUCCESS",
                "ثبت ویزیت پزشکی برای بیمار " + patientNationalId + " توسط دکتر " + managedDoctor.getFullName());

        return record;
    }

    @Transactional
    public MedicalRecord performNurseTriage(Nurse nurse, String patientNationalId, String bloodPressure,
                                            double bodyTemperatureCelsius, int heartRateBpm,
                                            String chiefComplaint, String nursingNotes) {
        Objects.requireNonNull(nurse, "پرستار نمی‌تواند خالی باشد.");

        Nurse managedNurse = resolveManagedNurse(nurse);
        Patient patient = patientRepository.findByNationalIdForUpdate(patientNationalId)
                .orElseThrow(() -> EntityNotFoundException.patientNotFound(patientNationalId));

        MedicalRecord record = managedNurse.performTriage(
                patient, bloodPressure, bodyTemperatureCelsius, heartRateBpm, chiefComplaint, nursingNotes
        );

        medicalRecordRepository.save(record);
        patientRepository.save(patient);
        staffRepository.save(managedNurse);

        auditService.logCurrentActorAction("NURSE", "PERFORM_TRIAGE", "SUCCESS",
                "ثبت تریاژ و علائم حیاتی برای بیمار " + patientNationalId + " توسط پرستار " + managedNurse.getFullName());

        return record;
    }

    @Transactional
    public MedicalRecord administerNursingCare(Nurse nurse, String patientNationalId, String careProcedure,
                                               BigDecimal serviceFee, String careNotes) {
        Objects.requireNonNull(nurse, "پرستار نمی‌تواند خالی باشد.");

        Nurse managedNurse = resolveManagedNurse(nurse);
        Patient patient = patientRepository.findByNationalIdForUpdate(patientNationalId)
                .orElseThrow(() -> EntityNotFoundException.patientNotFound(patientNationalId));

        MedicalRecord record = managedNurse.administerNursingCare(patient, careProcedure, serviceFee, careNotes);

        medicalRecordRepository.save(record);
        patientRepository.save(patient);
        staffRepository.save(managedNurse);

        auditService.logCurrentActorAction("NURSE", "NURSING_CARE", "SUCCESS",
                "ثبت خدمت پرستاری (" + careProcedure + ") برای بیمار " + patientNationalId);

        return record;
    }

    @Transactional
    public MedicalRecord amendMedicalRecord(String recordId, String attendingDoctorId, String updatedDiagnosis, String updatedPrescription, String reason) {
        MedicalRecord originalRecord = medicalRecordRepository.findById(recordId)
                .orElseThrow(() -> EntityNotFoundException.medicalRecordNotFound(recordId));

        if (!originalRecord.verifyIntegrity()) {
            throw new SecurityException("هش یکپارچگی پرونده پزشکی اصلی مخدوش شده است.");
        }
        if (originalRecord.getRecordStatus() == MedicalRecord.RecordStatus.CANCELLED) {
            throw new IllegalStateException("امکان اصلاح پرونده پزشکی باطل‌شده وجود ندارد.");
        }

        MedicalRecord amendedRecord = originalRecord.createAmendment(
                attendingDoctorId,
                updatedDiagnosis,
                updatedPrescription,
                reason,
                SystemClock.now()
        );

        MedicalRecord saved = medicalRecordRepository.save(amendedRecord);

        auditService.logCurrentActorAction("DOCTOR", "AMEND_MEDICAL_RECORD", "SUCCESS",
                "اصلاح پرونده پزشکی #" + recordId + " | علت: " + reason);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<MedicalRecord> getRecordsByPatient(String patientNationalId) {
        return medicalRecordRepository.findByPatientNationalId(patientNationalId);
    }

    @Transactional(readOnly = true)
    public boolean verifyAllRecordsIntegrity() {
        return medicalRecordRepository.findAll().stream().allMatch(MedicalRecord::verifyIntegrity);
    }

    private Doctor resolveManagedDoctor(Doctor doctor) {
        Staff found = staffRepository.findByNationalId(doctor.getNationalId()).orElse(doctor);
        if (!(found instanceof Doctor managedDoctor)) {
            throw InsufficientPermissionException.clinicalRoleRequired(doctor.getUsername(), "CONDUCT_CONSULTATION");
        }
        return managedDoctor;
    }

    private Nurse resolveManagedNurse(Nurse nurse) {
        Staff found = staffRepository.findByNationalId(nurse.getNationalId()).orElse(nurse);
        if (!(found instanceof Nurse managedNurse)) {
            throw InsufficientPermissionException.clinicalRoleRequired(nurse.getUsername(), "NURSING_ACTION");
        }
        return managedNurse;
    }
}