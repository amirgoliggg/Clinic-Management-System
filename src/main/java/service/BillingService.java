package service;

import enums.PatientStatus;
import exceptions.EntityNotFoundException;
import models.Admin;
import models.Cashier;
import models.Patient;
import models.Receipt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.FinancialRepository;
import repository.PatientRepository;
import system.SystemClock;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class BillingService {

    private final FinancialRepository financialRepository;
    private final PatientRepository patientRepository;

    public BillingService(FinancialRepository financialRepository, PatientRepository patientRepository) {
        this.financialRepository = financialRepository;
        this.patientRepository = patientRepository;
    }

    @Transactional
    public Receipt processPatientSettlement(Cashier cashier, String patientNationalId, BigDecimal amount, Receipt.PaymentMethod method, String description) {
        Patient patient = patientRepository.findByNationalId(patientNationalId)
                .orElseThrow(() -> EntityNotFoundException.patientNotFound(patientNationalId));

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Charge amount must be strictly greater than zero.");
        }

        if (amount.compareTo(cashier.getMaxSingleTransactionLimit()) > 0) {
            throw new SecurityException("Transaction amount exceeds POS threshold limit ($" +
                    cashier.getMaxSingleTransactionLimit() + "). Supervisor authorization override required.");
        }

        if (amount.compareTo(patient.getPendingBill()) > 0) {
            throw new IllegalArgumentException("Payment amount exceeds current pending bill.");
        }

        patient.deductPayment(amount);

        String docId = (patient.getAssignedDoctor() != null)
                ? patient.getAssignedDoctor().getNationalId()
                : "DOC-GENERAL";

        String referenceNumber = "POS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Receipt receipt = new Receipt(
                referenceNumber,
                patient.getNationalId(),
                docId,
                cashier.getUsername(),
                amount,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                method,
                Receipt.ReceiptStatus.PAID,
                description,
                SystemClock.now()
        );

        financialRepository.save(receipt);
        patient.addReceipt(receipt);

        if (patient.getPendingBill().compareTo(BigDecimal.ZERO) == 0) {
            patient.setStatus(PatientStatus.PAYMENT_COMPLETED);
        }

        patientRepository.save(patient);
        return receipt;
    }

    @Transactional
    public Receipt issueSupervisedRefund(Cashier cashier, String patientNationalId, String originalReceiptId, BigDecimal refundAmount, String reason, Admin supervisor) {
        Patient patient = patientRepository.findByNationalId(patientNationalId)
                .orElseThrow(() -> EntityNotFoundException.patientNotFound(patientNationalId));

        if (supervisor.isAccountLocked() || !supervisor.getAdminRole().canManageFinances()) {
            throw new SecurityException("Security Violation: Authorizing supervisor lacks financial disbursement permissions.");
        }

        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be strictly positive.");
        }

        Receipt originalReceipt = financialRepository.findById(originalReceiptId.trim())
                .orElseThrow(() -> new NoSuchElementException("Original receipt [" + originalReceiptId + "] not found."));

        if (!originalReceipt.isPaid()) {
            throw new IllegalStateException("Only PAID receipts can be refunded.");
        }

        if (refundAmount.compareTo(originalReceipt.getAmountPaid()) > 0) {
            throw new IllegalArgumentException("Refund amount cannot exceed original charge ($" + originalReceipt.getAmountPaid() + ").");
        }

        originalReceipt.refundReceipt(reason);
        financialRepository.save(originalReceipt);

        String fullDescription = String.format("REFUND [%s] AuthBy: %s (%s) | Reason: %s",
                originalReceiptId, supervisor.getFullName(), supervisor.getUsername(),
                (reason == null ? "Clinical Reimbursement" : reason.trim()));

        String docId = (patient.getAssignedDoctor() != null)
                ? patient.getAssignedDoctor().getNationalId()
                : "DOC-GENERAL";

        Receipt refundReceipt = new Receipt(
                "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                patient.getNationalId(),
                docId,
                cashier.getUsername(),
                refundAmount,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Receipt.PaymentMethod.CASH,
                Receipt.ReceiptStatus.REFUNDED,
                fullDescription,
                SystemClock.now()
        );

        financialRepository.save(refundReceipt);
        patient.addReceipt(refundReceipt);
        patient.addCharge(refundAmount);
        patient.setStatus(PatientStatus.WAITING_FOR_PAYMENT);
        patientRepository.save(patient);

        return refundReceipt;
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateTotalRevenue() {
        BigDecimal total = financialRepository.calculateTotalRevenue();
        return total != null ? total : BigDecimal.ZERO;
    }
}