package com.clinic.service;

import com.clinic.enums.PatientStatus;
import com.clinic.exceptions.EntityNotFoundException;
import com.clinic.exceptions.InvalidWorkflowException;
import com.clinic.models.Patient;
import com.clinic.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class PatientService {

    private static final String PATIENT_ID_PREFIX = "PAT-";

    private final PatientRepository patientRepository;
    private final AuditService auditService;

    public PatientService(PatientRepository patientRepository, AuditService auditService) {
        this.patientRepository = patientRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Patient registerNewPatient(Patient patient) {
        Objects.requireNonNull(patient, "اطلاعات بیمار نمی‌تواند خالی باشد.");
        String nationalId = patient.getNationalId();

        if (patientRepository.existsByNationalId(nationalId)) {
            throw new IllegalArgumentException("بیماری با کد ملی " + nationalId + " از قبل در سامانه ثبت شده است.");
        }
        if (patient.getPatientId() == null || patient.getPatientId().isBlank()) {
            patient.setPatientId(PATIENT_ID_PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }

        // تضمین صفر بودن بدهی اولیه و وضعیت استاندارد در سمت سرور
        patient.setPendingBill(BigDecimal.ZERO);
        patient.transitionTo(PatientStatus.WAITING_FOR_DOCTOR);

        Patient saved = patientRepository.save(patient);

        auditService.logCurrentActorAction("RECEPTIONIST", "PATIENT_REGISTERED", "SUCCESS",
                "پذیرش بیمار جدید: " + saved.getFullName() + " (کد ملی: " + saved.getNationalId() + ")");
        return saved;
    }

    @Transactional(readOnly = true)
    public Patient getPatientByNationalId(String nationalId) {
        String cleanId = requireValidNationalId(nationalId);
        return patientRepository.findByNationalId(cleanId)
                .orElseThrow(() -> EntityNotFoundException.patientNotFound(cleanId));
    }

    @Transactional
    public void checkInExistingPatient(String nationalId) {
        String cleanId = requireValidNationalId(nationalId);
        Patient patient = patientRepository.findByNationalIdForUpdate(cleanId)
                .orElseThrow(() -> EntityNotFoundException.patientNotFound(cleanId));

        PatientStatus current = patient.getCurrentStatus();
        if (current == PatientStatus.IN_VISIT
                || current == PatientStatus.IN_TREATMENT
                || current == PatientStatus.WAITING_FOR_DOCTOR) {
            throw new IllegalStateException("بیمار در حال حاضر در صف انتظار یا در حال معاینه است.");
        }
        if (patient.getPendingBill().compareTo(BigDecimal.ZERO) > 0
                || current == PatientStatus.WAITING_FOR_PAYMENT
                || current == PatientStatus.PAYMENT_COMPLETED) {
            throw new IllegalStateException("بیمار دارای پرونده مالی باز یا ترخیص‌نشده است؛ ابتدا تسویه و ترخیص قبلی را تکمیل کنید.");
        }

        if (current.isTerminal()) {
            patient.setAssignedDoctor(null);
        }

        patient.transitionTo(PatientStatus.WAITING_FOR_DOCTOR);
        patientRepository.save(patient);

        auditService.logCurrentActorAction("RECEPTIONIST", "PATIENT_CHECKIN", "SUCCESS",
                "پذیرش مجدد و انتقال به صف انتظار پزشک برای بیمار: " + cleanId);
    }

    @Transactional
    public void dischargePatient(String nationalId) {
        String cleanId = requireValidNationalId(nationalId);
        Patient patient = patientRepository.findByNationalIdForUpdate(cleanId)
                .orElseThrow(() -> EntityNotFoundException.patientNotFound(cleanId));

        PatientStatus current = patient.getCurrentStatus();
        if (current == PatientStatus.DISCHARGED || current == PatientStatus.CLEARED_AND_FINISHED) {
            throw new IllegalStateException("بیمار با شناسه " + cleanId + " پیش‌تر ترخیص شده است.");
        }
        if (current != PatientStatus.PAYMENT_COMPLETED || patient.getPendingBill().compareTo(BigDecimal.ZERO) > 0) {
            throw InvalidWorkflowException.dischargeWithoutPayment(cleanId, current);
        }

        patient.transitionTo(PatientStatus.DISCHARGED);
        patientRepository.save(patient);

        auditService.logCurrentActorAction("RECEPTIONIST", "PATIENT_DISCHARGE", "SUCCESS",
                "ترخیص رسمی بیمار پس از تسویه کامل: " + cleanId);
    }

    @Transactional
    public void cancelAdmission(String nationalId) {
        String cleanId = requireValidNationalId(nationalId);
        Patient patient = patientRepository.findByNationalIdForUpdate(cleanId)
                .orElseThrow(() -> EntityNotFoundException.patientNotFound(cleanId));

        PatientStatus current = patient.getCurrentStatus();
        if (current.isTerminal() || current == PatientStatus.PAYMENT_COMPLETED) {
            throw new IllegalStateException("امکان لغو پذیرش برای بیماری که تسویه حساب کرده یا ترخیص شده وجود ندارد.");
        }

        if (patient.getPendingBill().compareTo(BigDecimal.ZERO) > 0) {
            throw InvalidWorkflowException.cancellationWithOpenDebt(cleanId, current, patient.getPendingBill());
        }

        patient.transitionTo(PatientStatus.CANCELLED);
        patientRepository.save(patient);

        auditService.logCurrentActorAction("RECEPTIONIST", "CANCEL_ADMISSION", "SUCCESS",
                "لغو پذیرش بیمار بدون بدهی: " + cleanId);
    }

    @Transactional(readOnly = true)
    public List<Patient> getPatientsByStatus(PatientStatus status) {
        Objects.requireNonNull(status, "وضعیت بیمار نمی‌تواند خالی باشد.");
        return patientRepository.findByCurrentStatus(status);
    }

    @Transactional(readOnly = true)
    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    private static String requireValidNationalId(String nationalId) {
        if (nationalId == null || nationalId.isBlank()) {
            throw new IllegalArgumentException("کد ملی بیمار نمی‌تواند خالی باشد.");
        }
        return nationalId.trim();
    }
}