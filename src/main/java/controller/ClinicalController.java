package controller;

import models.Doctor;
import models.MedicalRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import service.ClinicalService;
import service.StaffManagementService;

@RestController
@RequestMapping("/api/clinical/records")
public class ClinicalController {

    private final ClinicalService clinicalService;
    private final StaffManagementService staffManagementService;

    public ClinicalController(ClinicalService clinicalService, StaffManagementService staffManagementService) {
        this.clinicalService = clinicalService;
        this.staffManagementService = staffManagementService;
    }

    @PostMapping("/consultation")
    public ResponseEntity<MedicalRecord> conductConsultation(
            Authentication authentication,
            @RequestParam String patientNationalId,
            @RequestParam String diagnosis,
            @RequestParam String prescription,
            @RequestParam String notes) {

        Doctor doctor = (Doctor) staffManagementService.getStaffByUsername(authentication.getName());
        MedicalRecord record = clinicalService.conductConsultation(doctor, patientNationalId, diagnosis, prescription, notes);
        return ResponseEntity.ok(record);
    }

    @PutMapping("/{recordId}/amend")
    public ResponseEntity<MedicalRecord> amendRecord(
            Authentication authentication,
            @PathVariable String recordId,
            @RequestParam String updatedDiagnosis,
            @RequestParam String updatedPrescription,
            @RequestParam String reason) {

        Doctor doctor = (Doctor) staffManagementService.getStaffByUsername(authentication.getName());
        MedicalRecord amendedRecord = clinicalService.amendMedicalRecord(recordId, doctor.getNationalId(), updatedDiagnosis, updatedPrescription, reason);
        return ResponseEntity.ok(amendedRecord);
    }
}