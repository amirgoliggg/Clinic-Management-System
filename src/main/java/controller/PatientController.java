package controller;

import enums.PatientStatus;
import models.Patient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import service.PatientService;

import java.util.List;

@RestController
@RequestMapping("/api/clinical/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @PostMapping("/register")
    public ResponseEntity<Patient> registerPatient(@RequestBody Patient patient) {
        return ResponseEntity.ok(patientService.registerNewPatient(patient));
    }

    @GetMapping("/{nationalId}")
    public ResponseEntity<Patient> getPatient(@PathVariable String nationalId) {
        return ResponseEntity.ok(patientService.getPatientByNationalId(nationalId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Patient>> getPatientsByStatus(@PathVariable PatientStatus status) {
        return ResponseEntity.ok(patientService.getPatientsByStatus(status));
    }

    @PutMapping("/{nationalId}/check-in")
    public ResponseEntity<Void> checkInPatient(@PathVariable String nationalId) {
        patientService.checkInExistingPatient(nationalId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{nationalId}/discharge")
    public ResponseEntity<Void> dischargePatient(@PathVariable String nationalId) {
        patientService.dischargePatient(nationalId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{nationalId}/cancel")
    public ResponseEntity<Void> cancelAdmission(@PathVariable String nationalId) {
        patientService.cancelAdmission(nationalId);
        return ResponseEntity.ok().build();
    }
}