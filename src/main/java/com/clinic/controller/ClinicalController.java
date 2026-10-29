package com.clinic.controller;

import com.clinic.dto.ClinicalDTOs.AmendRecordRequest;
import com.clinic.dto.ClinicalDTOs.ConsultationRequest;
import com.clinic.dto.ClinicalDTOs.TriageRequest;
import com.clinic.dto.ClinicalDTOs.NursingCareRequest;
import com.clinic.exceptions.InsufficientPermissionException;
import com.clinic.models.Doctor;
import com.clinic.models.MedicalRecord;
import com.clinic.models.Nurse;
import com.clinic.models.Staff;
import com.clinic.service.ClinicalService;
import com.clinic.service.StaffManagementService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/clinical/records")
public class ClinicalController {

    private final ClinicalService clinicalService;
    private final StaffManagementService staffManagementService;

    public ClinicalController(ClinicalService clinicalService, StaffManagementService staffManagementService) {
        this.clinicalService = clinicalService;
        this.staffManagementService = staffManagementService;
    }

    private Doctor requireAuthenticatedDoctor(Authentication authentication, String actionName) {
        Staff staff = getAuthenticatedStaff(authentication, actionName);
        if (!(staff instanceof Doctor doctor)) {
            throw InsufficientPermissionException.clinicalRoleRequired(staff.getUsername(), actionName);
        }
        return doctor;
    }

    private Nurse requireAuthenticatedNurse(Authentication authentication, String actionName) {
        Staff staff = getAuthenticatedStaff(authentication, actionName);
        if (!(staff instanceof Nurse nurse)) {
            throw InsufficientPermissionException.clinicalRoleRequired(staff.getUsername(), actionName);
        }
        return nurse;
    }

    private Staff getAuthenticatedStaff(Authentication authentication, String actionName) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw InsufficientPermissionException.clinicalRoleRequired("ANONYMOUS", actionName);
        }
        return staffManagementService.getStaffByUsername(authentication.getName());
    }

    // ==================== Doctor Endpoints ====================

    @PostMapping(value = "/consultation", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MedicalRecord> conductConsultationJson(
            Authentication authentication,
            @Valid @RequestBody ConsultationRequest req) {

        Doctor doctor = requireAuthenticatedDoctor(authentication, "CONDUCT_CONSULTATION");
        MedicalRecord record = clinicalService.conductConsultation(
                doctor,
                req.patientNationalId().trim(),
                req.diagnosis().trim(),
                req.prescription().trim(),
                req.notes() != null ? req.notes().trim() : ""
        );
        return ResponseEntity.ok(record);
    }

    @PutMapping(value = "/{recordId}/amend", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MedicalRecord> amendRecordJson(
            Authentication authentication,
            @PathVariable String recordId,
            @Valid @RequestBody AmendRecordRequest req) {

        Doctor doctor = requireAuthenticatedDoctor(authentication, "AMEND_MEDICAL_RECORD");
        MedicalRecord amendedRecord = clinicalService.amendMedicalRecord(
                recordId,
                doctor.getNationalId(),
                req.updatedDiagnosis().trim(),
                req.updatedPrescription().trim(),
                req.reason().trim()
        );
        return ResponseEntity.ok(amendedRecord);
    }

    // ==================== Nurse Triage Endpoints ====================

    @PostMapping(value = "/triage", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MedicalRecord> performTriage(
            Authentication authentication,
            @Valid @RequestBody TriageRequest req) {

        Nurse nurse = requireAuthenticatedNurse(authentication, "PERFORM_TRIAGE");
        MedicalRecord record = clinicalService.performNurseTriage(
                nurse,
                req.patientNationalId().trim(),
                req.bloodPressure().trim(),
                req.bodyTemperatureCelsius(),
                req.heartRateBpm(),
                req.chiefComplaint(),
                req.nursingNotes()
        );
        return ResponseEntity.ok(record);
    }

    @PostMapping(value = "/care", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MedicalRecord> administerCare(
            Authentication authentication,
            @Valid @RequestBody NursingCareRequest req) {

        Nurse nurse = requireAuthenticatedNurse(authentication, "NURSING_CARE");
        MedicalRecord record = clinicalService.administerNursingCare(
                nurse,
                req.patientNationalId().trim(),
                req.careProcedure().trim(),
                req.serviceFee() != null ? req.serviceFee() : BigDecimal.ZERO,
                req.careNotes()
        );
        return ResponseEntity.ok(record);
    }

    // ==================== Read Endpoints ====================

    @GetMapping("/patient/{nationalId}")
    public ResponseEntity<List<MedicalRecord>> getPatientRecords(@PathVariable String nationalId) {
        return ResponseEntity.ok(clinicalService.getRecordsByPatient(nationalId));
    }
}