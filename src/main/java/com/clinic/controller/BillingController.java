package com.clinic.controller;

import com.clinic.dto.ClinicalDTOs.FinancialSummaryResponse;
import com.clinic.dto.ClinicalDTOs.PatientResponse;
import com.clinic.dto.ClinicalDTOs.RefundRequest;
import com.clinic.dto.ClinicalDTOs.SettlementRequest;
import com.clinic.enums.PatientStatus;
import com.clinic.exceptions.InsufficientPermissionException;
import com.clinic.models.*;
import com.clinic.repository.StaffRepository;
import com.clinic.service.BillingService;
import com.clinic.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private static final String DEFAULT_SETTLEMENT_DESC = "پرداخت در باجه صندوق";

    private final BillingService billingService;
    private final PatientService patientService;
    private final StaffRepository staffRepository;

    public BillingController(BillingService billingService,
                             PatientService patientService,
                             StaffRepository staffRepository) {
        this.billingService = billingService;
        this.patientService = patientService;
        this.staffRepository = staffRepository;
    }

    private Cashier requireAuthenticatedCashier(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw InsufficientPermissionException.accessDenied("ANONYMOUS", "POS_TERMINAL");
        }
        String username = authentication.getName();
        Staff staff = staffRepository.findByUsername(username)
                .orElseThrow(() -> InsufficientPermissionException.accessDenied(username, "POS_TERMINAL"));

        if (!(staff instanceof Cashier cashier)) {
            throw InsufficientPermissionException.accessDenied(username, "POS_SETTLEMENT_CASHIER_ONLY");
        }
        return cashier;
    }

    @GetMapping("/pending-patients")
    public ResponseEntity<List<PatientResponse>> getPendingPatients() {
        List<PatientResponse> list = patientService.getPatientsByStatus(PatientStatus.WAITING_FOR_PAYMENT)
                .stream()
                .map(PatientResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/completed-patients")
    public ResponseEntity<List<PatientResponse>> getCompletedPatients() {
        List<PatientResponse> list = patientService.getPatientsByStatus(PatientStatus.PAYMENT_COMPLETED)
                .stream()
                .map(PatientResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/patient/{nationalId}")
    public ResponseEntity<Map<String, Object>> getPatientDebt(@PathVariable String nationalId) {
        Patient patient = patientService.getPatientByNationalId(nationalId.trim());
        return ResponseEntity.ok(Map.of(
                "nationalId", patient.getNationalId(),
                "fullName", patient.getFullName(),
                "status", patient.getCurrentStatus().name(),
                "pendingBill", patient.getPendingBill()
        ));
    }

    @GetMapping("/patient/{nationalId}/receipts")
    public ResponseEntity<List<Receipt>> getPatientReceipts(@PathVariable String nationalId) {
        return ResponseEntity.ok(billingService.getPatientReceipts(nationalId.trim()));
    }

    @GetMapping("/receipts")
    public ResponseEntity<List<Receipt>> getAllReceipts() {
        return ResponseEntity.ok(billingService.getAllReceipts());
    }

    @GetMapping("/summary")
    public ResponseEntity<FinancialSummaryResponse> getFinancialSummary() {
        return ResponseEntity.ok(billingService.getFinancialSummary());
    }

    @PostMapping(value = "/settle", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Receipt> settlePatientBillJson(
            Authentication authentication,
            @Valid @RequestBody SettlementRequest req) {

        Cashier cashier = requireAuthenticatedCashier(authentication);
        String desc = (req.description() != null && !req.description().isBlank())
                ? req.description().trim()
                : DEFAULT_SETTLEMENT_DESC;

        Receipt receipt = billingService.processPatientSettlement(
                cashier,
                req.patientNationalId().trim(),
                req.amount(),
                req.discountAmount(),
                req.taxAmount(),
                req.method(),
                desc,
                req.idempotencyKey()
        );
        return ResponseEntity.ok(receipt);
    }

    @PostMapping("/settle")
    public ResponseEntity<Receipt> settlePatientBillParams(
            Authentication authentication,
            @RequestParam String patientNationalId,
            @RequestParam BigDecimal amount,
            @RequestParam(defaultValue = "0.00") BigDecimal discountAmount,
            @RequestParam(defaultValue = "0.00") BigDecimal taxAmount,
            @RequestParam Receipt.PaymentMethod method,
            @RequestParam(defaultValue = DEFAULT_SETTLEMENT_DESC) String description,
            @RequestParam(required = false) String idempotencyKey) {

        Cashier cashier = requireAuthenticatedCashier(authentication);
        Receipt receipt = billingService.processPatientSettlement(
                cashier, patientNationalId.trim(), amount, discountAmount, taxAmount, method, description, idempotencyKey
        );
        return ResponseEntity.ok(receipt);
    }

    @PostMapping("/refund")
    public ResponseEntity<Receipt> refundReceipt(
            Authentication authentication,
            @Valid @RequestBody RefundRequest req) {

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw InsufficientPermissionException.refundApprovalRequired("ANONYMOUS", req.originalReceiptId());
        }

        Staff actor = staffRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> InsufficientPermissionException.refundApprovalRequired(authentication.getName(), req.originalReceiptId()));

        if (!(actor instanceof Admin admin) || !admin.getAdminRole().canManageFinances()) {
            throw InsufficientPermissionException.refundApprovalRequired(actor.getUsername(), req.originalReceiptId());
        }

        Receipt refund = billingService.issueSupervisedRefund(
                null,
                req.patientNationalId().trim(),
                req.originalReceiptId().trim(),
                req.refundAmount(),
                req.reason().trim(),
                admin
        );
        return ResponseEntity.ok(refund);
    }
}