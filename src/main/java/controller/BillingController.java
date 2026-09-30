package controller;

import enums.PatientStatus;
import models.Cashier;
import models.Patient;
import models.Receipt;
import models.Staff;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import repository.PatientRepository;
import repository.StaffRepository;
import service.BillingService;
import service.PatientService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingService billingService;
    private final PatientService patientService;
    private final PatientRepository patientRepository;
    private final StaffRepository staffRepository;

    public BillingController(BillingService billingService,
                             PatientService patientService,
                             PatientRepository patientRepository,
                             StaffRepository staffRepository) {
        this.billingService = billingService;
        this.patientService = patientService;
        this.patientRepository = patientRepository;
        this.staffRepository = staffRepository;
    }

    @GetMapping("/pending-patients")
    public ResponseEntity<List<Patient>> getPendingPatients() {
        List<Patient> list = patientService.getPatientsByStatus(PatientStatus.WAITING_FOR_PAYMENT);
        // اگر بیماری ویزیت شده اما بدهی آن صفر مانده، به صورت خودکار تعرفه پایه ویزیت اعمال می‌شود
        for (Patient p : list) {
            if (p.getPendingBill() == null || p.getPendingBill().compareTo(BigDecimal.ZERO) <= 0) {
                p.setPendingBill(new BigDecimal("150.00"));
                patientRepository.save(p);
            }
        }
        return ResponseEntity.ok(list);
    }

    @GetMapping("/patient/{nationalId}")
    public ResponseEntity<?> getPatientDebt(@PathVariable String nationalId) {
        try {
            Patient patient = patientService.getPatientByNationalId(nationalId);
            if (patient.getCurrentStatus() == PatientStatus.WAITING_FOR_PAYMENT &&
                    (patient.getPendingBill() == null || patient.getPendingBill().compareTo(BigDecimal.ZERO) <= 0)) {
                patient.setPendingBill(new BigDecimal("150.00"));
                patientRepository.save(patient);
            }

            return ResponseEntity.ok(Map.of(
                    "nationalId", patient.getNationalId(),
                    "fullName", patient.getFullName(),
                    "status", patient.getCurrentStatus().name(),
                    "pendingBill", patient.getPendingBill()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/settle")
    public ResponseEntity<?> settlePatientBill(
            Authentication authentication,
            @RequestParam String patientNationalId,
            @RequestParam BigDecimal amount,
            @RequestParam Receipt.PaymentMethod method,
            @RequestParam(defaultValue = "پرداخت در باجه صندوق") String description) {

        String username = (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser"))
                ? authentication.getName()
                : "cashier";

        Staff staff = staffRepository.findByUsername(username)
                .orElseGet(() -> staffRepository.findAll().stream()
                        .filter(s -> s instanceof Cashier)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("هیچ صندوق‌داری یافت نشد.")));

        // اصلاح بدهی در صورت صفر بودن
        Patient patient = patientService.getPatientByNationalId(patientNationalId);
        if (patient.getPendingBill() == null || patient.getPendingBill().compareTo(BigDecimal.ZERO) <= 0) {
            patient.setPendingBill(amount);
            patientRepository.save(patient);
        }

        Receipt receipt = billingService.processPatientSettlement((Cashier) staff, patientNationalId, amount, method, description);
        return ResponseEntity.ok(receipt);
    }
}