package com.clinic.controller;

import com.clinic.dto.ClinicalDTOs.PatientRegistrationRequest;
import com.clinic.dto.ClinicalDTOs.PatientResponse;
import com.clinic.enums.PatientStatus;
import com.clinic.models.Patient;
import com.clinic.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/clinical/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @PostMapping("/register")
    public ResponseEntity<PatientResponse> registerPatient(@Valid @RequestBody PatientRegistrationRequest req) {
        Patient patient = new Patient(
                req.nationalId().trim(),
                req.fullName().trim(),
                req.age(),
                req.contactNumber().trim(),
                req.bloodType() != null ? req.bloodType() : Patient.BloodType.UNKNOWN
        );
        patient.setPendingBill(BigDecimal.ZERO);
        Patient saved = patientService.registerNewPatient(patient);
        return ResponseEntity.ok(PatientResponse.fromEntity(saved));
    }

    @GetMapping("/{nationalId}")
    public ResponseEntity<PatientResponse> getPatient(@PathVariable String nationalId) {
        Patient patient = patientService.getPatientByNationalId(nationalId.trim());
        return ResponseEntity.ok(PatientResponse.fromEntity(patient));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<PatientResponse>> getPatientsByStatus(@PathVariable PatientStatus status) {
        List<PatientResponse> responses = patientService.getPatientsByStatus(status)
                .stream()
                .map(PatientResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{nationalId}/check-in")
    public ResponseEntity<Void> checkInPatient(@PathVariable String nationalId) {
        patientService.checkInExistingPatient(nationalId.trim());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{nationalId}/discharge")
    public ResponseEntity<Void> dischargePatient(@PathVariable String nationalId) {
        patientService.dischargePatient(nationalId.trim());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{nationalId}/cancel")
    public ResponseEntity<Void> cancelAdmission(@PathVariable String nationalId) {
        patientService.cancelAdmission(nationalId.trim());
        return ResponseEntity.ok().build();
    }
}